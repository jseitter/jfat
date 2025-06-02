package net.seitter.jfat.util;

import net.seitter.jfat.core.FatType;
import net.seitter.jfat.core.BootSector;
import net.seitter.jfat.io.DeviceAccess;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Utility class for creating FAT disk images for testing.
 */
public class DiskImageCreator {
    
    // Default disk image parameters
    private static final int DEFAULT_SECTOR_SIZE = 512;
    private static final int DEFAULT_SECTORS_PER_CLUSTER = 1;
    private static final int DEFAULT_RESERVED_SECTORS = 1;
    private static final int DEFAULT_NUMBER_OF_FATS = 2;
    private static final int DEFAULT_ROOT_ENTRIES = 512;
    private static final int DEFAULT_SECTORS_PER_TRACK = 63;
    private static final int DEFAULT_HEADS = 255;
    
    /**
     * Creates a FAT disk image with the given parameters using recommended cluster size.
     *
     * @param imagePath The path to the disk image file to create
     * @param fatType   The FAT type (FAT12, FAT16, or FAT32)
     * @param sizeMB    The size of the disk image in MB
     * @throws IOException If the disk image cannot be created
     */
    public static void createDiskImage(String imagePath, FatType fatType, int sizeMB) throws IOException {
        // Calculate recommended cluster size
        long volumeSizeBytes = (long) sizeMB * 1024 * 1024;
        int recommendedSectorsPerCluster = BootSector.getRecommendedSectorsPerCluster(
            volumeSizeBytes, fatType, DEFAULT_SECTOR_SIZE);
        
        createDiskImage(imagePath, fatType, sizeMB, DEFAULT_SECTOR_SIZE, recommendedSectorsPerCluster);
    }
    
    /**
     * Creates a FAT disk image with custom sector size and cluster configuration.
     *
     * @param imagePath The path to the disk image file to create
     * @param fatType   The FAT type (FAT12, FAT16, or FAT32)
     * @param sizeMB    The size of the disk image in MB
     * @param bytesPerSector The number of bytes per sector (512, 1024, 2048, or 4096)
     * @param sectorsPerCluster The number of sectors per cluster (must be power of 2, 1-128)
     * @throws IOException If the disk image cannot be created
     */
    public static void createDiskImage(String imagePath, FatType fatType, int sizeMB, 
                                     int bytesPerSector, int sectorsPerCluster) throws IOException {
        // Validate parameters
        validateDiskImageParameters(fatType, sizeMB, bytesPerSector, sectorsPerCluster);
        
        // Calculate the total number of sectors
        long totalSectors = (long) sizeMB * 1024 * 1024 / bytesPerSector;
        
        // Create a new disk image file
        try (RandomAccessFile file = new RandomAccessFile(imagePath, "rw")) {
            // Set the file size
            file.setLength(totalSectors * bytesPerSector);
            
            // Write the boot sector with custom parameters
            writeBootSector(file, fatType, totalSectors, bytesPerSector, sectorsPerCluster);
            
            // Write the FATs
            int sectorsPerFat = calculateSectorsPerFat(fatType, totalSectors, bytesPerSector, sectorsPerCluster);
            writeFATs(file, fatType, sectorsPerFat, bytesPerSector);
            
            // Initialize the root directory
            writeRootDirectory(file, fatType, bytesPerSector, sectorsPerCluster);
        }
    }
    
    /**
     * Validates disk image creation parameters.
     *
     * @param fatType The FAT type
     * @param sizeMB The size in MB
     * @param bytesPerSector The bytes per sector
     * @param sectorsPerCluster The sectors per cluster
     * @throws IOException If any parameter is invalid
     */
    private static void validateDiskImageParameters(FatType fatType, int sizeMB, 
                                                   int bytesPerSector, int sectorsPerCluster) throws IOException {
        // Validate bytes per sector
        if (!BootSector.isValidBytesPerSector(bytesPerSector)) {
            throw new IOException("Invalid bytes per sector: " + bytesPerSector + 
                                ". Valid values are: 512, 1024, 2048, 4096");
        }
        
        // Validate sectors per cluster
        if (!BootSector.isValidSectorsPerCluster(sectorsPerCluster)) {
            throw new IOException("Invalid sectors per cluster: " + sectorsPerCluster + 
                                ". Must be a power of 2 between 1 and 128 inclusive");
        }
        
        // Validate cluster size doesn't exceed 32MB
        long clusterSize = (long) bytesPerSector * sectorsPerCluster;
        if (clusterSize > 32 * 1024 * 1024) {
            throw new IOException("Cluster size too large: " + clusterSize + " bytes. " +
                                "Maximum cluster size is 32MB");
        }
        
        // Warn about non-standard volume sizes (but don't fail)
        if (fatType == FatType.FAT32 && sizeMB < 512) {
            System.err.println("Warning: FAT32 volumes smaller than 512MB are not recommended " +
                             "for production use. Given size: " + sizeMB + "MB");
        }
        
        if (fatType == FatType.FAT12 && sizeMB > 32) {
            System.err.println("Warning: FAT12 volumes larger than 32MB may have compatibility issues. " +
                             "Given size: " + sizeMB + "MB");
        }
        
        // Hard limits that should cause failures
        if (fatType == FatType.FAT32 && sizeMB < 8) {
            throw new IOException("FAT32 volumes must be at least 8MB. Given size: " + sizeMB + "MB");
        }
        
        if (fatType == FatType.FAT12 && sizeMB > 512) {
            throw new IOException("FAT12 volumes cannot exceed 512MB. Given size: " + sizeMB + "MB");
        }
    }
    
    /**
     * Calculates the number of sectors per FAT based on the FAT type and total sectors.
     *
     * @param fatType The FAT type
     * @param totalSectors The total number of sectors
     * @param bytesPerSector The number of bytes per sector
     * @param sectorsPerCluster The number of sectors per cluster
     * @return The number of sectors per FAT
     */
    private static int calculateSectorsPerFat(FatType fatType, long totalSectors, 
                                            int bytesPerSector, int sectorsPerCluster) {
        int bytesPerFatEntry;
        switch (fatType) {
            case FAT12:
                bytesPerFatEntry = 3; // 12 bits, but we need to consider full bytes
                break;
            case FAT16:
                bytesPerFatEntry = 2;
                break;
            case FAT32:
                bytesPerFatEntry = 4;
                break;
            default:
                throw new IllegalArgumentException("Unsupported FAT type");
        }
        
        // Estimate the number of clusters (this is simplified)
        long dataSectors = totalSectors - DEFAULT_RESERVED_SECTORS -
                          (DEFAULT_NUMBER_OF_FATS * (totalSectors / 100)) - // Rough estimate for FAT size
                          (DEFAULT_ROOT_ENTRIES * 32 / bytesPerSector); // Root directory size
        long clusters = dataSectors / sectorsPerCluster;
        
        // Calculate sectors per FAT (rounded up)
        int sectorsPerFat = (int) ((clusters * bytesPerFatEntry + bytesPerSector - 1) / bytesPerSector);
        
        // For FAT32, ensure a minimum size
        if (fatType == FatType.FAT32 && sectorsPerFat < 64) {
            sectorsPerFat = 64;
        }
        
        return sectorsPerFat;
    }
    
    /**
     * Writes the boot sector to the disk image.
     *
     * @param file The disk image file
     * @param fatType The FAT type
     * @param totalSectors The total number of sectors
     * @param bytesPerSector The number of bytes per sector
     * @param sectorsPerCluster The number of sectors per cluster
     * @throws IOException If the boot sector cannot be written
     */
    private static void writeBootSector(RandomAccessFile file, FatType fatType, long totalSectors,
                                      int bytesPerSector, int sectorsPerCluster) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(bytesPerSector);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Jump instruction (EB 3C 90) and OEM name
        buffer.put((byte) 0xEB);
        buffer.put((byte) 0x3C);
        buffer.put((byte) 0x90);
        buffer.put("JFAT    ".getBytes()); // 8 bytes for OEM name
        
        // BIOS Parameter Block (BPB)
        buffer.putShort((short) bytesPerSector); // Bytes per sector
        buffer.put((byte) sectorsPerCluster); // Sectors per cluster
        buffer.putShort((short) DEFAULT_RESERVED_SECTORS); // Reserved sectors
        buffer.put((byte) DEFAULT_NUMBER_OF_FATS); // Number of FATs
        
        // Root entries (0 for FAT32)
        buffer.putShort(fatType == FatType.FAT32 ? 0 : (short) DEFAULT_ROOT_ENTRIES);
        
        // Total sectors (16-bit value, 0 if more than 65535)
        buffer.putShort(totalSectors <= 65535 ? (short) totalSectors : 0);
        
        // Media descriptor (F8 for fixed disk)
        buffer.put((byte) 0xF8);
        
        // Sectors per FAT (0 for FAT32, calculated for FAT12/16)
        int sectorsPerFat = calculateSectorsPerFat(fatType, totalSectors, bytesPerSector, sectorsPerCluster);
        buffer.putShort(fatType == FatType.FAT32 ? 0 : (short) sectorsPerFat);
        
        // Sectors per track and number of heads
        buffer.putShort((short) DEFAULT_SECTORS_PER_TRACK);
        buffer.putShort((short) DEFAULT_HEADS);
        
        // Hidden sectors (0 for simplicity)
        buffer.putInt(0);
        
        // Total sectors (32-bit value)
        buffer.putInt((int) totalSectors);
        
        // FAT32-specific fields
        if (fatType == FatType.FAT32) {
            buffer.putInt(sectorsPerFat); // Sectors per FAT
            buffer.putShort((short) 0); // Flags
            buffer.putShort((short) 0); // FAT version
            buffer.putInt(2); // Root directory cluster
            buffer.putShort((short) 1); // FSInfo sector
            buffer.putShort((short) 0); // Backup boot sector
            buffer.position(buffer.position() + 12); // Reserved (12 bytes)
        }
        
        // Drive number, flags, signature, volume ID, volume label, and filesystem type
        buffer.put((byte) 0x80); // Drive number (80h for hard disk)
        buffer.put((byte) 0); // Reserved
        buffer.put((byte) 0x29); // Extended boot signature
        buffer.putInt(0x12345678); // Volume ID
        
        // Volume label (11 bytes)
        buffer.put("JFAT VOLUME".getBytes());
        
        // Filesystem type (8 bytes)
        switch (fatType) {
            case FAT12:
                buffer.put("FAT12   ".getBytes());
                break;
            case FAT16:
                buffer.put("FAT16   ".getBytes());
                break;
            case FAT32:
                buffer.put("FAT32   ".getBytes());
                break;
        }
        
        // Boot code and signature
        buffer.position(bytesPerSector - 2);
        buffer.put((byte) 0x55);
        buffer.put((byte) 0xAA);
        
        // Write the buffer to the file
        buffer.flip();
        file.seek(0);
        file.write(buffer.array());
    }
    
    /**
     * Writes the FATs to the disk image.
     *
     * @param file The disk image file
     * @param fatType The FAT type
     * @param sectorsPerFat The number of sectors per FAT
     * @param bytesPerSector The number of bytes per sector
     * @throws IOException If the FATs cannot be written
     */
    private static void writeFATs(RandomAccessFile file, FatType fatType, int sectorsPerFat, int bytesPerSector) throws IOException {
        int fatSize = sectorsPerFat * bytesPerSector;
        byte[] fatData = new byte[fatSize];
        
        // Initialize the FAT data
        switch (fatType) {
            case FAT12:
                // First two entries are reserved
                fatData[0] = (byte) 0xF8; // Media descriptor
                fatData[1] = (byte) 0xFF;
                fatData[2] = (byte) 0xFF;
                break;
            case FAT16:
                // First two entries are reserved
                fatData[0] = (byte) 0xF8; // Media descriptor
                fatData[1] = (byte) 0xFF;
                fatData[2] = (byte) 0xFF;
                fatData[3] = (byte) 0xFF;
                break;
            case FAT32:
                // First two entries are reserved
                fatData[0] = (byte) 0xF8; // Media descriptor
                fatData[1] = (byte) 0xFF;
                fatData[2] = (byte) 0xFF;
                fatData[3] = (byte) 0xFF;
                fatData[4] = (byte) 0x0F;
                fatData[5] = (byte) 0xFF;
                fatData[6] = (byte) 0xFF;
                fatData[7] = (byte) 0x0F;
                
                // Mark the root directory cluster (cluster 2) as end of chain
                fatData[8] = (byte) 0xFF;
                fatData[9] = (byte) 0xFF;
                fatData[10] = (byte) 0xFF;
                fatData[11] = (byte) 0x0F;
                break;
        }
        
        // Write the FATs
        for (int i = 0; i < DEFAULT_NUMBER_OF_FATS; i++) {
            file.seek(DEFAULT_RESERVED_SECTORS * bytesPerSector + i * fatSize);
            file.write(fatData);
        }
    }
    
    /**
     * Writes an empty root directory to the disk image.
     *
     * @param file The disk image file
     * @param fatType The FAT type
     * @param bytesPerSector The number of bytes per sector
     * @param sectorsPerCluster The number of sectors per cluster
     * @throws IOException If the root directory cannot be written
     */
    private static void writeRootDirectory(RandomAccessFile file, FatType fatType, 
                                         int bytesPerSector, int sectorsPerCluster) throws IOException {
        // For FAT32, the root directory is stored in clusters, so we don't need to initialize it here
        if (fatType == FatType.FAT32) {
            return;
        }
        
        // For FAT12/16, the root directory has a fixed location after the FATs
        int rootDirSize = DEFAULT_ROOT_ENTRIES * 32; // 32 bytes per entry
        byte[] rootDirData = new byte[rootDirSize];
        
        // Write the root directory
        int fatSize = calculateSectorsPerFat(fatType, file.length() / bytesPerSector, bytesPerSector, sectorsPerCluster) * bytesPerSector;
        file.seek(DEFAULT_RESERVED_SECTORS * bytesPerSector + DEFAULT_NUMBER_OF_FATS * fatSize);
        file.write(rootDirData);
    }
    
    /**
     * Gets all valid cluster sizes for the given sector size.
     * 
     * @param bytesPerSector The bytes per sector
     * @return Array of valid cluster sizes in bytes
     */
    public static int[] getValidClusterSizes(int bytesPerSector) {
        if (!BootSector.isValidBytesPerSector(bytesPerSector)) {
            throw new IllegalArgumentException("Invalid bytes per sector: " + bytesPerSector);
        }
        
        int[] validSectorsPerCluster = {1, 2, 4, 8, 16, 32, 64, 128};
        int[] clusterSizes = new int[validSectorsPerCluster.length];
        
        for (int i = 0; i < validSectorsPerCluster.length; i++) {
            int clusterSize = bytesPerSector * validSectorsPerCluster[i];
            // Stop if cluster size exceeds 32MB
            if (clusterSize > 32 * 1024 * 1024) {
                int[] result = new int[i];
                System.arraycopy(clusterSizes, 0, result, 0, i);
                return result;
            }
            clusterSizes[i] = clusterSize;
        }
        
        return clusterSizes;
    }
    
    /**
     * Formats a cluster size value for display.
     * 
     * @param clusterSizeBytes The cluster size in bytes
     * @return Formatted string (e.g., "4 KB", "2 MB")
     */
    public static String formatClusterSize(int clusterSizeBytes) {
        if (clusterSizeBytes >= 1024 * 1024) {
            return (clusterSizeBytes / (1024 * 1024)) + " MB";
        } else if (clusterSizeBytes >= 1024) {
            return (clusterSizeBytes / 1024) + " KB";
        } else {
            return clusterSizeBytes + " bytes";
        }
    }
    
    /**
     * Displays cluster size information for debugging and educational purposes.
     * 
     * @param volumeSizeMB The volume size in MB
     * @param fatType The FAT type
     * @param bytesPerSector The bytes per sector
     */
    public static void displayClusterSizeInfo(int volumeSizeMB, FatType fatType, int bytesPerSector) {
        System.out.println("\nCluster Size Information:");
        System.out.println("Volume Size: " + volumeSizeMB + " MB");
        System.out.println("FAT Type: " + fatType);
        System.out.println("Bytes per Sector: " + bytesPerSector);
        
        long volumeSizeBytes = (long) volumeSizeMB * 1024 * 1024;
        int recommendedSectorsPerCluster = BootSector.getRecommendedSectorsPerCluster(
            volumeSizeBytes, fatType, bytesPerSector);
        int recommendedClusterSize = bytesPerSector * recommendedSectorsPerCluster;
        
        System.out.println("Recommended Cluster Size: " + formatClusterSize(recommendedClusterSize) + 
                         " (" + recommendedSectorsPerCluster + " sectors per cluster)");
        
        System.out.println("\nValid cluster sizes for " + bytesPerSector + "-byte sectors:");
        int[] validClusterSizes = getValidClusterSizes(bytesPerSector);
        for (int clusterSize : validClusterSizes) {
            int sectorsPerCluster = clusterSize / bytesPerSector;
            String marker = (clusterSize == recommendedClusterSize) ? " <- RECOMMENDED" : "";
            System.out.println("  " + formatClusterSize(clusterSize) + 
                             " (" + sectorsPerCluster + " sectors per cluster)" + marker);
        }
    }
    
    /**
     * Main method for testing the disk image creation.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            System.out.println("JFAT Disk Image Creator - Enhanced with Configurable Cluster Sizes");
            System.out.println("=".repeat(70));
            
            // Demonstrate cluster size information
            displayClusterSizeInfo(128, FatType.FAT16, 512);
            displayClusterSizeInfo(1024, FatType.FAT32, 512);
            
            System.out.println("\n" + "=".repeat(70));
            System.out.println("Creating test images with recommended cluster sizes...\n");
            
            // Create a FAT16 disk image (128MB) with recommended cluster size
            String fat16Path = "fat16_recommended.img";
            createDiskImage(fat16Path, FatType.FAT16, 128);
            System.out.println("✓ Created FAT16 image: " + fat16Path + " (128MB, recommended cluster size)");
            
            // Create a FAT32 disk image (1GB) with recommended cluster size
            String fat32Path = "fat32_recommended.img";
            createDiskImage(fat32Path, FatType.FAT32, 1024);
            System.out.println("✓ Created FAT32 image: " + fat32Path + " (1GB, recommended cluster size)");
            
            // Create a FAT32 disk image with custom cluster size (32KB clusters)
            String fat32CustomPath = "fat32_custom_32k.img";
            createDiskImage(fat32CustomPath, FatType.FAT32, 1024, 512, 64); // 64 sectors = 32KB clusters
            System.out.println("✓ Created FAT32 image: " + fat32CustomPath + " (1GB, 32KB clusters)");
            
            // Create a small FAT12 disk image
            String fat12Path = "fat12_small.img";
            createDiskImage(fat12Path, FatType.FAT12, 2);
            System.out.println("✓ Created FAT12 image: " + fat12Path + " (2MB, recommended cluster size)");
            
            System.out.println("\nAll test images created successfully!");
            
        } catch (IOException e) {
            System.err.println("Error creating disk image: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 