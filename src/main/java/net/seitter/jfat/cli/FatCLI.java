package net.seitter.jfat.cli;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.core.FatType;
import net.seitter.jfat.core.BootSector;
import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.DiskImageCreator;
import net.seitter.jfat.web.FatWebServer;
import net.seitter.jfat.analysis.FragmentationAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Command-line interface for JFAT - Java FAT Filesystem Library
 */
public class FatCLI {
    
    private static final String VERSION = "0.1.0";
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        String command = args[0].toLowerCase();
        
        try {
            switch (command) {
                case "create":
                    handleCreate(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "list":
                case "ls":
                    handleList(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "copy":
                case "cp":
                    handleCopy(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "mkdir":
                    handleMkdir(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "info":
                    handleInfo(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "fragmentation":
                case "frag":
                case "defrag":
                    handleFragmentation(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "graph":
                case "dot":
                    handleGraph(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "extract":
                    handleExtract(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "interactive":
                case "shell":
                    handleInteractive(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "webserver":
                case "web":
                    handleWebServer(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "help":
                case "-h":
                case "--help":
                    printUsage();
                    break;
                case "version":
                case "-v":
                case "--version":
                    printVersion();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (System.getProperty("jfat.debug") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("JFAT - Java FAT Filesystem Library v" + VERSION);
        System.out.println("Usage: java -jar jfat.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  create <image> <type> <size>     Create a new FAT filesystem image");
        System.out.println("                                   Types: fat12, fat16, fat32");
        System.out.println("                                   Size: in MB (e.g., 64)");
        System.out.println();
        System.out.println("  list <image> [path]              List contents of filesystem or directory");
        System.out.println("  ls <image> [path]                Alias for list");
        System.out.println();
        System.out.println("  copy <src> <image> <dest>        Copy file/directory from local filesystem");
        System.out.println("  cp <src> <image> <dest>          to FAT image");
        System.out.println();
        System.out.println("  mkdir <image> <path>             Create directory in FAT image");
        System.out.println();
        System.out.println("  extract <image> <src> <dest>     Extract file from FAT image to local filesystem");
        System.out.println();
        System.out.println("  info <image>                     Show filesystem information");
        System.out.println();
        System.out.println("  fragmentation <image> [analysis-type]  Analyze filesystem fragmentation");
        System.out.println("  frag <image> [analysis-type]    Alias for fragmentation");
        System.out.println("  defrag <image> [analysis-type]  Alias for fragmentation");
        System.out.println("                                   Analysis types: summary (default), files, free,");
        System.out.println("                                   performance, recommendations");
        System.out.println();
        System.out.println("  graph <image> [output.dot] [--expert]  Export filesystem structure as DOT graph");
        System.out.println("                                   Use --expert for detailed FAT table and cluster chains");
        System.out.println("  dot <image> [output.dot] [--expert]    Alias for graph");
        System.out.println();
        System.out.println("  interactive <image>              Run interactive shell with FAT image");
        System.out.println("  shell <image>                    Alias for interactive");
        System.out.println();
        System.out.println("  webserver <port> [--dev]          Start web server on specified port");
        System.out.println("  web <port>                       Alias for webserver");
        System.out.println("                                   Use --dev for development mode");
        System.out.println();
        System.out.println("  help                             Show this help message");
        System.out.println("  version                          Show version information");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar jfat.jar create disk.img fat32 64");
        System.out.println("  java -jar jfat.jar list disk.img");
        System.out.println("  java -jar jfat.jar copy /home/user/file.txt disk.img /file.txt");
        System.out.println("  java -jar jfat.jar info disk.img");
        System.out.println("  java -jar jfat.jar fragmentation disk.img");
        System.out.println("  java -jar jfat.jar frag disk.img files");
        System.out.println("  java -jar jfat.jar defrag disk.img recommendations");
        System.out.println("  java -jar jfat.jar graph disk.img filesystem.dot");
        System.out.println("  java -jar jfat.jar graph disk.img expert_view.dot --expert");
        System.out.println("  java -jar jfat.jar interactive disk.img");
        System.out.println("  java -jar jfat.jar webserver 8080");
        System.out.println("  java -jar jfat.jar web 3000 --dev");
        System.out.println();
        System.out.println("Web Interface:");
        System.out.println("  The web server provides a browser-based interface for managing FAT images.");
        System.out.println("  After starting the server, open http://localhost:<port> in your browser.");
        System.out.println("  Production mode: All assets served from JAR file");
        System.out.println("  Development mode: Frontend must be started separately (see docs/web-interface.md)");
        System.out.println();
        System.out.println("Set JFAT_DEBUG=1 or -Djfat.debug=true for debug output");
    }
    
    private static void printVersion() {
        System.out.println("JFAT - Java FAT Filesystem Library");
        System.out.println("Version: " + VERSION);
        System.out.println("Build: " + System.getProperty("java.version"));
        System.out.println();
        System.out.println("Supported FAT types: FAT12, FAT16, FAT32");
        System.out.println("Licensed under MIT License");
    }
    
    private static void handleCreate(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: create <image> <type> <size>");
            System.err.println("  image: Output image file path");
            System.err.println("  type:  fat12, fat16, or fat32");
            System.err.println("  size:  Size in MB");
            System.exit(1);
        }
        
        String imagePath = args[0];
        String typeStr = args[1].toLowerCase();
        int sizeMB;
        
        try {
            sizeMB = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid size: " + args[2]);
            System.exit(1);
            return;
        }
        
        FatType fatType;
        switch (typeStr) {
            case "fat12":
                fatType = FatType.FAT12;
                break;
            case "fat16":
                fatType = FatType.FAT16;
                break;
            case "fat32":
                fatType = FatType.FAT32;
                break;
            default:
                System.err.println("Error: Invalid FAT type: " + typeStr);
                System.err.println("Supported types: fat12, fat16, fat32");
                System.exit(1);
                return;
        }
        
        System.out.println("Creating " + fatType + " filesystem image: " + imagePath + " (" + sizeMB + "MB)");
        DiskImageCreator.createDiskImage(imagePath, fatType, sizeMB);
        System.out.println("✓ Filesystem image created successfully");
        
        // Verify the created image
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            System.out.println("✓ Image verification passed");
            System.out.println("  FAT Type: " + fs.getBootSector().getFatType());
            System.out.println("  Cluster Size: " + fs.getBootSector().getClusterSizeBytes() + " bytes");
            System.out.println("  Total Size: " + (fs.getBootSector().getTotalSectors() * fs.getBootSector().getBytesPerSector() / (1024*1024)) + " MB");
        }
    }
    
    private static void handleList(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: list <image> [path]");
            System.exit(1);
        }
        
        String imagePath = args[0];
        String targetPath = args.length > 1 ? args[1] : "/";
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Listing contents of: " + targetPath);
            System.out.println("Filesystem: " + fs.getBootSector().getFatType() + " (" + imagePath + ")");
            System.out.println();
            
            FatDirectory dir;
            if (targetPath.equals("/")) {
                dir = fs.getRootDirectory();
            } else {
                FatEntry entry = getEntryByPath(fs, targetPath);
                if (entry == null) {
                    System.err.println("Error: Path not found: " + targetPath);
                    System.exit(1);
                    return;
                }
                if (!entry.isDirectory()) {
                    // It's a file, show file info instead
                    showFileInfo((FatFile) entry);
                    return;
                }
                dir = (FatDirectory) entry;
            }
            
            List<FatEntry> entries = dir.list();
            if (entries.isEmpty()) {
                System.out.println("(empty directory)");
                return;
            }
            
            System.out.printf("%-20s %-10s %-12s %s%n", "Name", "Type", "Size", "Modified");
            System.out.println("-".repeat(60));
            
            for (FatEntry entry : entries) {
                String type = entry.isDirectory() ? "DIR" : "FILE";
                String size = entry.isDirectory() ? "-" : formatSize(entry.getSize());
                String modified = formatDate(entry.getModifyTime());
                
                System.out.printf("%-20s %-10s %-12s %s%n", 
                    entry.getName(), type, size, modified);
            }
            
            System.out.println();
            System.out.println("Total: " + entries.size() + " entries");
        }
    }
    
    private static void handleCopy(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: copy <src> <image> <dest>");
            System.err.println("  src:   Source file/directory on local filesystem");
            System.err.println("  image: Target FAT image file");
            System.err.println("  dest:  Destination path in FAT image");
            System.exit(1);
        }
        
        String srcPath = args[0];
        String imagePath = args[1];
        String destPath = args[2];
        
        File srcFile = new File(srcPath);
        if (!srcFile.exists()) {
            System.err.println("Error: Source file does not exist: " + srcPath);
            System.exit(1);
        }
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Copying: " + srcPath + " -> " + destPath);
            
            if (srcFile.isDirectory()) {
                copyDirectoryToFat(srcFile, fs, destPath);
            } else {
                copyFileToFat(srcFile, fs, destPath);
            }
            
            System.out.println("✓ Copy completed successfully");
        }
    }
    
    private static void handleMkdir(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: mkdir <image> <path>");
            System.exit(1);
        }
        
        String imagePath = args[0];
        String dirPath = args[1];
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Creating directory: " + dirPath);
            createDirectoryPath(fs, dirPath);
            System.out.println("✓ Directory created successfully");
        }
    }
    
    private static void handleInfo(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: info <image>");
            System.exit(1);
        }
        
        String imagePath = args[0];
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            var bootSector = fs.getBootSector();
            
            System.out.println("FAT Filesystem Information");
            System.out.println("=".repeat(50));
            System.out.println("Image file: " + imagePath);
            System.out.println();
            
            // Display detailed cluster size information
            System.out.println(bootSector.getClusterSizeInfo());
            System.out.println();
            
            // Additional filesystem details
            System.out.println("Filesystem Details:");
            System.out.println("-".repeat(30));
            System.out.println("Reserved sectors: " + bootSector.getReservedSectorCount());
            System.out.println("Number of FATs: " + bootSector.getNumberOfFats());
            System.out.println("Root entries: " + bootSector.getRootEntryCount() + 
                             (bootSector.getFatType() == FatType.FAT32 ? " (unlimited)" : ""));
            System.out.println("Sectors per FAT: " + bootSector.getSectorsPerFat());
            
            // Cluster size validation status
            System.out.println();
            System.out.println("Cluster Size Validation:");
            System.out.println("-".repeat(30));
            boolean validSectors = BootSector.isValidSectorsPerCluster(bootSector.getSectorsPerCluster());
            boolean validBytes = BootSector.isValidBytesPerSector(bootSector.getBytesPerSector());
            long clusterSize = (long) bootSector.getBytesPerSector() * bootSector.getSectorsPerCluster();
            boolean validClusterSize = clusterSize <= 32 * 1024 * 1024;
            
            System.out.println("Sectors per cluster valid: " + (validSectors ? "✓ YES" : "✗ NO"));
            System.out.println("Bytes per sector valid: " + (validBytes ? "✓ YES" : "✗ NO"));
            System.out.println("Cluster size ≤ 32MB: " + (validClusterSize ? "✓ YES" : "✗ NO"));
            
            if (validSectors && validBytes && validClusterSize) {
                System.out.println("Overall validation: ✓ PASSED");
            } else {
                System.out.println("Overall validation: ✗ FAILED");
            }
            
            // Count files and directories
            System.out.println();
            System.out.println("Content Statistics");
            System.out.println("-".repeat(30));
            
            FileStats stats = countFilesRecursively(fs.getRootDirectory());
            System.out.println("Directories: " + stats.directories);
            System.out.println("Files: " + stats.files);
            System.out.println("Total entries: " + (stats.directories + stats.files));
            System.out.println("Used space: " + formatSize(stats.totalSize));
        }
    }
    
    private static void handleFragmentation(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: fragmentation <image> [analysis-type]");
            System.err.println("  image: FAT image file");
            System.err.println("  analysis-type: files, free, performance, recommendations, or summary (default)");
            System.err.println();
            System.err.println("Analysis types:");
            System.err.println("  summary        - Overall fragmentation summary (default)");
            System.err.println("  files          - Detailed file fragmentation analysis");
            System.err.println("  free           - Free space fragmentation analysis");
            System.err.println("  performance    - Performance impact assessment");
            System.err.println("  recommendations - Defragmentation recommendations");
            System.exit(1);
        }
        
        String imagePath = args[0];
        String analysisType = args.length > 1 ? args[1].toLowerCase() : "summary";
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Analyzing fragmentation in: " + imagePath);
            System.out.println("Filesystem: " + fs.getBootSector().getFatType());
            System.out.println("=".repeat(60));
            
            FragmentationAnalyzer analyzer = new FragmentationAnalyzer(fs);
            
            switch (analysisType) {
                case "summary":
                    displayFragmentationSummary(analyzer);
                    break;
                case "files":
                    displayFileFragmentationAnalysis(analyzer);
                    break;
                case "free":
                    displayFreeSpaceAnalysis(analyzer);
                    break;
                case "performance":
                    displayPerformanceAnalysis(analyzer);
                    break;
                case "recommendations":
                    displayRecommendations(analyzer);
                    break;
                default:
                    System.err.println("Error: Unknown analysis type: " + analysisType);
                    System.err.println("Valid types: summary, files, free, performance, recommendations");
                    System.exit(1);
            }
            
            System.out.println();
            System.out.println("✓ Fragmentation analysis completed");
        }
    }
    
    private static void handleGraph(String[] args) throws IOException {
        if (args.length < 1 || args.length > 3) {
            System.err.println("Usage: graph <image> [output.dot] [--expert]");
            System.err.println("  If output file is not specified, writes to stdout");
            System.err.println("  --expert: Include detailed FAT table and cluster chain information");
            System.exit(1);
        }
        
        String imagePath = args[0];
        String outputPath = null;
        boolean expertMode = false;
        
        // Parse arguments
        for (int i = 1; i < args.length; i++) {
            if ("--expert".equals(args[i])) {
                expertMode = true;
            } else if (outputPath == null) {
                outputPath = args[i];
            }
        }
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            StringBuilder dot = new StringBuilder();
            if (expertMode) {
                generateExpertDotGraph(fs, dot);
            } else {
                generateDotGraph(fs, dot);
            }
            
            if (outputPath != null) {
                Files.write(Paths.get(outputPath), dot.toString().getBytes());
                System.out.println("✓ DOT graph exported to: " + outputPath);
                System.out.println("  Render with: dot -Tpng " + outputPath + " -o filesystem.png");
                if (expertMode) {
                    System.out.println("  Expert mode: Includes FAT table and cluster chain details");
                }
            } else {
                System.out.println(dot.toString());
            }
        }
    }
    
    private static void handleExtract(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: extract <image> <src> <dest>");
            System.err.println("  image: FAT image file");
            System.err.println("  src:   Source path in FAT image");
            System.err.println("  dest:  Destination path on local filesystem");
            System.exit(1);
        }
        
        String imagePath = args[0];
        String srcPath = args[1];
        String destPath = args[2];
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            FatEntry entry = getEntryByPath(fs, srcPath);
            if (entry == null) {
                System.err.println("Error: Path not found in image: " + srcPath);
                System.exit(1);
            }
            
            System.out.println("Extracting: " + srcPath + " -> " + destPath);
            
            if (entry.isDirectory()) {
                extractDirectoryFromFat((FatDirectory) entry, Paths.get(destPath));
            } else {
                extractFileFromFat((FatFile) entry, Paths.get(destPath));
            }
            
            System.out.println("✓ Extraction completed successfully");
        }
    }
    
    private static void handleInteractive(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: interactive <image>");
            System.err.println("  image: FAT image file to open in interactive shell");
            System.exit(1);
        }
        
        String imagePath = args[0];
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Opening FAT image: " + imagePath);
            System.out.println("Filesystem: " + fs.getBootSector().getFatType());
            System.out.println();
            
            FatInteractiveShell shell = new FatInteractiveShell(fs);
            shell.run();
            
        } catch (IOException e) {
            System.err.println("Error opening FAT image: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void handleWebServer(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: webserver <port> [--dev]");
            System.err.println("  port:  Web server port");
            System.err.println("  --dev: Start in development mode (optional)");
            System.exit(1);
        }
        
        int port;
        boolean developmentMode = false;
        
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid port: " + args[0]);
            System.exit(1);
            return;
        }
        
        if (args.length == 2 && "--dev".equals(args[1])) {
            developmentMode = true;
        } else if (args.length == 2) {
            System.err.println("Error: Unknown flag: " + args[1]);
            System.err.println("Use --dev for development mode");
            System.exit(1);
        }
        
        if (port < 1 || port > 65535) {
            System.err.println("Error: Port must be between 1 and 65535");
            System.exit(1);
        }
        
        System.out.println("Starting JFAT Web Server on port " + port + "...");
        if (developmentMode) {
            System.out.println("Development mode enabled");
            System.out.println("Note: You need to start the frontend dev server separately:");
            System.out.println("  cd src/main/frontend && npm run dev");
        }
        System.out.println("Press Ctrl+C to stop the server");
        
        FatWebServer server = new FatWebServer(port, developmentMode);
        
        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down JFAT Web Server...");
            server.stop();
        }));
        
        server.start();
    }
    
    // Helper methods
    
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
                // This is the last component and it's a file
                return entry;
            }
        }
        
        return currentDir;
    }
    
    private static void copyFileToFat(File srcFile, FatFileSystem fs, String destPath) throws IOException {
        byte[] data = Files.readAllBytes(srcFile.toPath());
        FatFile fatFile = fs.createFile(destPath);
        fatFile.write(data);
        System.out.println("  Copied file: " + srcFile.getName() + " (" + formatSize(data.length) + ")");
    }
    
    private static void copyDirectoryToFat(File srcDir, FatFileSystem fs, String destPath) throws IOException {
        // Create the directory
        createDirectoryPath(fs, destPath);
        
        File[] files = srcDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String childDestPath = destPath + "/" + file.getName();
                
                if (file.isDirectory()) {
                    copyDirectoryToFat(file, fs, childDestPath);
                } else {
                    copyFileToFat(file, fs, childDestPath);
                }
            }
        }
        
        System.out.println("  Copied directory: " + srcDir.getName());
    }
    
    private static void createDirectoryPath(FatFileSystem fs, String path) throws IOException {
        String[] components = path.split("/");
        FatDirectory currentDir = fs.getRootDirectory();
        
        for (String component : components) {
            if (component.isEmpty()) continue;
            
            FatEntry existing = currentDir.getEntry(component);
            if (existing == null) {
                currentDir = currentDir.createDirectory(component);
            } else if (existing.isDirectory()) {
                currentDir = (FatDirectory) existing;
            } else {
                throw new IOException("Path component is a file, not a directory: " + component);
            }
        }
    }
    
    private static void extractFileFromFat(FatFile fatFile, Path destPath) throws IOException {
        byte[] data = fatFile.readAllBytes();
        // Only create parent directories if they exist
        if (destPath.getParent() != null) {
            Files.createDirectories(destPath.getParent());
        }
        Files.write(destPath, data);
        System.out.println("  Extracted file: " + fatFile.getName() + " (" + formatSize(data.length) + ")");
    }
    
    private static void extractDirectoryFromFat(FatDirectory fatDir, Path destPath) throws IOException {
        Files.createDirectories(destPath);
        
        List<FatEntry> entries = fatDir.list();
        for (FatEntry entry : entries) {
            Path childDestPath = destPath.resolve(entry.getName());
            
            if (entry.isDirectory()) {
                extractDirectoryFromFat((FatDirectory) entry, childDestPath);
            } else {
                extractFileFromFat((FatFile) entry, childDestPath);
            }
        }
        
        System.out.println("  Extracted directory: " + fatDir.getName());
    }
    
    private static void showFileInfo(FatFile file) {
        System.out.println("File Information");
        System.out.println("-".repeat(30));
        System.out.println("Name: " + file.getName());
        System.out.println("Size: " + formatSize(file.getSize()));
        System.out.println("Created: " + formatDate(file.getCreateTime()));
        System.out.println("Modified: " + formatDate(file.getModifyTime()));
        System.out.println("Accessed: " + formatDate(file.getAccessDate()));
        System.out.println("First cluster: " + file.getFirstCluster());
    }
    
    private static void generateDotGraph(FatFileSystem fs, StringBuilder dot) throws IOException {
        dot.append("digraph filesystem {\n");
        dot.append("  rankdir=LR;\n");
        dot.append("  node [shape=box];\n");
        dot.append("  \n");
        dot.append("  // Filesystem info\n");
        dot.append("  fs [label=\"").append(fs.getBootSector().getFatType()).append("\\n");
        dot.append(formatSize(fs.getBootSector().getTotalSectors() * fs.getBootSector().getBytesPerSector()));
        dot.append("\", shape=ellipse, style=filled, fillcolor=lightblue];\n");
        dot.append("  \n");
        
        // Generate nodes and edges
        generateDotNodes(fs.getRootDirectory(), "root", dot, 0);
        
        dot.append("  \n");
        dot.append("  fs -> root;\n");
        dot.append("}\n");
    }
    
    private static void generateExpertDotGraph(FatFileSystem fs, StringBuilder dot) throws IOException {
        dot.append("digraph filesystem {\n");
        dot.append("  rankdir=TB;\n");
        dot.append("  node [shape=box];\n");
        dot.append("  compound=true;\n");
        dot.append("  \n");
        
        var bootSector = fs.getBootSector();
        var fatTable = fs.getFatTable();
        
        // Filesystem header with detailed info
        dot.append("  subgraph cluster_fs {\n");
        dot.append("    label=\"FAT Filesystem Details\";\n");
        dot.append("    style=filled;\n");
        dot.append("    fillcolor=lightblue;\n");
        dot.append("    \n");
        dot.append("    fs_info [label=\"").append(bootSector.getFatType()).append(" Filesystem\\n");
        dot.append("Total Size: ").append(formatSize(bootSector.getTotalSectors() * bootSector.getBytesPerSector())).append("\\n");
        dot.append("Cluster Size: ").append(bootSector.getClusterSizeBytes()).append(" bytes\\n");
        dot.append("Sectors/Cluster: ").append(bootSector.getSectorsPerCluster()).append("\\n");
        dot.append("Reserved Sectors: ").append(bootSector.getReservedSectorCount()).append("\\n");
        dot.append("FAT Tables: ").append(bootSector.getNumberOfFats()).append("\\n");
        dot.append("Sectors/FAT: ").append(bootSector.getSectorsPerFat());
        dot.append("\", shape=record, style=filled, fillcolor=white];\n");
        dot.append("  }\n");
        dot.append("  \n");
        
        // FAT table visualization
        generateFatTableVisualization(fs, dot);
        
        // Directory structure with cluster details
        dot.append("  subgraph cluster_dirs {\n");
        dot.append("    label=\"Directory Structure\";\n");
        dot.append("    style=filled;\n");
        dot.append("    fillcolor=lightyellow;\n");
        dot.append("    \n");
        
        ExpertNodeContext context = new ExpertNodeContext();
        generateExpertDotNodes(fs.getRootDirectory(), "root", dot, context, fs);
        
        dot.append("  }\n");
        dot.append("  \n");
        
        // Generate cluster chain connections
        generateClusterChainConnections(context, dot);
        
        dot.append("}\n");
    }
    
    private static void generateFatTableVisualization(FatFileSystem fs, StringBuilder dot) throws IOException {
        var bootSector = fs.getBootSector();
        var fatTable = fs.getFatTable();
        
        dot.append("  subgraph cluster_fat {\n");
        dot.append("    label=\"FAT Table Summary\";\n");
        dot.append("    style=filled;\n");
        dot.append("    fillcolor=lightgray;\n");
        dot.append("    \n");
        
        // Calculate total clusters
        long dataSectors = bootSector.getTotalSectors() - 
                          (bootSector.getReservedSectorCount() + 
                           (bootSector.getNumberOfFats() * bootSector.getSectorsPerFat()) + 
                           bootSector.getRootDirectorySectorCount());
        long totalClusters = dataSectors / bootSector.getSectorsPerCluster();
        
        // Count used/free clusters
        int usedClusters = 0;
        int freeClusters = 0;
        int badClusters = 0;
        
        for (long cluster = 2; cluster < totalClusters && cluster < 100; cluster++) { // Limit to first 100 for performance
            try {
                long entry = fatTable.getClusterEntry(cluster);
                if (entry == 0) {
                    freeClusters++;
                } else if (entry == fatTable.getBadClusterMarker()) {
                    badClusters++;
                } else {
                    usedClusters++;
                }
            } catch (IOException e) {
                // Skip problematic clusters
            }
        }
        
        dot.append("    fat_summary [label=\"FAT Table Summary\\n");
        dot.append("Total Clusters: ").append(totalClusters).append("\\n");
        dot.append("Used Clusters: ").append(usedClusters).append("\\n");
        dot.append("Free Clusters: ").append(freeClusters).append("\\n");
        dot.append("Bad Clusters: ").append(badClusters).append("\\n");
        dot.append("EOF Marker: 0x").append(Long.toHexString(fatTable.getEndOfChainMarker()).toUpperCase());
        dot.append("\", shape=record, style=filled, fillcolor=white];\n");
        dot.append("  }\n");
        dot.append("  \n");
    }
    
    private static class ExpertNodeContext {
        int nodeCounter = 0;
        StringBuilder clusterConnections = new StringBuilder();
        
        String getNextNodeId() {
            return "node" + (++nodeCounter);
        }
        
        void addClusterConnection(String fromNode, String toNode, String label) {
            clusterConnections.append("  ").append(fromNode).append(" -> ").append(toNode);
            clusterConnections.append(" [label=\"").append(label).append("\", style=dashed, color=red];\n");
        }
    }
    
    private static void generateExpertDotNodes(FatDirectory dir, String nodeId, StringBuilder dot, 
                                             ExpertNodeContext context, FatFileSystem fs) throws IOException {
        // Get cluster chain for this directory
        List<Long> clusterChain = new ArrayList<>();
        if (dir.getFirstCluster() != 0) {
            clusterChain = fs.getFatTable().getClusterChain(dir.getFirstCluster());
        }
        
        // Create node for directory with cluster information
        dot.append("    ").append(nodeId).append(" [label=\"📁 ");
        dot.append(dir.getName().isEmpty() ? "ROOT" : dir.getName()).append("\\n");
        if (!clusterChain.isEmpty()) {
            dot.append("First Cluster: ").append(dir.getFirstCluster()).append("\\n");
            dot.append("Cluster Chain: ");
            for (int i = 0; i < Math.min(clusterChain.size(), 5); i++) {
                if (i > 0) dot.append("→");
                dot.append(clusterChain.get(i));
            }
            if (clusterChain.size() > 5) {
                dot.append("...(").append(clusterChain.size()).append(" total)");
            }
        } else {
            dot.append("No clusters allocated");
        }
        dot.append("\", style=filled, fillcolor=lightyellow];\n");
        
        List<FatEntry> entries = dir.list();
        for (FatEntry entry : entries) {
            String childId = context.getNextNodeId();
            
            if (entry.isDirectory()) {
                generateExpertDotNodes((FatDirectory) entry, childId, dot, context, fs);
                dot.append("    ").append(nodeId).append(" -> ").append(childId);
                dot.append(" [color=blue, label=\"contains\"];\n");
            } else {
                // File node with cluster details
                List<Long> fileClusterChain = new ArrayList<>();
                if (entry.getFirstCluster() != 0) {
                    fileClusterChain = fs.getFatTable().getClusterChain(entry.getFirstCluster());
                }
                
                dot.append("    ").append(childId).append(" [label=\"📄 ").append(entry.getName()).append("\\n");
                dot.append("Size: ").append(formatSize(entry.getSize())).append("\\n");
                if (!fileClusterChain.isEmpty()) {
                    dot.append("First Cluster: ").append(entry.getFirstCluster()).append("\\n");
                    dot.append("Clusters: ");
                    for (int i = 0; i < Math.min(fileClusterChain.size(), 3); i++) {
                        if (i > 0) dot.append("→");
                        dot.append(fileClusterChain.get(i));
                    }
                    if (fileClusterChain.size() > 3) {
                        dot.append("...(").append(fileClusterChain.size()).append(" total)");
                    }
                } else {
                    dot.append("Empty file");
                }
                dot.append("\", style=filled, fillcolor=lightgreen];\n");
                
                dot.append("    ").append(nodeId).append(" -> ").append(childId);
                dot.append(" [color=green, label=\"contains\"];\n");
                
                // Add cluster chain visualization for larger files
                if (fileClusterChain.size() > 1) {
                    generateClusterChainNodes(childId, fileClusterChain, dot, context);
                }
            }
        }
    }
    
    private static void generateClusterChainNodes(String fileNodeId, List<Long> clusterChain, 
                                                StringBuilder dot, ExpertNodeContext context) {
        if (clusterChain.size() <= 1) return;
        
        String prevClusterNodeId = null;
        for (int i = 0; i < Math.min(clusterChain.size(), 8); i++) { // Limit visualization to 8 clusters
            String clusterNodeId = context.getNextNodeId();
            long cluster = clusterChain.get(i);
            
            dot.append("    ").append(clusterNodeId).append(" [label=\"Cluster\\n");
            dot.append(cluster).append("\", shape=circle, style=filled, fillcolor=orange, fontsize=10];\n");
            
            if (i == 0) {
                // Connect file to first cluster
                context.addClusterConnection(fileNodeId, clusterNodeId, "data");
            } else {
                // Connect clusters in chain
                context.addClusterConnection(prevClusterNodeId, clusterNodeId, "next");
            }
            
            prevClusterNodeId = clusterNodeId;
        }
        
        if (clusterChain.size() > 8) {
            String endNodeId = context.getNextNodeId();
            dot.append("    ").append(endNodeId).append(" [label=\"...\\n");
            dot.append(clusterChain.size() - 8).append(" more\", shape=circle, style=filled, fillcolor=lightgray, fontsize=10];\n");
            context.addClusterConnection(prevClusterNodeId, endNodeId, "...");
        }
    }
    
    private static void generateClusterChainConnections(ExpertNodeContext context, StringBuilder dot) {
        if (context.clusterConnections.length() > 0) {
            dot.append("  // Cluster chain connections\n");
            dot.append(context.clusterConnections);
        }
    }
    
    private static int generateDotNodes(FatDirectory dir, String nodeId, StringBuilder dot, int counter) throws IOException {
        // Create node for directory
        dot.append("  ").append(nodeId).append(" [label=\"📁 ").append(dir.getName().isEmpty() ? "ROOT" : dir.getName());
        dot.append("\", style=filled, fillcolor=lightyellow];\n");
        
        List<FatEntry> entries = dir.list();
        for (FatEntry entry : entries) {
            counter++;
            String childId = "node" + counter;
            
            if (entry.isDirectory()) {
                counter = generateDotNodes((FatDirectory) entry, childId, dot, counter);
                dot.append("  ").append(nodeId).append(" -> ").append(childId).append(";\n");
            } else {
                // File node
                dot.append("  ").append(childId).append(" [label=\"📄 ").append(entry.getName());
                dot.append("\\n").append(formatSize(entry.getSize()));
                dot.append("\", style=filled, fillcolor=lightgreen];\n");
                dot.append("  ").append(nodeId).append(" -> ").append(childId).append(";\n");
            }
        }
        
        return counter;
    }
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private static String formatDate(java.util.Date date) {
        if (date == null) return "N/A";
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
    
    private static FileStats countFilesRecursively(FatDirectory dir) throws IOException {
        FileStats stats = new FileStats();
        stats.directories = 1; // Count this directory
        
        List<FatEntry> entries = dir.list();
        for (FatEntry entry : entries) {
            if (entry.isDirectory()) {
                FileStats childStats = countFilesRecursively((FatDirectory) entry);
                stats.directories += childStats.directories;
                stats.files += childStats.files;
                stats.totalSize += childStats.totalSize;
            } else {
                stats.files++;
                stats.totalSize += entry.getSize();
            }
        }
        
        return stats;
    }
    
    private static class FileStats {
        int directories = 0;
        int files = 0;
        long totalSize = 0;
    }
    
    private static void displayFragmentationSummary(FragmentationAnalyzer analyzer) throws IOException {
        var analysis = analyzer.analyzeFragmentation();
        var fileFragmentation = analysis.fileFragmentation;
        var freeSpaceFragmentation = analysis.freeSpaceFragmentation;
        var performanceImpact = analysis.performanceImpact;
        
        System.out.println("Fragmentation Summary");
        System.out.println("-".repeat(40));
        System.out.printf("File Fragmentation:      %6.1f%%\n", fileFragmentation.fragmentationRatio);
        System.out.printf("Free Space Fragmentation: %6.1f%%\n", freeSpaceFragmentation.freeSpaceFragmentationRatio);
        System.out.printf("Performance Impact:      %6.1f/100\n", performanceImpact.fragmentationImpactScore);
        System.out.printf("Average Fragments/File:  %6.1f\n", fileFragmentation.averageFragmentsPerFile);
        System.out.printf("Most Fragmented File:    %6d fragments\n", fileFragmentation.maxFileFragments);
        System.out.printf("Sequential Cluster Ratio: %6.1f%%\n", fileFragmentation.sequentialClusterRatio);
        
        System.out.println();
        if (performanceImpact.fragmentationImpactScore > 50) {
            System.out.println("⚠️  HIGH FRAGMENTATION DETECTED - Consider defragmentation");
        } else if (performanceImpact.fragmentationImpactScore > 20) {
            System.out.println("⚠️  MODERATE FRAGMENTATION - Monitor filesystem health");
        } else {
            System.out.println("✅ LOW FRAGMENTATION - Filesystem is well optimized");
        }
    }
    
    private static void displayFileFragmentationAnalysis(FragmentationAnalyzer analyzer) throws IOException {
        var analysis = analyzer.analyzeFragmentation();
        var fileFragmentation = analysis.fileFragmentation;
        
        System.out.println("File Fragmentation Analysis");
        System.out.println("-".repeat(40));
        System.out.printf("Fragmentation Ratio:      %6.1f%%\n", fileFragmentation.fragmentationRatio);
        System.out.printf("Average Fragments/File:   %6.1f\n", fileFragmentation.averageFragmentsPerFile);
        System.out.printf("Maximum File Fragments:   %6d\n", fileFragmentation.maxFileFragments);
        System.out.printf("Sequential Cluster Ratio: %6.1f%%\n", fileFragmentation.sequentialClusterRatio);
        System.out.printf("Average Cluster Gap:      %6.1f clusters\n", fileFragmentation.averageClusterGap);
        
        var worstFiles = fileFragmentation.worstFiles;
        if (!worstFiles.isEmpty()) {
            System.out.println();
            System.out.println("Most Fragmented Files:");
            System.out.println("-".repeat(40));
            System.out.printf("%-30s %10s %10s %12s\n", "Filename", "Size", "Fragments", "Severity");
            System.out.println("-".repeat(70));
            
            for (var fileInfo : worstFiles.subList(0, Math.min(10, worstFiles.size()))) {
                System.out.printf("%-30s %10s %10d %12s\n",
                    truncatePath(fileInfo.name, 30),
                    formatSize(fileInfo.size),
                    fileInfo.fragmentCount,
                    fileInfo.severity);
            }
            
            if (worstFiles.size() > 10) {
                System.out.println("... and " + (worstFiles.size() - 10) + " more fragmented files");
            }
        }
    }
    
    private static void displayFreeSpaceAnalysis(FragmentationAnalyzer analyzer) throws IOException {
        var analysis = analyzer.analyzeFragmentation();
        var freeSpaceFragmentation = analysis.freeSpaceFragmentation;
        
        System.out.println("Free Space Fragmentation Analysis");
        System.out.println("-".repeat(40));
        System.out.printf("Free Space Fragmentation: %6.1f%%\n", freeSpaceFragmentation.freeSpaceFragmentationRatio);
        System.out.printf("Free Block Count:         %6d\n", freeSpaceFragmentation.freeBlockCount);
        System.out.printf("Average Block Size:       %6.1f clusters\n", freeSpaceFragmentation.averageFreeBlockSize);
        System.out.printf("Largest Contiguous Block: %6d clusters (%s)\n", 
            freeSpaceFragmentation.largestContiguousFreeBlock,
            formatSize(freeSpaceFragmentation.largestContiguousFreeBlock * analyzer.getFileSystem().getBootSector().getClusterSizeBytes()));
        
        var freeSpaceMap = freeSpaceFragmentation.freeSpaceMap;
        if (!freeSpaceMap.isEmpty() && freeSpaceMap.size() <= 20) {
            System.out.println();
            System.out.println("Free Space Distribution:");
            System.out.println("-".repeat(40));
            System.out.printf("%-15s %-15s %s\n", "Start Cluster", "Size (clusters)", "Size (bytes)");
            System.out.println("-".repeat(50));
            
            for (var block : freeSpaceMap) {
                long sizeBytes = block.size * analyzer.getFileSystem().getBootSector().getClusterSizeBytes();
                System.out.printf("%-15d %-15d %s\n", 
                    block.startCluster, 
                    block.size,
                    formatSize(sizeBytes));
            }
        } else if (freeSpaceMap.size() > 20) {
            System.out.println();
            System.out.println("Free space is highly fragmented (" + freeSpaceMap.size() + " blocks)");
            System.out.println("Consider defragmentation to consolidate free space");
        }
    }
    
    private static void displayPerformanceAnalysis(FragmentationAnalyzer analyzer) throws IOException {
        var analysis = analyzer.analyzeFragmentation();
        var performanceImpact = analysis.performanceImpact;
        
        System.out.println("Performance Impact Analysis");
        System.out.println("-".repeat(40));
        System.out.printf("Seek Distance Score:       %6.1f/100\n", performanceImpact.seekDistanceScore);
        System.out.printf("Read Efficiency Score:     %6.1f/100\n", performanceImpact.readEfficiencyScore);
        System.out.printf("Overall Impact Score:      %6.1f/100\n", performanceImpact.fragmentationImpactScore);
        
        System.out.println();
        System.out.println("Performance Assessment:");
        System.out.println("-".repeat(25));
        
        double impactScore = performanceImpact.fragmentationImpactScore;
        if (impactScore < 20) {
            System.out.println("✅ EXCELLENT - Minimal performance impact");
            System.out.println("   Files are well organized with minimal fragmentation");
        } else if (impactScore < 40) {
            System.out.println("✅ GOOD - Low performance impact");
            System.out.println("   Some fragmentation present but not significant");
        } else if (impactScore < 60) {
            System.out.println("⚠️  FAIR - Moderate performance impact");
            System.out.println("   Noticeable fragmentation affecting read performance");
        } else if (impactScore < 80) {
            System.out.println("⚠️  POOR - High performance impact");
            System.out.println("   Significant fragmentation causing slower file access");
        } else {
            System.out.println("❌ CRITICAL - Severe performance impact");
            System.out.println("   Extreme fragmentation severely degrading performance");
        }
        
        System.out.println();
        if (impactScore > 50) {
            System.out.println("Recommendation: Run defragmentation to improve performance");
        } else {
            System.out.println("Recommendation: Current fragmentation level is acceptable");
        }
    }
    
    private static void displayRecommendations(FragmentationAnalyzer analyzer) throws IOException {
        var analysis = analyzer.analyzeFragmentation();
        var recommendations = analysis.recommendations;
        
        System.out.println("Defragmentation Recommendations");
        System.out.println("-".repeat(40));
        
        if (recommendations.isEmpty()) {
            System.out.println("✅ No defragmentation needed!");
            System.out.println("   Your filesystem is well optimized.");
            return;
        }
        
        for (int i = 0; i < recommendations.size(); i++) {
            var rec = recommendations.get(i);
            System.out.printf("\n%d. %s [%s PRIORITY]\n", 
                i + 1, rec.description, rec.priority);
            System.out.println("   Type: " + rec.type);
            
            if (!rec.affectedFiles.isEmpty()) {
                System.out.println("   Affected files: " + rec.affectedFiles.size());
                if (rec.affectedFiles.size() <= 5) {
                    for (String file : rec.affectedFiles) {
                        System.out.println("   - " + file);
                    }
                } else {
                    for (int j = 0; j < 3; j++) {
                        System.out.println("   - " + rec.affectedFiles.get(j));
                    }
                    System.out.println("   ... and " + (rec.affectedFiles.size() - 3) + " more files");
                }
            }
        }
        
        System.out.println();
        System.out.println("💡 Implementation Tips:");
        System.out.println("   • Back up important data before defragmentation");
        System.out.println("   • Start with HIGH priority recommendations");
        System.out.println("   • Monitor filesystem health regularly");
        System.out.println("   • Consider preventive measures (avoid file deletion/creation cycles)");
    }
    
    private static String truncatePath(String path, int maxLength) {
        if (path.length() <= maxLength) {
            return path;
        }
        return "..." + path.substring(path.length() - maxLength + 3);
    }
} 