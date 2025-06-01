package net.seitter.jfat.cli;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.core.FatType;
import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.DiskImageCreator;

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
                case "graph":
                case "dot":
                    handleGraph(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "extract":
                    handleExtract(Arrays.copyOfRange(args, 1, args.length));
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
        System.out.println("  graph <image> [output.dot] [--expert]  Export filesystem structure as DOT graph");
        System.out.println("                                   Use --expert for detailed FAT table and cluster chains");
        System.out.println("  dot <image> [output.dot] [--expert]    Alias for graph");
        System.out.println();
        System.out.println("  help                             Show this help message");
        System.out.println("  version                          Show version information");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar jfat.jar create disk.img fat32 64");
        System.out.println("  java -jar jfat.jar list disk.img");
        System.out.println("  java -jar jfat.jar copy /home/user/file.txt disk.img /file.txt");
        System.out.println("  java -jar jfat.jar graph disk.img filesystem.dot");
        System.out.println("  java -jar jfat.jar graph disk.img expert_view.dot --expert");
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
            System.out.println("FAT type: " + bootSector.getFatType());
            System.out.println("Bytes per sector: " + bootSector.getBytesPerSector());
            System.out.println("Sectors per cluster: " + bootSector.getSectorsPerCluster());
            System.out.println("Cluster size: " + bootSector.getClusterSizeBytes() + " bytes");
            System.out.println("Reserved sectors: " + bootSector.getReservedSectorCount());
            System.out.println("Number of FATs: " + bootSector.getNumberOfFats());
            System.out.println("Root entries: " + bootSector.getRootEntryCount() + 
                             (bootSector.getFatType() == FatType.FAT32 ? " (unlimited)" : ""));
            System.out.println("Total sectors: " + bootSector.getTotalSectors());
            System.out.println("Sectors per FAT: " + bootSector.getSectorsPerFat());
            System.out.println("Total size: " + formatSize(bootSector.getTotalSectors() * bootSector.getBytesPerSector()));
            
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
} 