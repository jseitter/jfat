package net.seitter.jfat.analysis;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatTable;
import net.seitter.jfat.core.BootSector;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.core.FatFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprehensive fragmentation analysis for FAT filesystems.
 * Analyzes file fragmentation, free space fragmentation, and performance impact.
 */
public class FragmentationAnalyzer {
    
    private final FatFileSystem fileSystem;
    private final FatTable fatTable;
    private final BootSector bootSector;
    
    public FragmentationAnalyzer(FatFileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.fatTable = fileSystem.getFatTable();
        this.bootSector = fileSystem.getBootSector();
    }
    
    public FatFileSystem getFileSystem() {
        return fileSystem;
    }
    
    /**
     * Performs comprehensive fragmentation analysis
     */
    public FragmentationAnalysis analyzeFragmentation() throws IOException {
        System.out.println("🔍 Starting fragmentation analysis...");
        
        // Analyze all files in the filesystem
        List<FileFragmentationInfo> allFiles = analyzeAllFiles();
        
        // Calculate file fragmentation metrics
        FileFragmentationData fileFragmentation = calculateFileFragmentation(allFiles);
        
        // Analyze free space fragmentation
        FreeSpaceFragmentationData freeSpaceFragmentation = analyzeFreeSpaceFragmentation();
        
        // Calculate performance impact
        PerformanceImpactData performanceImpact = calculatePerformanceImpact(allFiles, freeSpaceFragmentation);
        
        // Generate recommendations
        List<FragmentationRecommendation> recommendations = generateRecommendations(
            fileFragmentation, freeSpaceFragmentation, performanceImpact
        );
        
        System.out.println("✅ Fragmentation analysis complete");
        
        return new FragmentationAnalysis(
            fileFragmentation,
            freeSpaceFragmentation,
            performanceImpact,
            recommendations
        );
    }
    
    /**
     * Analyzes fragmentation for all files in the filesystem
     */
    private List<FileFragmentationInfo> analyzeAllFiles() throws IOException {
        List<FileFragmentationInfo> allFiles = new ArrayList<>();
        analyzeDirectoryRecursive(fileSystem.getRootDirectory(), "", allFiles);
        return allFiles;
    }
    
    /**
     * Recursively analyzes files in directories
     */
    private void analyzeDirectoryRecursive(FatDirectory directory, String path, 
                                         List<FileFragmentationInfo> allFiles) throws IOException {
        List<FatEntry> entries = directory.list();
        
        for (FatEntry entry : entries) {
            String fullPath = path + "/" + entry.getName();
            
            if (entry.isDirectory()) {
                analyzeDirectoryRecursive((FatDirectory) entry, fullPath, allFiles);
            } else {
                FatFile file = (FatFile) entry;
                FileFragmentationInfo fragInfo = analyzeFileFragmentation(file, fullPath);
                allFiles.add(fragInfo);
            }
        }
    }
    
    /**
     * Analyzes fragmentation for a single file
     */
    private FileFragmentationInfo analyzeFileFragmentation(FatFile file, String path) throws IOException {
        if (file.getFirstCluster() == 0 || file.getSize() == 0) {
            // Empty file - no fragmentation
            return new FileFragmentationInfo(path, file.getName(), file.getSize(), 
                                           new ArrayList<>(), 0, 0.0, FragmentationSeverity.NONE);
        }
        
        // Get cluster chain for the file
        List<Long> clusterChain = fatTable.getClusterChain(file.getFirstCluster());
        
        if (clusterChain.size() <= 1) {
            // Single cluster file - no fragmentation
            return new FileFragmentationInfo(path, file.getName(), file.getSize(), 
                                           clusterChain, 1, 0.0, FragmentationSeverity.NONE);
        }
        
        // Count fragments (contiguous cluster sequences)
        int fragmentCount = 1;
        double totalGap = 0.0;
        int gapCount = 0;
        
        for (int i = 1; i < clusterChain.size(); i++) {
            long currentCluster = clusterChain.get(i);
            long previousCluster = clusterChain.get(i - 1);
            
            if (currentCluster != previousCluster + 1) {
                // Non-contiguous clusters - this is a fragment boundary
                fragmentCount++;
                double gap = currentCluster - previousCluster - 1;
                totalGap += gap;
                gapCount++;
            }
        }
        
        double averageGap = gapCount > 0 ? totalGap / gapCount : 0.0;
        FragmentationSeverity severity = calculateFragmentationSeverity(fragmentCount, averageGap, clusterChain.size());
        
        return new FileFragmentationInfo(path, file.getName(), file.getSize(), 
                                       clusterChain, fragmentCount, averageGap, severity);
    }
    
    /**
     * Calculates file fragmentation metrics
     */
    private FileFragmentationData calculateFileFragmentation(List<FileFragmentationInfo> allFiles) {
        if (allFiles.isEmpty()) {
            return new FileFragmentationData(0.0, 0.0, 0, 0.0, 0.0, new ArrayList<>());
        }
        
        // Count fragmented files
        long fragmentedFiles = allFiles.stream()
            .mapToLong(f -> f.fragmentCount > 1 ? 1 : 0)
            .sum();
        
        double fragmentationRatio = (double) fragmentedFiles / allFiles.size() * 100.0;
        
        // Calculate average fragments per file
        double averageFragmentsPerFile = allFiles.stream()
            .mapToInt(f -> f.fragmentCount)
            .average()
            .orElse(0.0);
        
        // Find maximum fragments
        int maxFileFragments = allFiles.stream()
            .mapToInt(f -> f.fragmentCount)
            .max()
            .orElse(0);
        
        // Calculate sequential cluster ratio
        long totalClusters = allFiles.stream()
            .mapToLong(f -> f.clusterChain.size())
            .sum();
        
        long sequentialClusters = allFiles.stream()
            .mapToLong(this::countSequentialClusters)
            .sum();
        
        double sequentialClusterRatio = totalClusters > 0 ? 
            (double) sequentialClusters / totalClusters * 100.0 : 100.0;
        
        // Calculate average cluster gap
        double averageClusterGap = allFiles.stream()
            .filter(f -> f.fragmentCount > 1)
            .mapToDouble(f -> f.averageGap)
            .average()
            .orElse(0.0);
        
        // Get worst fragmented files (top 10)
        List<FileFragmentationInfo> worstFiles = allFiles.stream()
            .filter(f -> f.fragmentCount > 1)
            .sorted((a, b) -> Integer.compare(b.fragmentCount, a.fragmentCount))
            .limit(10)
            .collect(Collectors.toList());
        
        return new FileFragmentationData(
            fragmentationRatio,
            averageFragmentsPerFile,
            maxFileFragments,
            sequentialClusterRatio,
            averageClusterGap,
            worstFiles
        );
    }
    
    /**
     * Counts sequential clusters in a file's cluster chain
     */
    private long countSequentialClusters(FileFragmentationInfo file) {
        if (file.clusterChain.size() <= 1) {
            return file.clusterChain.size();
        }
        
        long sequentialCount = 1; // First cluster is always "sequential"
        
        for (int i = 1; i < file.clusterChain.size(); i++) {
            if (file.clusterChain.get(i) == file.clusterChain.get(i - 1) + 1) {
                sequentialCount++;
            }
        }
        
        return sequentialCount;
    }
    
    /**
     * Analyzes free space fragmentation
     */
    private FreeSpaceFragmentationData analyzeFreeSpaceFragmentation() throws IOException {
        // Calculate total clusters
        long dataSectors = bootSector.getTotalSectors() - bootSector.getFirstDataSector();
        long totalClusters = dataSectors / bootSector.getSectorsPerCluster();
        
        // Find all free cluster blocks
        List<FreeSpaceBlock> freeBlocks = new ArrayList<>();
        long currentBlockStart = -1;
        long currentBlockSize = 0;
        
        for (long cluster = 2; cluster < totalClusters + 2; cluster++) {
            long entry = fatTable.getClusterEntry(cluster);
            
            if (entry == 0) { // Free cluster
                if (currentBlockStart == -1) {
                    // Start of new free block
                    currentBlockStart = cluster;
                    currentBlockSize = 1;
                } else {
                    // Continue current free block
                    currentBlockSize++;
                }
            } else {
                // End of free block (if any)
                if (currentBlockStart != -1) {
                    freeBlocks.add(new FreeSpaceBlock(currentBlockStart, currentBlockSize));
                    currentBlockStart = -1;
                    currentBlockSize = 0;
                }
            }
        }
        
        // Don't forget the last block if it extends to the end
        if (currentBlockStart != -1) {
            freeBlocks.add(new FreeSpaceBlock(currentBlockStart, currentBlockSize));
        }
        
        // Calculate metrics
        long totalFreeClusters = freeBlocks.stream()
            .mapToLong(block -> block.size)
            .sum();
        
        double freeSpaceFragmentationRatio = freeBlocks.size() > 1 ? 
            (double) (freeBlocks.size() - 1) / freeBlocks.size() * 100.0 : 0.0;
        
        long largestContiguousFreeBlock = freeBlocks.stream()
            .mapToLong(block -> block.size)
            .max()
            .orElse(0);
        
        double averageFreeBlockSize = freeBlocks.isEmpty() ? 0.0 :
            (double) totalFreeClusters / freeBlocks.size();
        
        return new FreeSpaceFragmentationData(
            freeSpaceFragmentationRatio,
            largestContiguousFreeBlock,
            averageFreeBlockSize,
            freeBlocks.size(),
            freeBlocks
        );
    }
    
    /**
     * Calculates performance impact of fragmentation
     */
    private PerformanceImpactData calculatePerformanceImpact(List<FileFragmentationInfo> allFiles,
                                                           FreeSpaceFragmentationData freeSpaceData) {
        // Calculate seek distance score (0-100, higher is worse)
        double seekDistanceScore = calculateSeekDistanceScore(allFiles);
        
        // Calculate overall fragmentation impact score (0-100)
        double fileFragRatio = allFiles.stream()
            .mapToLong(f -> f.fragmentCount > 1 ? 1 : 0)
            .sum() * 100.0 / Math.max(1, allFiles.size());
        
        double fragmentationImpactScore = (seekDistanceScore * 0.4) + 
                                        (fileFragRatio * 0.4) + 
                                        (freeSpaceData.freeSpaceFragmentationRatio * 0.2);
        
        // Calculate read efficiency score (0-100, higher is better)
        double readEfficiencyScore = 100.0 - fragmentationImpactScore;
        
        return new PerformanceImpactData(
            seekDistanceScore,
            Math.min(100.0, fragmentationImpactScore),
            Math.max(0.0, readEfficiencyScore)
        );
    }
    
    /**
     * Calculates weighted seek distance score
     */
    private double calculateSeekDistanceScore(List<FileFragmentationInfo> allFiles) {
        double totalWeightedDistance = 0.0;
        long totalFileSize = 0;
        
        for (FileFragmentationInfo file : allFiles) {
            if (file.fragmentCount <= 1) continue;
            
            double avgGap = file.averageGap;
            long fileSize = file.size;
            
            // Weight larger files more heavily
            double weight = Math.log(Math.max(1, fileSize / 1024.0)); // Log of KB
            totalWeightedDistance += avgGap * weight;
            totalFileSize += fileSize;
        }
        
        if (totalFileSize == 0) return 0.0;
        
        // Normalize to 0-100 scale
        return Math.min(100.0, totalWeightedDistance / (totalFileSize / 1024.0) * 10.0);
    }
    
    /**
     * Generates defragmentation recommendations
     */
    private List<FragmentationRecommendation> generateRecommendations(
            FileFragmentationData fileData,
            FreeSpaceFragmentationData freeData,
            PerformanceImpactData performanceData) {
        
        List<FragmentationRecommendation> recommendations = new ArrayList<>();
        
        // High priority: Severely fragmented files
        List<String> severelyFragmentedFiles = fileData.worstFiles.stream()
            .filter(f -> f.severity == FragmentationSeverity.SEVERE || f.fragmentCount > 10)
            .map(f -> f.path)
            .collect(Collectors.toList());
        
        if (!severelyFragmentedFiles.isEmpty()) {
            recommendations.add(new FragmentationRecommendation(
                RecommendationType.DEFRAGMENT_FILES,
                String.format("Defragment %d severely fragmented files", severelyFragmentedFiles.size()),
                Priority.HIGH,
                severelyFragmentedFiles
            ));
        }
        
        // Medium priority: Free space fragmentation
        if (freeData.freeSpaceFragmentationRatio > 50.0) {
            recommendations.add(new FragmentationRecommendation(
                RecommendationType.CONSOLIDATE_FREE_SPACE,
                String.format("Consolidate free space (%.1f%% fragmented)", freeData.freeSpaceFragmentationRatio),
                Priority.MEDIUM,
                new ArrayList<>()
            ));
        }
        
        // Low priority: General optimization
        if (performanceData.fragmentationImpactScore > 30.0) {
            recommendations.add(new FragmentationRecommendation(
                RecommendationType.FULL_DEFRAGMENTATION,
                "Consider full filesystem defragmentation for optimal performance",
                Priority.LOW,
                new ArrayList<>()
            ));
        }
        
        return recommendations;
    }
    
    /**
     * Calculates fragmentation severity for a file
     */
    private FragmentationSeverity calculateFragmentationSeverity(int fragmentCount, double averageGap, int totalClusters) {
        if (fragmentCount == 1) {
            return FragmentationSeverity.NONE;
        } else if (fragmentCount == 2 && averageGap < 10) {
            return FragmentationSeverity.LIGHT;
        } else if (fragmentCount <= 5 && averageGap < 50) {
            return FragmentationSeverity.MODERATE;
        } else if (fragmentCount <= 10 || averageGap < 100) {
            return FragmentationSeverity.HEAVY;
        } else {
            return FragmentationSeverity.SEVERE;
        }
    }
    
    // Data classes for fragmentation analysis results
    
    public static class FragmentationAnalysis {
        public final FileFragmentationData fileFragmentation;
        public final FreeSpaceFragmentationData freeSpaceFragmentation;
        public final PerformanceImpactData performanceImpact;
        public final List<FragmentationRecommendation> recommendations;
        
        public FragmentationAnalysis(FileFragmentationData fileFragmentation,
                                   FreeSpaceFragmentationData freeSpaceFragmentation,
                                   PerformanceImpactData performanceImpact,
                                   List<FragmentationRecommendation> recommendations) {
            this.fileFragmentation = fileFragmentation;
            this.freeSpaceFragmentation = freeSpaceFragmentation;
            this.performanceImpact = performanceImpact;
            this.recommendations = recommendations;
        }
    }
    
    public static class FileFragmentationData {
        public final double fragmentationRatio;
        public final double averageFragmentsPerFile;
        public final int maxFileFragments;
        public final double sequentialClusterRatio;
        public final double averageClusterGap;
        public final List<FileFragmentationInfo> worstFiles;
        
        public FileFragmentationData(double fragmentationRatio, double averageFragmentsPerFile,
                                   int maxFileFragments, double sequentialClusterRatio,
                                   double averageClusterGap, List<FileFragmentationInfo> worstFiles) {
            this.fragmentationRatio = fragmentationRatio;
            this.averageFragmentsPerFile = averageFragmentsPerFile;
            this.maxFileFragments = maxFileFragments;
            this.sequentialClusterRatio = sequentialClusterRatio;
            this.averageClusterGap = averageClusterGap;
            this.worstFiles = worstFiles;
        }
    }
    
    public static class FreeSpaceFragmentationData {
        public final double freeSpaceFragmentationRatio;
        public final long largestContiguousFreeBlock;
        public final double averageFreeBlockSize;
        public final int freeBlockCount;
        public final List<FreeSpaceBlock> freeSpaceMap;
        
        public FreeSpaceFragmentationData(double freeSpaceFragmentationRatio,
                                        long largestContiguousFreeBlock,
                                        double averageFreeBlockSize,
                                        int freeBlockCount,
                                        List<FreeSpaceBlock> freeSpaceMap) {
            this.freeSpaceFragmentationRatio = freeSpaceFragmentationRatio;
            this.largestContiguousFreeBlock = largestContiguousFreeBlock;
            this.averageFreeBlockSize = averageFreeBlockSize;
            this.freeBlockCount = freeBlockCount;
            this.freeSpaceMap = freeSpaceMap;
        }
    }
    
    public static class PerformanceImpactData {
        public final double seekDistanceScore;
        public final double fragmentationImpactScore;
        public final double readEfficiencyScore;
        
        public PerformanceImpactData(double seekDistanceScore,
                                   double fragmentationImpactScore,
                                   double readEfficiencyScore) {
            this.seekDistanceScore = seekDistanceScore;
            this.fragmentationImpactScore = fragmentationImpactScore;
            this.readEfficiencyScore = readEfficiencyScore;
        }
    }
    
    public static class FileFragmentationInfo {
        public final String path;
        public final String name;
        public final long size;
        public final List<Long> clusterChain;
        public final int fragmentCount;
        public final double averageGap;
        public final FragmentationSeverity severity;
        
        public FileFragmentationInfo(String path, String name, long size,
                                   List<Long> clusterChain, int fragmentCount,
                                   double averageGap, FragmentationSeverity severity) {
            this.path = path;
            this.name = name;
            this.size = size;
            this.clusterChain = clusterChain;
            this.fragmentCount = fragmentCount;
            this.averageGap = averageGap;
            this.severity = severity;
        }
    }
    
    public static class FreeSpaceBlock {
        public final long startCluster;
        public final long size;
        
        public FreeSpaceBlock(long startCluster, long size) {
            this.startCluster = startCluster;
            this.size = size;
        }
    }
    
    public static class FragmentationRecommendation {
        public final RecommendationType type;
        public final String description;
        public final Priority priority;
        public final List<String> affectedFiles;
        
        public FragmentationRecommendation(RecommendationType type, String description,
                                         Priority priority, List<String> affectedFiles) {
            this.type = type;
            this.description = description;
            this.priority = priority;
            this.affectedFiles = affectedFiles;
        }
    }
    
    public enum FragmentationSeverity {
        NONE, LIGHT, MODERATE, HEAVY, SEVERE
    }
    
    public enum RecommendationType {
        DEFRAGMENT_FILES, CONSOLIDATE_FREE_SPACE, FULL_DEFRAGMENTATION, OPTIMIZE_ALLOCATION
    }
    
    public enum Priority {
        LOW, MEDIUM, HIGH
    }
} 