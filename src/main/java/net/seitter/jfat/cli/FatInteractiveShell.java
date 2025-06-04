package net.seitter.jfat.cli;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.analysis.FragmentationAnalyzer;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Completer;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader.Option;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Interactive command-line shell for FAT filesystems with MSDOS-like commands
 * and comprehensive tab autocompletion
 */
public class FatInteractiveShell {
    
    private final FatFileSystem fileSystem;
    private FatDirectory currentDirectory;
    private String currentPath;
    private final Terminal terminal;
    private final LineReader reader;
    private boolean running;
    
    // MSDOS-like commands
    private static final String[] COMMANDS = {
        "DIR", "LS", "CD", "COPY", "CP", "DEL", "DELETE", "MD", "MKDIR", 
        "RD", "RMDIR", "TYPE", "CAT", "PWD", "CLS", "CLEAR", "FRAG", "FRAGMENTATION", 
        "HELP", "EXIT", "QUIT"
    };
    
    public FatInteractiveShell(FatFileSystem fileSystem) throws IOException {
        this.fileSystem = fileSystem;
        this.currentDirectory = fileSystem.getRootDirectory();
        this.currentPath = "/";
        this.running = true;
        
        // Initialize JLine terminal and reader
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new FatCompleter())
                .option(Option.CASE_INSENSITIVE, true)
                .option(Option.AUTO_FRESH_LINE, true)
                .build();
    }
    
    /**
     * Custom completer that provides intelligent autocompletion for FAT filesystem navigation
     */
    private class FatCompleter implements Completer {
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String buffer = line.line();
            List<String> words = line.words();
            int wordIndex = line.wordIndex();
            String word = line.word();
            
            try {
                if (words.isEmpty() || wordIndex == 0) {
                    // Complete command names
                    completeCommands(word, candidates);
                } else {
                    // Complete based on command context
                    String command = words.get(0).toUpperCase();
                    completeForCommand(command, word, wordIndex, candidates);
                }
            } catch (Exception e) {
                // Silently handle completion errors
                if (System.getProperty("jfat.debug") != null) {
                    System.err.println("Completion error: " + e.getMessage());
                }
            }
        }
        
        private void completeCommands(String prefix, List<Candidate> candidates) {
            for (String command : COMMANDS) {
                if (command.toLowerCase().startsWith(prefix.toLowerCase())) {
                    candidates.add(new Candidate(command.toLowerCase(), command, null, null, null, null, true));
                }
            }
        }
        
        private void completeForCommand(String command, String prefix, int wordIndex, List<Candidate> candidates) 
                throws IOException {
            switch (command) {
                case "DIR":
                case "LS":
                case "CD":
                case "RD":
                case "RMDIR":
                    // Complete directory names only
                    completeDirectories(prefix, candidates);
                    break;
                    
                case "TYPE":
                case "CAT":
                case "DEL":
                case "DELETE":
                    // Complete file names only
                    completeFiles(prefix, candidates);
                    break;
                    
                case "COPY":
                case "CP":
                    if (wordIndex == 1) {
                        // Source: complete both files and local paths
                        completeFiles(prefix, candidates);
                        completeLocalPaths(prefix, candidates);
                    } else if (wordIndex == 2) {
                        // Destination: complete directories and files
                        completeFilesAndDirectories(prefix, candidates);
                    }
                    break;
                    
                case "MD":
                case "MKDIR":
                    // For new directories, just complete parent paths
                    completeDirectoryPaths(prefix, candidates);
                    break;
                    
                default:
                    // Default to completing files and directories
                    completeFilesAndDirectories(prefix, candidates);
                    break;
            }
        }
        
        private void completeDirectories(String prefix, List<Candidate> candidates) throws IOException {
            completeEntries(prefix, candidates, true, false);
        }
        
        private void completeFiles(String prefix, List<Candidate> candidates) throws IOException {
            completeEntries(prefix, candidates, false, true);
        }
        
        private void completeFilesAndDirectories(String prefix, List<Candidate> candidates) throws IOException {
            completeEntries(prefix, candidates, true, true);
        }
        
        private void completeDirectoryPaths(String prefix, List<Candidate> candidates) throws IOException {
            // For directory creation, complete parent directory paths
            if (prefix.contains("/") || prefix.contains("\\")) {
                String parentPath = prefix.substring(0, prefix.lastIndexOf("/"));
                if (parentPath.isEmpty()) parentPath = "/";
                
                try {
                    FatEntry parentEntry = resolveEntry(parentPath);
                    if (parentEntry != null && parentEntry.isDirectory()) {
                        FatDirectory parentDir = (FatDirectory) parentEntry;
                        for (FatEntry entry : parentDir.list()) {
                            if (entry.isDirectory()) {
                                String fullPath = parentPath + "/" + entry.getName();
                                if (fullPath.toLowerCase().startsWith(prefix.toLowerCase())) {
                                    candidates.add(new Candidate(fullPath, entry.getName(), "dir", null, null, null, true));
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    // Ignore and continue
                }
            } else {
                completeDirectories(prefix, candidates);
            }
        }
        
        private void completeEntries(String prefix, List<Candidate> candidates, 
                                   boolean includeDirs, boolean includeFiles) throws IOException {
            
            // Handle absolute vs relative paths
            FatDirectory targetDir = currentDirectory;
            String searchPrefix = prefix;
            String pathPrefix = "";
            
            if (prefix.startsWith("/")) {
                // Absolute path
                targetDir = fileSystem.getRootDirectory();
                searchPrefix = prefix.substring(1);
                pathPrefix = "/";
            }
            
            // Handle path separators
            if (searchPrefix.contains("/")) {
                String[] pathParts = searchPrefix.split("/");
                StringBuilder currentPath = new StringBuilder(pathPrefix);
                
                // Navigate to the parent directory
                for (int i = 0; i < pathParts.length - 1; i++) {
                    if (!pathParts[i].isEmpty()) {
                        FatEntry entry = targetDir.getEntry(pathParts[i]);
                        if (entry != null && entry.isDirectory()) {
                            targetDir = (FatDirectory) entry;
                            currentPath.append(pathParts[i]).append("/");
                        } else {
                            return; // Path doesn't exist
                        }
                    }
                }
                
                pathPrefix = currentPath.toString();
                searchPrefix = pathParts[pathParts.length - 1];
            }
            
            // Add special directory entries
            if (includeDirs && !prefix.startsWith("/")) {
                if ("..".startsWith(searchPrefix.toLowerCase())) {
                    candidates.add(new Candidate("..", "..", "parent", null, null, null, true));
                }
                if ("/".startsWith(searchPrefix.toLowerCase())) {
                    candidates.add(new Candidate("/", "/", "root", null, null, null, true));
                }
            }
            
            // List entries in target directory
            try {
                for (FatEntry entry : targetDir.list()) {
                    boolean isDirectory = entry.isDirectory();
                    
                    if ((isDirectory && includeDirs) || (!isDirectory && includeFiles)) {
                        String entryName = entry.getName();
                        
                        if (entryName.toLowerCase().startsWith(searchPrefix.toLowerCase())) {
                            String fullPath = pathPrefix + entryName;
                            String suffix = isDirectory ? "/" : "";
                            String description = isDirectory ? "dir" : formatSize(entry.getSize());
                            
                            candidates.add(new Candidate(
                                fullPath + suffix, 
                                entryName + suffix, 
                                description, 
                                null, 
                                null, 
                                null, 
                                true
                            ));
                        }
                    }
                }
            } catch (IOException e) {
                // Ignore directory listing errors during completion
            }
        }
        
        private void completeLocalPaths(String prefix, List<Candidate> candidates) {
            // Complete local filesystem paths for COPY command source
            if (isLocalPath(prefix)) {
                try {
                    Path path = Paths.get(prefix);
                    Path parent = path.getParent();
                    String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
                    
                    if (parent != null && Files.exists(parent) && Files.isDirectory(parent)) {
                        Files.list(parent)
                                .filter(p -> p.getFileName().toString().toLowerCase()
                                        .startsWith(fileName.toLowerCase()))
                                .forEach(p -> {
                                    String name = p.getFileName().toString();
                                    boolean isDir = Files.isDirectory(p);
                                    String suffix = isDir ? "/" : "";
                                    String description = isDir ? "local-dir" : "local-file";
                                    
                                    candidates.add(new Candidate(
                                        p.toString() + suffix,
                                        name + suffix,
                                        description,
                                        null,
                                        null,
                                        null,
                                        true
                                    ));
                                });
                    }
                } catch (Exception e) {
                    // Ignore local path completion errors
                }
            }
        }
    }
    
    /**
     * Start the interactive shell
     */
    public void run() {
        printWelcome();
        
        while (running) {
            try {
                String prompt = currentPath + "> ";
                String line = reader.readLine(prompt);
                
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                
                processCommand(line.trim());
                
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                if (System.getProperty("jfat.debug") != null) {
                    e.printStackTrace();
                }
            }
        }
        
        try {
            terminal.close();
        } catch (IOException e) {
            System.err.println("Error closing terminal: " + e.getMessage());
        }
    }
    
    private void printWelcome() {
        System.out.println("JFAT Interactive Shell - FAT Filesystem Navigator");
        System.out.println("Type 'HELP' for available commands or 'EXIT' to quit");
        System.out.println("Use TAB for autocompletion of commands and paths");
        System.out.println("Current filesystem: " + fileSystem.getBootSector().getFatType());
        System.out.println();
    }
    
    private void processCommand(String line) throws IOException {
        String[] parts = parseCommand(line);
        if (parts.length == 0) return;
        
        String command = parts[0].toUpperCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        switch (command) {
            case "DIR":
            case "LS":
                handleDir(args);
                break;
            case "CD":
                handleCd(args);
                break;
            case "COPY":
            case "CP":
                handleCopy(args);
                break;
            case "DEL":
            case "DELETE":
                handleDelete(args);
                break;
            case "MD":
            case "MKDIR":
                handleMkdir(args);
                break;
            case "RD":
            case "RMDIR":
                handleRmdir(args);
                break;
            case "TYPE":
            case "CAT":
                handleType(args);
                break;
            case "FRAG":
            case "FRAGMENTATION":
                handleFragmentation(args);
                break;
            case "PWD":
                handlePwd();
                break;
            case "CLS":
            case "CLEAR":
                handleClear();
                break;
            case "HELP":
                handleHelp();
                break;
            case "EXIT":
            case "QUIT":
                handleExit();
                break;
            default:
                System.out.println("Unknown command: " + command);
                System.out.println("Type 'HELP' for available commands");
        }
    }
    
    private String[] parseCommand(String line) {
        // Simple command parsing - handles quoted arguments
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts.toArray(new String[0]);
    }
    
    private void handleDir(String[] args) throws IOException {
        FatDirectory targetDir = currentDirectory;
        String targetPath = currentPath;
        
        if (args.length > 0) {
            FatEntry entry = resolveEntry(args[0]);
            if (entry == null) {
                System.out.println("Directory not found: " + args[0]);
                return;
            }
            if (!entry.isDirectory()) {
                System.out.println("Not a directory: " + args[0]);
                return;
            }
            targetDir = (FatDirectory) entry;
            targetPath = resolvePath(args[0]);
        }
        
        System.out.println("Directory of " + targetPath);
        System.out.println();
        
        List<FatEntry> entries = targetDir.list();
        if (entries.isEmpty()) {
            System.out.println("(empty directory)");
            return;
        }
        
        System.out.printf("%-20s %-10s %-15s %s%n", "Name", "Type", "Size", "Modified");
        System.out.println("-".repeat(65));
        
        long totalSize = 0;
        int fileCount = 0;
        int dirCount = 0;
        
        for (FatEntry entry : entries) {
            String type = entry.isDirectory() ? "<DIR>" : "";
            String size = entry.isDirectory() ? "" : formatSize(entry.getSize());
            String modified = formatDate(entry.getModifyTime());
            
            System.out.printf("%-20s %-10s %-15s %s%n", 
                entry.getName(), type, size, modified);
            
            if (entry.isDirectory()) {
                dirCount++;
            } else {
                fileCount++;
                totalSize += entry.getSize();
            }
        }
        
        System.out.println();
        System.out.printf("%d file(s) %s bytes%n", fileCount, formatSize(totalSize));
        System.out.printf("%d dir(s)%n", dirCount);
    }
    
    private void handleCd(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println(currentPath);
            return;
        }
        
        String targetPath = args[0];
        
        // Handle special cases
        if ("..".equals(targetPath)) {
            if (!"/".equals(currentPath)) {
                int lastSlash = currentPath.lastIndexOf('/', currentPath.length() - 2);
                if (lastSlash <= 0) {
                    currentPath = "/";
                    currentDirectory = fileSystem.getRootDirectory();
                } else {
                    currentPath = currentPath.substring(0, lastSlash + 1);
                    currentDirectory = (FatDirectory) resolveEntry(currentPath);
                }
            }
            return;
        }
        
        if ("/".equals(targetPath) || "\\".equals(targetPath)) {
            currentPath = "/";
            currentDirectory = fileSystem.getRootDirectory();
            return;
        }
        
        FatEntry entry = resolveEntry(targetPath);
        if (entry == null) {
            System.out.println("Directory not found: " + targetPath);
            return;
        }
        
        if (!entry.isDirectory()) {
            System.out.println("Not a directory: " + targetPath);
            return;
        }
        
        currentDirectory = (FatDirectory) entry;
        currentPath = resolvePath(targetPath);
    }
    
    private void handleCopy(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: COPY <source> <destination>");
            return;
        }
        
        String sourcePath = args[0];
        String destPath = args[1];
        
        // Check if source is a local file (starts with drive letter or absolute path)
        if (isLocalPath(sourcePath)) {
            copyFromLocal(sourcePath, destPath);
        } else {
            // Copy within FAT filesystem
            copyWithinFat(sourcePath, destPath);
        }
    }
    
    private void handleDelete(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: DEL <filename>");
            return;
        }
        
        for (String filename : args) {
            FatEntry entry = resolveEntry(filename);
            if (entry == null) {
                System.out.println("File not found: " + filename);
                continue;
            }
            
            if (entry.isDirectory()) {
                System.out.println("Cannot delete directory with DEL. Use RD instead: " + filename);
                continue;
            }
            
            try {
                entry.delete();
                System.out.println("Deleted: " + filename);
            } catch (IOException e) {
                System.out.println("Error deleting " + filename + ": " + e.getMessage());
            }
        }
    }
    
    private void handleMkdir(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: MD <directory>");
            return;
        }
        
        for (String dirName : args) {
            try {
                FatDirectory parent = currentDirectory;
                String name = dirName;
                
                // Handle paths with slashes
                if (dirName.contains("/") || dirName.contains("\\")) {
                    String[] pathParts = dirName.split("[/\\\\]");
                    for (int i = 0; i < pathParts.length - 1; i++) {
                        if (!pathParts[i].isEmpty()) {
                            FatEntry entry = parent.getEntry(pathParts[i]);
                            if (entry == null) {
                                parent = parent.createDirectory(pathParts[i]);
                            } else if (entry.isDirectory()) {
                                parent = (FatDirectory) entry;
                            } else {
                                throw new IOException("Path component is not a directory: " + pathParts[i]);
                            }
                        }
                    }
                    name = pathParts[pathParts.length - 1];
                }
                
                parent.createDirectory(name);
                System.out.println("Directory created: " + dirName);
            } catch (IOException e) {
                System.out.println("Error creating directory " + dirName + ": " + e.getMessage());
            }
        }
    }
    
    private void handleRmdir(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: RD <directory>");
            return;
        }
        
        for (String dirName : args) {
            FatEntry entry = resolveEntry(dirName);
            if (entry == null) {
                System.out.println("Directory not found: " + dirName);
                continue;
            }
            
            if (!entry.isDirectory()) {
                System.out.println("Not a directory: " + dirName);
                continue;
            }
            
            try {
                entry.delete();
                System.out.println("Directory removed: " + dirName);
            } catch (IOException e) {
                System.out.println("Error removing directory " + dirName + ": " + e.getMessage());
            }
        }
    }
    
    private void handleType(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: TYPE <filename>");
            return;
        }
        
        String filename = args[0];
        FatEntry entry = resolveEntry(filename);
        if (entry == null) {
            System.out.println("File not found: " + filename);
            return;
        }
        
        if (entry.isDirectory()) {
            System.out.println("Cannot display directory: " + filename);
            return;
        }
        
        FatFile file = (FatFile) entry;
        try {
            byte[] content = file.readAllBytes();
            // Try to display as text
            String text = new String(content);
            System.out.println(text);
        } catch (Exception e) {
            System.out.println("Error reading file " + filename + ": " + e.getMessage());
        }
    }
    
    private void handleFragmentation(String[] args) throws IOException {
        String analysisType = args.length > 0 ? args[0].toLowerCase() : "summary";
        
        System.out.println("Analyzing filesystem fragmentation...");
        System.out.println("Filesystem: " + fileSystem.getBootSector().getFatType());
        System.out.println("-".repeat(50));
        
        try {
            FragmentationAnalyzer analyzer = new FragmentationAnalyzer(fileSystem);
            var analysis = analyzer.analyzeFragmentation();
            
            switch (analysisType) {
                case "summary":
                case "":
                    displayShellFragmentationSummary(analysis);
                    break;
                case "files":
                    displayShellFileAnalysis(analysis);
                    break;
                case "free":
                    displayShellFreeSpaceAnalysis(analysis);
                    break;
                case "performance":
                    displayShellPerformanceAnalysis(analysis);
                    break;
                case "recommendations":
                case "rec":
                    displayShellRecommendations(analysis);
                    break;
                default:
                    System.out.println("Unknown analysis type: " + analysisType);
                    System.out.println("Available types: summary, files, free, performance, recommendations");
            }
            
        } catch (Exception e) {
            System.out.println("Error during fragmentation analysis: " + e.getMessage());
            if (System.getProperty("jfat.debug") != null) {
                e.printStackTrace();
            }
        }
    }
    
    private void displayShellFragmentationSummary(FragmentationAnalyzer.FragmentationAnalysis analysis) {
        var fileFragmentation = analysis.fileFragmentation;
        var performanceImpact = analysis.performanceImpact;
        
        System.out.printf("File Fragmentation:      %6.1f%%\n", fileFragmentation.fragmentationRatio);
        System.out.printf("Performance Impact:      %6.1f/100\n", performanceImpact.fragmentationImpactScore);
        System.out.printf("Fragmented Files:        %6d\n", fileFragmentation.worstFiles.size());
        System.out.printf("Most Fragmented:         %6d fragments\n", fileFragmentation.maxFileFragments);
        
        if (performanceImpact.fragmentationImpactScore > 50) {
            System.out.println("\n⚠️  HIGH FRAGMENTATION - Consider running 'FRAG recommendations'");
        } else if (performanceImpact.fragmentationImpactScore > 20) {
            System.out.println("\n⚠️  MODERATE FRAGMENTATION");
        } else {
            System.out.println("\n✅ LOW FRAGMENTATION - Filesystem is well optimized");
        }
    }
    
    private void displayShellFileAnalysis(FragmentationAnalyzer.FragmentationAnalysis analysis) {
        var fileFragmentation = analysis.fileFragmentation;
        
        System.out.printf("Files analyzed:        %d\n", fileFragmentation.worstFiles.size());
        System.out.printf("Fragmentation ratio:   %.1f%%\n", fileFragmentation.fragmentationRatio);
        System.out.printf("Average fragments:     %.1f\n", fileFragmentation.averageFragmentsPerFile);
        System.out.printf("Max file fragments:    %d\n", fileFragmentation.maxFileFragments);
        
        if (!fileFragmentation.worstFiles.isEmpty()) {
            System.out.println("\nMost fragmented files:");
            System.out.println("-".repeat(40));
            int limit = Math.min(5, fileFragmentation.worstFiles.size());
            for (int i = 0; i < limit; i++) {
                var file = fileFragmentation.worstFiles.get(i);
                System.out.printf("%s (%d fragments)\n", file.name, file.fragmentCount);
            }
            if (fileFragmentation.worstFiles.size() > 5) {
                System.out.println("... and " + (fileFragmentation.worstFiles.size() - 5) + " more");
            }
        }
    }
    
    private void displayShellFreeSpaceAnalysis(FragmentationAnalyzer.FragmentationAnalysis analysis) {
        var freeSpace = analysis.freeSpaceFragmentation;
        
        System.out.printf("Free space fragmentation: %.1f%%\n", freeSpace.freeSpaceFragmentationRatio);
        System.out.printf("Free blocks:              %d\n", freeSpace.freeBlockCount);
        System.out.printf("Largest free block:       %d clusters\n", freeSpace.largestContiguousFreeBlock);
        System.out.printf("Average block size:       %.1f clusters\n", freeSpace.averageFreeBlockSize);
        
        if (freeSpace.freeSpaceFragmentationRatio > 70) {
            System.out.println("\n⚠️  HIGH free space fragmentation");
            System.out.println("Consider consolidation to improve allocation efficiency");
        }
    }
    
    private void displayShellPerformanceAnalysis(FragmentationAnalyzer.FragmentationAnalysis analysis) {
        var performance = analysis.performanceImpact;
        
        System.out.printf("Seek distance score:    %.1f/100\n", performance.seekDistanceScore);
        System.out.printf("Read efficiency:        %.1f/100\n", performance.readEfficiencyScore);
        System.out.printf("Overall impact:         %.1f/100\n", performance.fragmentationImpactScore);
        
        double impact = performance.fragmentationImpactScore;
        System.out.println("\nPerformance Assessment:");
        if (impact < 20) {
            System.out.println("✅ EXCELLENT - Minimal performance impact");
        } else if (impact < 40) {
            System.out.println("✅ GOOD - Low performance impact");
        } else if (impact < 60) {
            System.out.println("⚠️  FAIR - Moderate performance impact");
        } else if (impact < 80) {
            System.out.println("⚠️  POOR - High performance impact");
        } else {
            System.out.println("❌ CRITICAL - Severe performance impact");
        }
    }
    
    private void displayShellRecommendations(FragmentationAnalyzer.FragmentationAnalysis analysis) {
        var recommendations = analysis.recommendations;
        
        if (recommendations.isEmpty()) {
            System.out.println("✅ No defragmentation needed!");
            System.out.println("Your filesystem is well optimized.");
            return;
        }
        
        System.out.println("Defragmentation Recommendations:");
        System.out.println("-".repeat(40));
        
        for (int i = 0; i < recommendations.size(); i++) {
            var rec = recommendations.get(i);
            System.out.printf("\n%d. [%s] %s\n", i + 1, rec.priority, rec.description);
            
            if (!rec.affectedFiles.isEmpty()) {
                int fileCount = rec.affectedFiles.size();
                if (fileCount <= 3) {
                    for (String file : rec.affectedFiles) {
                        System.out.println("   - " + file);
                    }
                } else {
                    System.out.println("   Affects " + fileCount + " files");
                }
            }
        }
    }
    
    private void handlePwd() {
        System.out.println(currentPath);
    }
    
    private void handleClear() {
        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
        terminal.flush();
    }
    
    private void handleHelp() {
        System.out.println("Available commands:");
        System.out.println("  DIR [path]          - List directory contents (alias: LS)");
        System.out.println("  CD <path>           - Change directory");
        System.out.println("  COPY <src> <dest>   - Copy file (alias: CP)");
        System.out.println("  DEL <file>          - Delete file (alias: DELETE)");
        System.out.println("  MD <dir>            - Create directory (alias: MKDIR)");
        System.out.println("  RD <dir>            - Remove directory (alias: RMDIR)");
        System.out.println("  TYPE <file>         - Display file contents (alias: CAT)");
        System.out.println("  FRAG [type]         - Analyze filesystem fragmentation (alias: FRAGMENTATION)");
        System.out.println("                        Types: summary, files, free, performance, recommendations");
        System.out.println("  PWD                 - Show current directory");
        System.out.println("  CLS                 - Clear screen (alias: CLEAR)");
        System.out.println("  HELP                - Show this help");
        System.out.println("  EXIT                - Exit interactive shell (alias: QUIT)");
        System.out.println();
        System.out.println("Tab Autocompletion:");
        System.out.println("  - Press TAB to complete commands and file/directory names");
        System.out.println("  - Context-aware completion based on command type");
        System.out.println("  - Supports both absolute (/path) and relative (path) paths");
        System.out.println("  - Local file completion for COPY source paths");
        System.out.println();
        System.out.println("Fragmentation Analysis:");
        System.out.println("  FRAG summary        - Overall fragmentation overview");
        System.out.println("  FRAG files          - Detailed file fragmentation analysis");
        System.out.println("  FRAG free           - Free space fragmentation analysis");
        System.out.println("  FRAG performance    - Performance impact assessment");
        System.out.println("  FRAG recommendations - Defragmentation recommendations");
        System.out.println();
        System.out.println("Special paths:");
        System.out.println("  /                   - Root directory");
        System.out.println("  ..                  - Parent directory");
        System.out.println("  C:\\path\\file.txt    - Local Windows file (for COPY)");
        System.out.println("  /local/path/file    - Local Unix file (for COPY)");
    }
    
    private void handleExit() {
        System.out.println("Goodbye!");
        running = false;
    }
    
    // Helper methods
    
    private FatEntry resolveEntry(String path) throws IOException {
        if (path.startsWith("/")) {
            // Absolute path
            return getEntryByPath(fileSystem, path);
        } else {
            // Relative path
            String fullPath = currentPath;
            if (!fullPath.endsWith("/")) {
                fullPath += "/";
            }
            fullPath += path;
            return getEntryByPath(fileSystem, fullPath);
        }
    }
    
    private String resolvePath(String path) {
        if (path.startsWith("/")) {
            return path.endsWith("/") ? path : path + "/";
        } else {
            String fullPath = currentPath;
            if (!fullPath.endsWith("/")) {
                fullPath += "/";
            }
            fullPath += path;
            return fullPath.endsWith("/") ? fullPath : fullPath + "/";
        }
    }
    
    private static FatEntry getEntryByPath(FatFileSystem fs, String path) throws IOException {
        if (path.equals("/")) {
            return fs.getRootDirectory();
        }
        
        String[] components = path.split("/");
        FatDirectory currentDir = fs.getRootDirectory();
        
        for (String component : components) {
            if (component.isEmpty()) continue;
            
            FatEntry entry = currentDir.getEntry(component);
            if (entry == null) {
                return null;
            }
            
            if (entry.isDirectory()) {
                currentDir = (FatDirectory) entry;
            } else {
                return entry;
            }
        }
        
        return currentDir;
    }
    
    private boolean isLocalPath(String path) {
        // Check for Windows drive letters or Unix absolute paths
        return path.matches("[A-Za-z]:.*") || path.startsWith("/") && !path.startsWith("//");
    }
    
    private void copyFromLocal(String localPath, String fatPath) throws IOException {
        Path source = Paths.get(localPath);
        if (!Files.exists(source)) {
            System.out.println("Local file not found: " + localPath);
            return;
        }
        
        if (Files.isDirectory(source)) {
            System.out.println("Directory copying not supported in interactive mode");
            return;
        }
        
        try {
            byte[] data = Files.readAllBytes(source);
            
            // Determine target path in FAT filesystem
            String targetPath = fatPath;
            if (!fatPath.startsWith("/")) {
                targetPath = currentPath;
                if (!targetPath.endsWith("/")) {
                    targetPath += "/";
                }
                targetPath += fatPath;
            }
            
            FatFile fatFile = fileSystem.createFile(targetPath);
            fatFile.write(data);
            System.out.println("Copied " + localPath + " to " + targetPath + " (" + formatSize(data.length) + ")");
            
        } catch (IOException e) {
            System.out.println("Error copying file: " + e.getMessage());
        }
    }
    
    private void copyWithinFat(String sourcePath, String destPath) throws IOException {
        FatEntry sourceEntry = resolveEntry(sourcePath);
        if (sourceEntry == null) {
            System.out.println("Source file not found: " + sourcePath);
            return;
        }
        
        if (sourceEntry.isDirectory()) {
            System.out.println("Directory copying within FAT filesystem not supported");
            return;
        }
        
        FatFile sourceFile = (FatFile) sourceEntry;
        
        try {
            byte[] data = sourceFile.readAllBytes();
            
            String targetPath = destPath;
            if (!destPath.startsWith("/")) {
                targetPath = currentPath;
                if (!targetPath.endsWith("/")) {
                    targetPath += "/";
                }
                targetPath += destPath;
            }
            
            FatFile targetFile = fileSystem.createFile(targetPath);
            targetFile.write(data);
            System.out.println("Copied " + sourcePath + " to " + targetPath + " (" + formatSize(data.length) + ")");
            
        } catch (IOException e) {
            System.out.println("Error copying file: " + e.getMessage());
        }
    }
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private static String formatDate(java.util.Date date) {
        if (date == null) return "N/A";
        return new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm").format(date);
    }
} 