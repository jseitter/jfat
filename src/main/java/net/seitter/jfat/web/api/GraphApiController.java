package net.seitter.jfat.web.api;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.BootSector;
import net.seitter.jfat.core.FatTable;
import net.seitter.jfat.io.DeviceAccess;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
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
    
    private String generateBasicDotGraph(FatFileSystem fs) throws IOException {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        
        out.println("digraph FatFilesystem {");
        out.println("  rankdir=TB;");
        out.println("  node [shape=box, style=filled];");
        out.println();
        
        // Add filesystem info
        BootSector bootSector = fs.getBootSector();
        out.println("  subgraph cluster_info {");
        out.println("    label=\"Filesystem Information\";");
        out.println("    style=filled;");
        out.println("    color=lightgrey;");
        out.printf("    info [label=\"%s\\nCluster Size: %d bytes\\nSectors per Cluster: %d\", fillcolor=lightblue];%n",
                bootSector.getFatType(),
                bootSector.getClusterSizeBytes(),
                bootSector.getSectorsPerCluster());
        out.println("  }");
        out.println();
        
        // Add root directory
        out.println("  root [label=\"Root Directory\", fillcolor=yellow];");
        
        // TODO: Add directory traversal and file nodes
        // This would require implementing a proper graph traversal
        // For now, just show basic structure
        
        out.println("}");
        
        return writer.toString();
    }
    
    private String generateExpertDotGraph(FatFileSystem fs) throws IOException {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        
        out.println("digraph FatFilesystemExpert {");
        out.println("  rankdir=TB;");
        out.println("  node [shape=box, style=filled];");
        out.println();
        
        // Add detailed filesystem info
        BootSector bootSector = fs.getBootSector();
        FatTable fatTable = fs.getFatTable();
        
        out.println("  subgraph cluster_boot {");
        out.println("    label=\"Boot Sector Information\";");
        out.println("    style=filled;");
        out.println("    color=lightgrey;");
        out.printf("    boot [label=\"%s\\nTotal Sectors: %d\\nBytes per Sector: %d\\nSectors per Cluster: %d\\nCluster Size: %d bytes\\nReserved Sectors: %d\\nNumber of FATs: %d\", fillcolor=lightblue];%n",
                bootSector.getFatType(),
                bootSector.getTotalSectors(),
                bootSector.getBytesPerSector(),
                bootSector.getSectorsPerCluster(),
                bootSector.getClusterSizeBytes(),
                bootSector.getReservedSectorCount(),
                bootSector.getNumberOfFats());
        out.println("  }");
        out.println();
        
        // Add FAT table information
        out.println("  subgraph cluster_fat {");
        out.println("    label=\"FAT Table Summary\";");
        out.println("    style=filled;");
        out.println("    color=lightyellow;");
        
        // Calculate cluster statistics
        long totalClusters = calculateTotalClusters(bootSector);
        long usedClusters = 0; // Would need to implement cluster counting
        
        out.printf("    fat_info [label=\"Total Clusters: %d\\nUsed Clusters: %d\\nFree Clusters: %d\\nCluster Utilization: %.1f%%\", fillcolor=lightgreen];%n",
                totalClusters,
                usedClusters,
                totalClusters - usedClusters,
                totalClusters > 0 ? (usedClusters * 100.0 / totalClusters) : 0.0);
        out.println("  }");
        out.println();
        
        out.println("  root [label=\"Root Directory\\nCluster: 2\", fillcolor=yellow];");
        out.println("  boot -> fat_info [style=dashed];");
        out.println("  fat_info -> root;");
        
        out.println("}");
        
        return writer.toString();
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
            System.err.println(message + ": " + e.getMessage());
            e.printStackTrace();
            ctx.getClass().getMethod("status", int.class).invoke(ctx, 500);
            ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                Map.of("error", message, "message", e.getMessage()));
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
    }
} 