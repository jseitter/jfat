package net.seitter.jfat.core;

import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.FatUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Represents the boot sector of a FAT filesystem.
 * This contains key information about the filesystem structure.
 */
public class BootSector {
    // Common fields for all FAT types
    private int bytesPerSector;
    private int sectorsPerCluster;
    private int reservedSectorCount;
    private int numberOfFats;
    private int rootEntryCount;
    private long totalSectors;
    private int sectorsPerFat;
    private int rootDirFirstSector;
    private FatType fatType;
    
    // FAT32 specific fields
    private long fat32RootCluster;
    private int fat32InfoSector;
    
    private final byte[] rawData;
    
    /**
     * Creates a new BootSector by reading from the given device.
     *
     * @param device The device to read from
     * @throws IOException If the boot sector cannot be read or is invalid
     */
    public BootSector(DeviceAccess device) throws IOException {
        // Read the boot sector (usually the first 512 bytes)
        rawData = device.read(0, 512);
        parse();
    }
    
    /**
     * Parses the boot sector data to extract filesystem information.
     */
    private void parse() throws IOException {
        // Basic parameters from the BPB (BIOS Parameter Block)
        bytesPerSector = FatUtils.readUInt16(rawData, 11);
        sectorsPerCluster = rawData[13] & 0xFF;
        reservedSectorCount = FatUtils.readUInt16(rawData, 14);
        numberOfFats = rawData[16] & 0xFF;
        rootEntryCount = FatUtils.readUInt16(rawData, 17);
        
        // Validate critical boot sector parameters
        validateBootSectorParameters();
        
        // Total sectors (16-bit field or 32-bit field)
        int totalSectors16 = FatUtils.readUInt16(rawData, 19);
        totalSectors = (totalSectors16 != 0) ? totalSectors16 : FatUtils.readUInt32(rawData, 32);
        
        // Sectors per FAT
        sectorsPerFat = FatUtils.readUInt16(rawData, 22);
        
        // Determine if this is FAT32 early (before calculating other values)
        // FAT32 is indicated by sectorsPerFat == 0 and rootEntryCount == 0
        boolean isFat32 = (sectorsPerFat == 0 && rootEntryCount == 0);
        
        // For FAT32, we need additional fields
        if (isFat32) {
            sectorsPerFat = (int) FatUtils.readUInt32(rawData, 36);
            fat32RootCluster = FatUtils.readUInt32(rawData, 44);
            fat32InfoSector = FatUtils.readUInt16(rawData, 48);
        }
        
        // Calculate the first sector of the root directory
        rootDirFirstSector = reservedSectorCount + (numberOfFats * sectorsPerFat);
        
        // Calculate root directory sector count
        int rootDirSectorCount = isFat32 ? 0 : ((rootEntryCount * 32) + bytesPerSector - 1) / bytesPerSector;
        
        // Determine the FAT type based on the number of clusters
        long dataSectors = totalSectors - (reservedSectorCount + (numberOfFats * sectorsPerFat) + rootDirSectorCount);
        long totalClusters = dataSectors / sectorsPerCluster;
        
        if (isFat32 || totalClusters >= 65525) {
            fatType = FatType.FAT32;
        } else if (totalClusters >= 4085) {
            fatType = FatType.FAT16;
        } else {
            fatType = FatType.FAT12;
        }
    }
    
    /**
     * Validates critical boot sector parameters according to FAT specification.
     * 
     * @throws IOException If any parameter is invalid
     */
    private void validateBootSectorParameters() throws IOException {
        // Validate bytes per sector
        if (!isValidBytesPerSector(bytesPerSector)) {
            throw new IOException("Invalid bytes per sector: " + bytesPerSector + 
                                ". Valid values are: 512, 1024, 2048, 4096");
        }
        
        // Validate sectors per cluster
        if (!isValidSectorsPerCluster(sectorsPerCluster)) {
            throw new IOException("Invalid sectors per cluster: " + sectorsPerCluster + 
                                ". Must be a power of 2 between 1 and 128 inclusive");
        }
        
        // Validate cluster size doesn't exceed 32MB
        long clusterSize = (long) bytesPerSector * sectorsPerCluster;
        if (clusterSize > 32 * 1024 * 1024) {
            throw new IOException("Cluster size too large: " + clusterSize + " bytes. " +
                                "Maximum cluster size is 32MB");
        }
        
        // Validate number of FATs
        if (numberOfFats < 1 || numberOfFats > 2) {
            throw new IOException("Invalid number of FATs: " + numberOfFats + 
                                ". Valid values are 1 or 2");
        }
    }
    
    /**
     * Checks if the given bytes per sector value is valid according to FAT specification.
     * 
     * @param bytesPerSector The bytes per sector value to validate
     * @return True if valid, false otherwise
     */
    public static boolean isValidBytesPerSector(int bytesPerSector) {
        return bytesPerSector == 512 || bytesPerSector == 1024 || 
               bytesPerSector == 2048 || bytesPerSector == 4096;
    }
    
    /**
     * Checks if the given sectors per cluster value is valid according to FAT specification.
     * Sectors per cluster must be a power of 2 between 1 and 128 inclusive.
     * 
     * @param sectorsPerCluster The sectors per cluster value to validate
     * @return True if valid, false otherwise
     */
    public static boolean isValidSectorsPerCluster(int sectorsPerCluster) {
        // Must be between 1 and 128 inclusive
        if (sectorsPerCluster < 1 || sectorsPerCluster > 128) {
            return false;
        }
        
        // Must be a power of 2
        return (sectorsPerCluster & (sectorsPerCluster - 1)) == 0;
    }
    
    /**
     * Calculates the optimal sectors per cluster for a given volume size and FAT type
     * following Microsoft's recommendations.
     * 
     * @param volumeSizeBytes The total volume size in bytes
     * @param fatType The FAT type
     * @param bytesPerSector The bytes per sector
     * @return The recommended sectors per cluster
     */
    public static int getRecommendedSectorsPerCluster(long volumeSizeBytes, FatType fatType, int bytesPerSector) {
        long volumeSizeMB = volumeSizeBytes / (1024 * 1024);
        
        // Convert to sectors for easier calculation
        long volumeSectors = volumeSizeBytes / bytesPerSector;
        
        switch (fatType) {
            case FAT12:
                // FAT12 volumes are typically small (≤ 4MB)
                return 1;
                
            case FAT16:
                // FAT16 recommendations based on Microsoft specifications
                if (volumeSizeMB <= 16) return 2;      // 1K cluster
                if (volumeSizeMB <= 128) return 4;     // 2K cluster  
                if (volumeSizeMB <= 256) return 8;     // 4K cluster
                if (volumeSizeMB <= 512) return 16;    // 8K cluster
                if (volumeSizeMB <= 1024) return 32;   // 16K cluster
                if (volumeSizeMB <= 2048) return 64;   // 32K cluster
                return 64; // Maximum for FAT16
                
            case FAT32:
                // FAT32 recommendations based on Microsoft specifications
                if (volumeSizeMB <= 260) return 1;     // 0.5K cluster (for 512 byte sectors)
                if (volumeSizeMB <= 8192) return 8;    // 4K cluster
                if (volumeSizeMB <= 16384) return 16;  // 8K cluster  
                if (volumeSizeMB <= 32768) return 32;  // 16K cluster
                return 64; // 32K cluster for > 32GB
                
            default:
                return 1;
        }
    }
    
    /**
     * Gets the number of sectors occupied by the root directory.
     * For FAT32, this is 0 as the root directory is stored in clusters.
     *
     * @return The number of sectors
     */
    public int getRootDirectorySectorCount() {
        if (fatType == FatType.FAT32) {
            return 0;
        }
        // Each directory entry is 32 bytes
        return ((rootEntryCount * 32) + bytesPerSector - 1) / bytesPerSector;
    }
    
    /**
     * Gets the first sector of the data area.
     *
     * @return The sector number
     */
    public int getFirstDataSector() {
        return rootDirFirstSector + getRootDirectorySectorCount();
    }
    
    /**
     * Converts a cluster number to a sector number.
     *
     * @param clusterNumber The cluster number (2-based)
     * @return The sector number
     */
    public long clusterToSector(long clusterNumber) {
        // Clusters are 2-based (first cluster is 2)
        return getFirstDataSector() + ((clusterNumber - 2) * sectorsPerCluster);
    }
    
    /**
     * Gets the type of FAT filesystem.
     *
     * @return The FAT type
     */
    public FatType getFatType() {
        return fatType;
    }
    
    /**
     * Gets the number of bytes per sector.
     *
     * @return The number of bytes
     */
    public int getBytesPerSector() {
        return bytesPerSector;
    }
    
    /**
     * Gets the number of sectors per cluster.
     *
     * @return The number of sectors
     */
    public int getSectorsPerCluster() {
        return sectorsPerCluster;
    }
    
    /**
     * Gets the number of reserved sectors.
     *
     * @return The number of sectors
     */
    public int getReservedSectorCount() {
        return reservedSectorCount;
    }
    
    /**
     * Gets the number of FATs.
     *
     * @return The number of FATs
     */
    public int getNumberOfFats() {
        return numberOfFats;
    }
    
    /**
     * Gets the number of root directory entries.
     * For FAT32, this is typically 0.
     *
     * @return The number of entries
     */
    public int getRootEntryCount() {
        return rootEntryCount;
    }
    
    /**
     * Gets the total number of sectors.
     *
     * @return The number of sectors
     */
    public long getTotalSectors() {
        return totalSectors;
    }
    
    /**
     * Gets the number of sectors per FAT.
     *
     * @return The number of sectors
     */
    public int getSectorsPerFat() {
        return sectorsPerFat;
    }
    
    /**
     * Gets the first sector of the root directory.
     *
     * @return The sector number
     */
    public int getRootDirFirstSector() {
        return rootDirFirstSector;
    }
    
    /**
     * Gets the root cluster for FAT32.
     *
     * @return The cluster number
     */
    public long getFat32RootCluster() {
        return fat32RootCluster;
    }
    
    /**
     * Gets the FAT32 info sector.
     *
     * @return The sector number
     */
    public int getFat32InfoSector() {
        return fat32InfoSector;
    }
    
    /**
     * Gets the size of a cluster in bytes.
     *
     * @return The cluster size in bytes
     */
    public int getClusterSizeBytes() {
        return bytesPerSector * sectorsPerCluster;
    }
    
    /**
     * Gets detailed cluster size information for this filesystem.
     * 
     * @return A formatted string with cluster size details
     */
    public String getClusterSizeInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Filesystem Type: ").append(fatType).append("\n");
        info.append("Bytes per Sector: ").append(bytesPerSector).append("\n");
        info.append("Sectors per Cluster: ").append(sectorsPerCluster).append("\n");
        info.append("Cluster Size: ").append(getClusterSizeBytes()).append(" bytes");
        
        // Format cluster size in human-readable form
        int clusterSizeBytes = getClusterSizeBytes();
        if (clusterSizeBytes >= 1024 * 1024) {
            info.append(" (").append(clusterSizeBytes / (1024 * 1024)).append(" MB)");
        } else if (clusterSizeBytes >= 1024) {
            info.append(" (").append(clusterSizeBytes / 1024).append(" KB)");
        }
        
        info.append("\n");
        info.append("Total Sectors: ").append(totalSectors).append("\n");
        info.append("Total Size: ");
        long totalSizeBytes = totalSectors * bytesPerSector;
        if (totalSizeBytes >= 1024 * 1024 * 1024) {
            info.append(totalSizeBytes / (1024 * 1024 * 1024)).append(" GB");
        } else if (totalSizeBytes >= 1024 * 1024) {
            info.append(totalSizeBytes / (1024 * 1024)).append(" MB");
        } else if (totalSizeBytes >= 1024) {
            info.append(totalSizeBytes / 1024).append(" KB");
        } else {
            info.append(totalSizeBytes).append(" bytes");
        }
        
        return info.toString();
    }
} 