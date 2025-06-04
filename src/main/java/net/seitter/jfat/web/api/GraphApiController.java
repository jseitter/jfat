package net.seitter.jfat.web.api;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.BootSector;
import net.seitter.jfat.core.FatTable;
import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.analysis.FragmentationAnalyzer;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for graph generation and filesystem analysis
 */
public class GraphApiController {
    
    private static final String IMAGES_DIR = "images";
    
    public void getGraph(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                String dotGraph = generateBasicDotGraph(fs);
                
                GraphResponse response = new GraphResponse();
                response.imageName = imageName;
                response.format = "dot";
                response.content = dotGraph;
                response.expertMode = false;
                
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, response);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to generate graph", e);
        }
    }
    
    public void getExpertGraph(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                String dotGraph = generateExpertDotGraph(fs);
                
                GraphResponse response = new GraphResponse();
                response.imageName = imageName;
                response.format = "dot";
                response.content = dotGraph;
                response.expertMode = true;
                
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, response);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to generate expert graph", e);
        }
    }
    
    public void getAnalysis(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                AnalysisResponse analysis = generateAnalysis(fs);
                analysis.imageName = imageName;
                
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, analysis);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to generate analysis", e);
        }
    }
    
    /**
     * New endpoint for detailed fragmentation analysis
     */
    public void getFragmentationAnalysis(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FragmentationAnalyzer analyzer = new FragmentationAnalyzer(fs);
                FragmentationAnalyzer.FragmentationAnalysis analysis = analyzer.analyzeFragmentation();
                
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, analysis);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to analyze fragmentation", e);
        }
    }
    
    private String generateBasicDotGraph(FatFileSystem fs) throws IOException {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        
        out.println("digraph filesystem {");
        out.println("  rankdir=LR;");
        out.println("  node [shape=box];");
        out.println("  ");
        out.println("  // Filesystem info");
        out.printf("  fs [label=\"%s\\n%s\", shape=ellipse, style=filled, fillcolor=lightblue];%n",
                fs.getBootSector().getFatType(),
                formatSize(fs.getBootSector().getTotalSectors() * fs.getBootSector().getBytesPerSector()));
        out.println("  ");
        
        // Generate nodes and edges
        int[] counter = {0};
        generateDotNodes(fs.getRootDirectory(), "root", out, counter);
        
        out.println("  ");
        out.println("  fs -> root;");
        out.println("}");
        
        return writer.toString();
    }
    
    private String generateExpertDotGraph(FatFileSystem fs) throws IOException {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        
        out.println("digraph filesystem {");
        out.println("  rankdir=TB;");
        out.println("  node [shape=box];");
        out.println("  compound=true;");
        out.println("  ");
        
        var bootSector = fs.getBootSector();
        var fatTable = fs.getFatTable();
        
        // Filesystem header with detailed info
        out.println("  subgraph cluster_fs {");
        out.println("    label=\"FAT Filesystem Details\";");
        out.println("    style=filled;");
        out.println("    fillcolor=lightblue;");
        out.println("    ");
        out.printf("    fs_info [label=\"%s Filesystem\\n", bootSector.getFatType());
        out.printf("Total Size: %s\\n", formatSize(bootSector.getTotalSectors() * bootSector.getBytesPerSector()));
        out.printf("Cluster Size: %d bytes\\n", bootSector.getClusterSizeBytes());
        out.printf("Sectors/Cluster: %d\\n", bootSector.getSectorsPerCluster());
        out.printf("Reserved Sectors: %d\\n", bootSector.getReservedSectorCount());
        out.printf("FAT Tables: %d\\n", bootSector.getNumberOfFats());
        out.printf("Sectors/FAT: %d", bootSector.getSectorsPerFat());
        out.println("\", shape=record, style=filled, fillcolor=white];");
        out.println("  }");
        out.println("  ");
        
        // FAT table visualization
        generateFatTableVisualization(fs, out);
        
        // Directory structure with cluster details
        out.println("  subgraph cluster_dirs {");
        out.println("    label=\"Directory Structure\";");
        out.println("    style=filled;");
        out.println("    fillcolor=lightyellow;");
        out.println("    ");
        
        ExpertNodeContext context = new ExpertNodeContext();
        generateExpertDotNodes(fs.getRootDirectory(), "root", out, context, fs);
        
        out.println("  }");
        out.println("  ");
        
        // Generate cluster chain connections
        generateClusterChainConnections(context, out);
        
        out.println("}");
        
        return writer.toString();
    }
    
    private void generateFatTableVisualization(FatFileSystem fs, PrintWriter out) throws IOException {
        var bootSector = fs.getBootSector();
        var fatTable = fs.getFatTable();
        
        out.println("  subgraph cluster_fat {");
        out.println("    label=\"FAT Table Summary\";");
        out.println("    style=filled;");
        out.println("    fillcolor=lightgray;");
        out.println("    ");
        
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
        
        out.printf("    fat_summary [label=\"FAT Table Summary\\n");
        out.printf("Total Clusters: %d\\n", totalClusters);
        out.printf("Used Clusters: %d\\n", usedClusters);
        out.printf("Free Clusters: %d\\n", freeClusters);
        out.printf("Bad Clusters: %d\\n", badClusters);
        out.printf("EOF Marker: 0x%s", Long.toHexString(fatTable.getEndOfChainMarker()).toUpperCase());
        out.println("\", shape=record, style=filled, fillcolor=white];");
        out.println("  }");
        out.println("  ");
    }
    
    private static class ExpertNodeContext {
        int nodeCounter = 0;
        StringBuilder clusterConnections = new StringBuilder();
        
        String getNextNodeId() {
            return "node" + (++nodeCounter);
        }
        
        void addClusterConnection(String fromNode, String toNode, String label) {
            clusterConnections.append("  ").append(fromNode).append(" -> ").append(toNode);
            clusterConnections.append(" [label=\"").append(label).append("\", style=dashed, color=red];").append('\n');
        }
    }
    
    private void generateExpertDotNodes(FatDirectory dir, String nodeId, PrintWriter out, 
                                             ExpertNodeContext context, FatFileSystem fs) throws IOException {
        // Get cluster chain for this directory
        List<Long> clusterChain = new ArrayList<>();
        try {
            if (dir.getFirstCluster() != 0) {
                clusterChain = fs.getFatTable().getClusterChain(dir.getFirstCluster());
            }
        } catch (Exception e) {
            // Directory might not have cluster chain (e.g., FAT16 root directory)
            clusterChain = new ArrayList<>();
        }
        
        // Create node for directory with cluster information
        out.printf("    %s [label=\"📁 ", nodeId);
        out.print(dir.getName().isEmpty() ? "ROOT" : dir.getName());
        out.print("\\n");
        if (!clusterChain.isEmpty()) {
            out.printf("First Cluster: %d\\n", dir.getFirstCluster());
            out.print("Cluster Chain: ");
            for (int i = 0; i < Math.min(clusterChain.size(), 5); i++) {
                if (i > 0) out.print("→");
                out.print(clusterChain.get(i));
            }
            if (clusterChain.size() > 5) {
                out.printf("...(%d total)", clusterChain.size());
            }
        } else {
            out.print("No clusters allocated");
        }
        out.println("\", style=filled, fillcolor=lightyellow];");
        
        List<FatEntry> entries = dir.list();
        for (FatEntry entry : entries) {
            String childId = context.getNextNodeId();
            
            if (entry.isDirectory()) {
                generateExpertDotNodes((FatDirectory) entry, childId, out, context, fs);
                out.printf("    %s -> %s", nodeId, childId);
                out.println(" [color=blue, label=\"contains\"];");
            } else {
                // File node with cluster details
                List<Long> fileClusterChain = new ArrayList<>();
                try {
                    if (entry.getFirstCluster() != 0) {
                        fileClusterChain = fs.getFatTable().getClusterChain(entry.getFirstCluster());
                    }
                } catch (Exception e) {
                    // File might not have cluster chain (e.g., small files)
                    fileClusterChain = new ArrayList<>();
                }
                
                out.printf("    %s [label=\"📄 %s\\n", childId, entry.getName());
                out.printf("Size: %s\\n", formatSize(entry.getSize()));
                if (!fileClusterChain.isEmpty()) {
                    out.printf("First Cluster: %d\\n", entry.getFirstCluster());
                    out.print("Clusters: ");
                    for (int i = 0; i < Math.min(fileClusterChain.size(), 3); i++) {
                        if (i > 0) out.print("→");
                        out.print(fileClusterChain.get(i));
                    }
                    if (fileClusterChain.size() > 3) {
                        out.printf("...(%d total)", fileClusterChain.size());
                    }
                } else {
                    if (entry.getSize() == 0) {
                        out.print("Empty file");
                    } else {
                        out.print("Small file (no clusters)");
                    }
                }
                out.println("\", style=filled, fillcolor=lightgreen];");
                
                out.printf("    %s -> %s", nodeId, childId);
                out.println(" [color=green, label=\"contains\"];");
                
                // Add cluster chain visualization for larger files
                if (fileClusterChain.size() > 1) {
                    generateClusterChainNodes(childId, fileClusterChain, out, context);
                }
            }
        }
    }
    
    private void generateClusterChainNodes(String fileNodeId, List<Long> clusterChain, 
                                                PrintWriter out, ExpertNodeContext context) {
        if (clusterChain.size() <= 1) return;
        
        String prevClusterNodeId = null;
        for (int i = 0; i < Math.min(clusterChain.size(), 8); i++) { // Limit visualization to 8 clusters
            String clusterNodeId = context.getNextNodeId();
            long cluster = clusterChain.get(i);
            
            out.printf("    %s [label=\"Cluster\\n", clusterNodeId);
            out.printf("%d\", shape=circle, style=filled, fillcolor=orange, fontsize=10];%n", cluster);
            
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
            out.printf("    %s [label=\"...\\n", endNodeId);
            out.printf("%d more\", shape=circle, style=filled, fillcolor=lightgray, fontsize=10];%n", clusterChain.size() - 8);
            context.addClusterConnection(prevClusterNodeId, endNodeId, "...");
        }
    }
    
    private void generateClusterChainConnections(ExpertNodeContext context, PrintWriter out) {
        if (context.clusterConnections.length() > 0) {
            out.println("  // Cluster chain connections");
            out.print(context.clusterConnections);
        }
    }
    
    private void generateDotNodes(FatDirectory dir, String nodeId, PrintWriter out, int[] counter) throws IOException {
        // Create node for directory
        out.printf("  %s [label=\"📁 %s", nodeId, dir.getName().isEmpty() ? "ROOT" : dir.getName());
        out.println("\", style=filled, fillcolor=lightyellow];");
        
        List<FatEntry> entries = dir.list();
        for (FatEntry entry : entries) {
            counter[0]++;
            String childId = "node" + counter[0];
            
            if (entry.isDirectory()) {
                generateDotNodes((FatDirectory) entry, childId, out, counter);
                out.printf("  %s -> %s;%n", nodeId, childId);
            } else {
                // File node
                out.printf("  %s [label=\"📄 %s", childId, entry.getName());
                out.printf("\\n%s", formatSize(entry.getSize()));
                out.println("\", style=filled, fillcolor=lightgreen];");
                out.printf("  %s -> %s;%n", nodeId, childId);
            }
        }
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private AnalysisResponse generateAnalysis(FatFileSystem fs) throws IOException {
        BootSector bootSector = fs.getBootSector();
        FatTable fatTable = fs.getFatTable();
        
        AnalysisResponse analysis = new AnalysisResponse();
        
        // Filesystem metrics
        analysis.fatType = bootSector.getFatType().toString();
        analysis.totalSectors = bootSector.getTotalSectors();
        analysis.bytesPerSector = bootSector.getBytesPerSector();
        analysis.sectorsPerCluster = bootSector.getSectorsPerCluster();
        analysis.clusterSize = bootSector.getClusterSizeBytes();
        analysis.totalSize = bootSector.getTotalSectors() * bootSector.getBytesPerSector();
        
        // Cluster metrics
        analysis.totalClusters = calculateTotalClusters(bootSector);
        analysis.usedClusters = 0; // Would need to implement
        analysis.freeClusters = analysis.totalClusters - analysis.usedClusters;
        analysis.clusterUtilization = analysis.totalClusters > 0 ? 
            (double) analysis.usedClusters / analysis.totalClusters : 0.0;
        
        // Performance metrics
        analysis.recommendedClusterSize = getRecommendedClusterSize(analysis.totalSize, bootSector.getFatType().toString());
        analysis.isOptimalClusterSize = analysis.clusterSize == analysis.recommendedClusterSize;
        
        // Cluster size validation
        analysis.clusterSizeInfo = bootSector.getClusterSizeInfo();
        
        // Add fragmentation analysis
        try {
            FragmentationAnalyzer fragAnalyzer = new FragmentationAnalyzer(fs);
            FragmentationAnalyzer.FragmentationAnalysis fragAnalysis = fragAnalyzer.analyzeFragmentation();
            
            // Add fragmentation summary to response
            analysis.fragmentationRatio = fragAnalysis.fileFragmentation.fragmentationRatio;
            analysis.fragmentationImpactScore = fragAnalysis.performanceImpact.fragmentationImpactScore;
            analysis.defragmentationRecommended = fragAnalysis.performanceImpact.fragmentationImpactScore > 30.0;
            analysis.worstFragmentedFileCount = fragAnalysis.fileFragmentation.worstFiles.size();
            analysis.freeSpaceFragmentationRatio = fragAnalysis.freeSpaceFragmentation.freeSpaceFragmentationRatio;
            
        } catch (Exception e) {
            System.err.println("Warning: Fragmentation analysis failed: " + e.getMessage());
            // Set default values if fragmentation analysis fails
            analysis.fragmentationRatio = 0.0;
            analysis.fragmentationImpactScore = 0.0;
            analysis.defragmentationRecommended = false;
            analysis.worstFragmentedFileCount = 0;
            analysis.freeSpaceFragmentationRatio = 0.0;
        }
        
        return analysis;
    }
    
    private long calculateTotalClusters(BootSector bootSector) {
        long dataSectors = bootSector.getTotalSectors() - bootSector.getFirstDataSector();
        return dataSectors / bootSector.getSectorsPerCluster();
    }
    
    private int getRecommendedClusterSize(long volumeSize, String fatType) {
        // Simplified recommendation logic
        if ("FAT32".equals(fatType)) {
            if (volumeSize <= 8L * 1024 * 1024 * 1024) return 4096;      // 8GB -> 4KB
            if (volumeSize <= 16L * 1024 * 1024 * 1024) return 8192;     // 16GB -> 8KB
            if (volumeSize <= 32L * 1024 * 1024 * 1024) return 16384;    // 32GB -> 16KB
            return 32768; // >32GB -> 32KB
        } else if ("FAT16".equals(fatType)) {
            if (volumeSize <= 16L * 1024 * 1024) return 1024;           // 16MB -> 1KB
            if (volumeSize <= 128L * 1024 * 1024) return 2048;          // 128MB -> 2KB
            if (volumeSize <= 256L * 1024 * 1024) return 4096;          // 256MB -> 4KB
            return 8192; // >256MB -> 8KB
        } else {
            return 512; // FAT12 -> 512B
        }
    }
    
    private void handleError(Object ctx, String message, Exception e) {
        try {
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.err.println(message + ": " + errorMessage);
            e.printStackTrace();
            ctx.getClass().getMethod("status", int.class).invoke(ctx, 500);
            ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                Map.of("error", message, "message", errorMessage));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    // DTOs
    public static class GraphResponse {
        public String imageName;
        public String format; // "dot", "svg", etc.
        public String content;
        public boolean expertMode;
    }
    
    public static class AnalysisResponse {
        public String imageName;
        public String fatType;
        public long totalSectors;
        public int bytesPerSector;
        public int sectorsPerCluster;
        public int clusterSize;
        public long totalSize;
        public long totalClusters;
        public long usedClusters;
        public long freeClusters;
        public double clusterUtilization;
        public int recommendedClusterSize;
        public boolean isOptimalClusterSize;
        public String clusterSizeInfo;
        
        // Fragmentation summary fields
        public double fragmentationRatio;
        public double fragmentationImpactScore;
        public boolean defragmentationRecommended;
        public int worstFragmentedFileCount;
        public double freeSpaceFragmentationRatio;
    }
} 