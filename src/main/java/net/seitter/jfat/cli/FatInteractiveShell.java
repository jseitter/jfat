package net.seitter.jfat.cli;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatEntry;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.StringsCompleter;
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
        "RD", "RMDIR", "TYPE", "CAT", "PWD", "CLS", "CLEAR", "HELP", "EXIT", "QUIT"
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
                .completer(new StringsCompleter(COMMANDS))
                .build();
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
        System.out.println("  PWD                 - Show current directory");
        System.out.println("  CLS                 - Clear screen (alias: CLEAR)");
        System.out.println("  HELP                - Show this help");
        System.out.println("  EXIT                - Exit interactive shell (alias: QUIT)");
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