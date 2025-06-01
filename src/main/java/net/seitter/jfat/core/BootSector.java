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
     * @throws IOException If the boot sector cannot be read
     */
    public BootSector(DeviceAccess device) throws IOException {
        // Read the boot sector (usually the first 512 bytes)
        rawData = device.read(0, 512);
        parse();
    }
    
    /**
     * Parses the boot sector data to extract filesystem information.
     */
    private void parse() {
        // Basic parameters from the BPB (BIOS Parameter Block)
        bytesPerSector = FatUtils.readUInt16(rawData, 11);
        sectorsPerCluster = rawData[13] & 0xFF;
        reservedSectorCount = FatUtils.readUInt16(rawData, 14);
        numberOfFats = rawData[16] & 0xFF;
        rootEntryCount = FatUtils.readUInt16(rawData, 17);
        
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
} 