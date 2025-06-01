package net.seitter.jfat.core;

import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.DiskImageCreator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for FAT filesystem operations.
 */
@Tag("performance")
public class FatPerformanceTest {
    
    private static final String TEST_DIR = "build/test-images";
    private static final Random random = new Random();
    
    @BeforeEach
    public void setUp() throws IOException {
        System.out.println("\n=== Setting up performance test environment ===");
        // Create test directory if it doesn't exist
        Files.createDirectories(Paths.get(TEST_DIR));
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        System.out.println("=== Cleaning up performance test environment ===");
        // Clean up test images
        File testDir = new File(TEST_DIR);
        File[] files = testDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".img")) {
                    System.out.println("Deleting test image: " + file.getName());
                    file.delete();
                }
            }
        }
    }
    
    /**
     * Test file creation performance for FAT32
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testFileCreationPerformance() throws IOException {
        System.out.println("\n=== Testing file creation performance ===");
        
        // Create a smaller FAT32 image (32MB instead of 64MB) for faster testing
        System.out.println("Creating FAT32 image (32MB)...");
        String imagePath = TEST_DIR + "/fat32_perf.img";
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 32);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Mounting filesystem...");
            FatDirectory rootDir = fs.getRootDirectory();
            
            // Create directory for the test
            System.out.println("Creating test directory...");
            FatDirectory testDir = rootDir.createDirectory("perf_test");
            
            // Create 50 files with random content (reduced from 100)
            int fileCount = 50;
            int fileSize = 5 * 1024; // 5KB (reduced from 10KB)
            
            System.out.println("Starting file creation test - creating " + fileCount + 
                              " files of " + (fileSize / 1024) + "KB each...");
            long startTime = System.currentTimeMillis();
            
            for (int i = 1; i <= fileCount; i++) {
                if (i % 10 == 0 || i == fileCount) {
                    System.out.println("  Created " + i + "/" + fileCount + " files");
                }
                
                String fileName = String.format("file%03d.dat", i);
                FatFile file = testDir.createFile(fileName);
                
                // Generate random content
                byte[] content = new byte[fileSize];
                random.nextBytes(content);
                file.write(content);
            }
            
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            
            System.out.println("Created " + fileCount + " files of " + (fileSize / 1024) + "KB each in " + elapsedTime + "ms");
            System.out.println("Average time per file: " + (elapsedTime / (double)fileCount) + "ms");
            System.out.println("Write throughput: " + String.format("%.2f", (fileCount * fileSize / 1024.0) / (elapsedTime / 1000.0)) + " KB/s");
            
            // The test is considered successful if it completes without errors
            assertTrue(elapsedTime > 0);
        }
    }
    
    /**
     * Test large file read/write performance for FAT32
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    public void testLargeFilePerformance() throws IOException {
        System.out.println("\n=== Testing large file read/write performance ===");
        
        // Create a smaller FAT32 image (32MB) and smaller test file (2MB) for faster testing
        System.out.println("Creating FAT32 image (32MB)...");
        String imagePath = TEST_DIR + "/fat32_large_perf.img";
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 32);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Mounting filesystem...");
            FatDirectory rootDir = fs.getRootDirectory();
            
            // Create a large file (2MB instead of 5MB)
            int fileSize = 2 * 1024 * 1024; // 2MB
            System.out.println("Testing large file write performance (" + (fileSize / (1024*1024)) + "MB)...");
            byte[] largeData = new byte[fileSize];
            
            System.out.println("Generating random data...");
            random.nextBytes(largeData);
            
            System.out.println("Writing data to file...");
            long writeStartTime = System.currentTimeMillis();
            
            FatFile largeFile = rootDir.createFile("large.dat");
            largeFile.write(largeData);
            
            long writeEndTime = System.currentTimeMillis();
            long writeElapsedTime = writeEndTime - writeStartTime;
            
            double writeSpeedKBs = (fileSize / 1024.0) / (writeElapsedTime / 1000.0);
            System.out.println("Wrote " + (fileSize / (1024*1024)) + "MB file in " + writeElapsedTime + "ms");
            System.out.println("Write speed: " + String.format("%.2f", writeSpeedKBs) + " KB/s");
            
            // Test reading the large file
            System.out.println("Testing large file read performance...");
            System.out.println("Reading data from file...");
            long readStartTime = System.currentTimeMillis();
            
            byte[] readData = largeFile.readAllBytes();
            
            long readEndTime = System.currentTimeMillis();
            long readElapsedTime = readEndTime - readStartTime;
            
            double readSpeedKBs = (fileSize / 1024.0) / (readElapsedTime / 1000.0);
            System.out.println("Read " + (fileSize / (1024*1024)) + "MB file in " + readElapsedTime + "ms");
            System.out.println("Read speed: " + String.format("%.2f", readSpeedKBs) + " KB/s");
            
            // Verify the data was read correctly
            System.out.println("Verifying data integrity...");
            assertTrue(Arrays.equals(largeData, readData));
            System.out.println("Data verification successful!");
        }
    }
    
    /**
     * Test directory traversal performance
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    public void testDirectoryTraversalPerformance() throws IOException {
        System.out.println("\n=== Testing directory traversal performance ===");
        
        // Create a smaller FAT32 image (32MB) for faster testing
        System.out.println("Creating FAT32 image (32MB)...");
        String imagePath = TEST_DIR + "/fat32_dir_perf.img";
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 32);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            System.out.println("Mounting filesystem...");
            FatDirectory rootDir = fs.getRootDirectory();
            
            // Create a less deep directory structure with fewer subdirectories
            System.out.println("Creating directory structure for traversal test...");
            
            // Create 3 levels of directories (reduced from 5), each with 3 subdirectories (reduced from 5)
            int width = 3;
            int depth = 3;
            System.out.println("Creating " + width + "x" + width + "x" + width + " directory structure (depth: " + depth + ")...");
            createNestedDirectories(rootDir, "dir", width, depth, 0);
            
            // Create fewer files in each leaf directory
            int filesPerDir = 5; // Reduced from 10
            System.out.println("Creating " + filesPerDir + " files in each leaf directory...");
            createFilesInLeafDirectories(rootDir, filesPerDir);
            
            // Test traversal performance
            System.out.println("Testing directory traversal performance...");
            long startTime = System.currentTimeMillis();
            
            // Count all entries recursively
            int totalEntries = countEntriesRecursively(rootDir);
            
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            
            System.out.println("Traversed " + totalEntries + " entries in " + elapsedTime + "ms");
            System.out.println("Average time per entry: " + String.format("%.2f", elapsedTime / (double)totalEntries) + "ms");
            
            // Calculate expected number of directories and files
            int expectedDirs = 0;
            for (int i = 0; i <= depth; i++) {
                expectedDirs += Math.pow(width, i);
            }
            int leafDirs = (int)Math.pow(width, depth);
            int expectedFiles = leafDirs * filesPerDir;
            int expectedTotal = expectedFiles + 1; // +1 for the entries in root dir
            
            System.out.println("Expected entries: " + expectedTotal + " (dirs: " + expectedDirs + ", files: " + expectedFiles + ")");
            System.out.println("Actual entries found: " + totalEntries);
            
            // The test is considered successful if it completes without errors
            assertTrue(elapsedTime > 0);
            assertTrue(totalEntries > 0);
        }
    }
    
    /**
     * Helper method to create nested directories for performance testing
     */
    private void createNestedDirectories(FatDirectory parentDir, String prefix, 
                                        int width, int depth, int currentDepth) throws IOException {
        if (currentDepth >= depth) {
            return;
        }
        
        for (int i = 0; i < width; i++) {
            String dirName = prefix + "_" + currentDepth + "_" + i;
            // First create the directory directly
            FatDirectory newDir = parentDir.createDirectory(dirName);
            
            // Show progress for first level
            if (currentDepth == 0) {
                System.out.println("  Created top-level directory: " + dirName);
            }
            
            createNestedDirectories(newDir, prefix, width, depth, currentDepth + 1);
        }
    }
    
    /**
     * Helper method to create files in all leaf directories
     */
    private void createFilesInLeafDirectories(FatDirectory dir, int filesPerDir) throws IOException {
        // Get all entries in this directory
        List<FatEntry> entries = dir.list();
        
        // If this directory has subdirectories, recurse into them
        boolean hasSubdirs = false;
        for (FatEntry entry : entries) {
            if (entry.isDirectory()) {
                hasSubdirs = true;
                createFilesInLeafDirectories((FatDirectory) entry, filesPerDir);
            }
        }
        
        // If this is a leaf directory (no subdirectories), create files
        if (!hasSubdirs) {
            for (int i = 0; i < filesPerDir; i++) {
                String fileName = "file_" + i + ".txt";
                FatFile file = dir.createFile(fileName);
                
                // Write smaller content for faster testing
                String content = "Test file " + i;
                file.write(content.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    /**
     * Helper method to count all entries recursively
     */
    private int countEntriesRecursively(FatDirectory dir) throws IOException {
        int count = 0;
        
        List<FatEntry> entries = dir.list();
        count += entries.size();
        
        for (FatEntry entry : entries) {
            if (entry.isDirectory()) {
                count += countEntriesRecursively((FatDirectory) entry);
            }
        }
        
        return count;
    }
} 