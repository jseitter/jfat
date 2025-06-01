package net.seitter.jfat.core;

import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.DiskImageCreator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for FAT type detection.
 */
public class FatTypeDetectionTest {

    private static final String TEST_DIR = "build/test-images";
    
    @BeforeEach
    public void setUp() throws IOException {
        Files.createDirectories(Paths.get(TEST_DIR));
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        File testDir = new File(TEST_DIR);
        File[] files = testDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".img")) {
                    file.delete();
                }
            }
        }
    }
    
    @Test
    public void testFat32Detection() throws IOException {
        System.out.println("=== Testing FAT32 Type Detection ===");
        
        String imagePath = TEST_DIR + "/fat32_detection_test.img";
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 32);
        
        try (DeviceAccess device = new DeviceAccess(imagePath)) {
            BootSector bootSector = new BootSector(device);
            
            System.out.println("Expected: FAT32");
            System.out.println("Detected: " + bootSector.getFatType());
            System.out.println("Total sectors: " + bootSector.getTotalSectors());
            System.out.println("Sectors per FAT: " + bootSector.getSectorsPerFat());
            System.out.println("Root entry count: " + bootSector.getRootEntryCount());
            System.out.println("Sectors per cluster: " + bootSector.getSectorsPerCluster());
            System.out.println("Reserved sectors: " + bootSector.getReservedSectorCount());
            System.out.println("Number of FATs: " + bootSector.getNumberOfFats());
            
            assertEquals(FatType.FAT32, bootSector.getFatType());
        }
    }
    
    @Test
    public void testFat16Detection() throws IOException {
        System.out.println("=== Testing FAT16 Type Detection ===");
        
        String imagePath = TEST_DIR + "/fat16_detection_test.img";
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT16, 16);
        
        try (DeviceAccess device = new DeviceAccess(imagePath)) {
            BootSector bootSector = new BootSector(device);
            
            System.out.println("Expected: FAT16");
            System.out.println("Detected: " + bootSector.getFatType());
            
            assertEquals(FatType.FAT16, bootSector.getFatType());
        }
    }
    
    @Test
    public void testFat12Detection() throws IOException {
        System.out.println("=== Testing FAT12 Type Detection ===");
        
        String imagePath = TEST_DIR + "/fat12_detection_test.img";
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT12, 1);
        
        try (DeviceAccess device = new DeviceAccess(imagePath)) {
            BootSector bootSector = new BootSector(device);
            
            System.out.println("Expected: FAT12");
            System.out.println("Detected: " + bootSector.getFatType());
            
            assertEquals(FatType.FAT12, bootSector.getFatType());
        }
    }
} 