package net.seitter.jfat.util;

import net.seitter.jfat.core.FatType;
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
     * Creates a FAT disk image with the given parameters.
     *
     * @param imagePath The path to the disk image file to create
     * @param fatType   The FAT type (FAT12, FAT16, or FAT32)
     * @param sizeMB    The size of the disk image in MB
     * @throws IOException If the disk image cannot be created
     */
    public static void createDiskImage(String imagePath, FatType fatType, int sizeMB) throws IOException {
        // Calculate the total number of sectors
        long totalSectors = (long) sizeMB * 1024 * 1024 / DEFAULT_SECTOR_SIZE;
        
        // Create a new disk image file
        try (RandomAccessFile file = new RandomAccessFile(imagePath, "rw")) {
            // Set the file size
            file.setLength(totalSectors * DEFAULT_SECTOR_SIZE);
            
            // Write the boot sector
            writeBootSector(file, fatType, totalSectors);
            
            // Write the FATs
            int sectorsPerFat = calculateSectorsPerFat(fatType, totalSectors);
            writeFATs(file, fatType, sectorsPerFat);
            
            // Initialize the root directory
            writeRootDirectory(file, fatType);
        }
    }
    
    /**
     * Calculates the number of sectors per FAT based on the FAT type and total sectors.
     *
     * @param fatType      The FAT type
     * @param totalSectors The total number of sectors
     * @return The number of sectors per FAT
     */
    private static int calculateSectorsPerFat(FatType fatType, long totalSectors) {
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
                          (DEFAULT_ROOT_ENTRIES * 32 / DEFAULT_SECTOR_SIZE); // Root directory size
        long clusters = dataSectors / DEFAULT_SECTORS_PER_CLUSTER;
        
        // Calculate sectors per FAT (rounded up)
        int sectorsPerFat = (int) ((clusters * bytesPerFatEntry + DEFAULT_SECTOR_SIZE - 1) / DEFAULT_SECTOR_SIZE);
        
        // For FAT32, ensure a minimum size
        if (fatType == FatType.FAT32 && sectorsPerFat < 64) {
            sectorsPerFat = 64;
        }
        
        return sectorsPerFat;
    }
    
    /**
     * Writes the boot sector to the disk image.
     *
     * @param file         The disk image file
     * @param fatType      The FAT type
     * @param totalSectors The total number of sectors
     * @throws IOException If the boot sector cannot be written
     */
    private static void writeBootSector(RandomAccessFile file, FatType fatType, long totalSectors) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(DEFAULT_SECTOR_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Jump instruction (EB 3C 90) and OEM name
        buffer.put((byte) 0xEB);
        buffer.put((byte) 0x3C);
        buffer.put((byte) 0x90);
        buffer.put("JFAT    ".getBytes()); // 8 bytes for OEM name
        
        // BIOS Parameter Block (BPB)
        buffer.putShort((short) DEFAULT_SECTOR_SIZE); // Bytes per sector
        buffer.put((byte) DEFAULT_SECTORS_PER_CLUSTER); // Sectors per cluster
        buffer.putShort((short) DEFAULT_RESERVED_SECTORS); // Reserved sectors
        buffer.put((byte) DEFAULT_NUMBER_OF_FATS); // Number of FATs
        
        // Root entries (0 for FAT32)
        buffer.putShort(fatType == FatType.FAT32 ? 0 : (short) DEFAULT_ROOT_ENTRIES);
        
        // Total sectors (16-bit value, 0 if more than 65535)
        buffer.putShort(totalSectors <= 65535 ? (short) totalSectors : 0);
        
        // Media descriptor (F8 for fixed disk)
        buffer.put((byte) 0xF8);
        
        // Sectors per FAT (0 for FAT32, calculated for FAT12/16)
        int sectorsPerFat = calculateSectorsPerFat(fatType, totalSectors);
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
        buffer.position(510);
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
     * @param file          The disk image file
     * @param fatType       The FAT type
     * @param sectorsPerFat The number of sectors per FAT
     * @throws IOException If the FATs cannot be written
     */
    private static void writeFATs(RandomAccessFile file, FatType fatType, int sectorsPerFat) throws IOException {
        int fatSize = sectorsPerFat * DEFAULT_SECTOR_SIZE;
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
            file.seek(DEFAULT_RESERVED_SECTORS * DEFAULT_SECTOR_SIZE + i * fatSize);
            file.write(fatData);
        }
    }
    
    /**
     * Writes an empty root directory to the disk image.
     *
     * @param file    The disk image file
     * @param fatType The FAT type
     * @throws IOException If the root directory cannot be written
     */
    private static void writeRootDirectory(RandomAccessFile file, FatType fatType) throws IOException {
        // For FAT32, the root directory is stored in clusters, so we don't need to initialize it here
        if (fatType == FatType.FAT32) {
            return;
        }
        
        // For FAT12/16, the root directory has a fixed location after the FATs
        int rootDirSize = DEFAULT_ROOT_ENTRIES * 32; // 32 bytes per entry
        byte[] rootDirData = new byte[rootDirSize];
        
        // Write the root directory
        int fatSize = calculateSectorsPerFat(fatType, file.length() / DEFAULT_SECTOR_SIZE) * DEFAULT_SECTOR_SIZE;
        file.seek(DEFAULT_RESERVED_SECTORS * DEFAULT_SECTOR_SIZE + DEFAULT_NUMBER_OF_FATS * fatSize);
        file.write(rootDirData);
    }
    
    /**
     * Main method for testing the disk image creation.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            // Create a FAT16 disk image (10MB)
            String imagePath = "fat16_test.img";
            createDiskImage(imagePath, FatType.FAT16, 10);
            System.out.println("Created FAT16 disk image: " + imagePath);
            
            // Create a FAT32 disk image (32MB)
            String fat32ImagePath = "fat32_test.img";
            createDiskImage(fat32ImagePath, FatType.FAT32, 32);
            System.out.println("Created FAT32 disk image: " + fat32ImagePath);
            
            // Create a FAT12 disk image (1MB)
            String fat12ImagePath = "fat12_test.img";
            createDiskImage(fat12ImagePath, FatType.FAT12, 1);
            System.out.println("Created FAT12 disk image: " + fat12ImagePath);
        } catch (IOException e) {
            System.err.println("Error creating disk image: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 