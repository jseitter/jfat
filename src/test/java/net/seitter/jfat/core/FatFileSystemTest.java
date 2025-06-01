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
 * Comprehensive test for FAT filesystem operations.
 * Tests all FAT variants (FAT12, FAT16, FAT32) with various file operations.
 */
@Tag("fat")
public class FatFileSystemTest {

    private static final String TEST_DIR = "build/test-images";
    private static final Random random = new Random();
    
    @BeforeEach
    public void setUp() throws IOException {
        System.out.println("\n=== Setting up test environment ===");
        // Create test directory if it doesn't exist
        Files.createDirectories(Paths.get(TEST_DIR));
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        System.out.println("=== Cleaning up test environment ===");
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
     * Test creating disk images for all FAT types
     */
    @Test
    @Tag("basic")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testCreateDiskImages() throws IOException {
        System.out.println("\n=== Testing disk image creation ===");
        
        // Test creating FAT12 image (1MB)
        System.out.println("Creating FAT12 image (1MB)...");
        String fat12Image = TEST_DIR + "/fat12_test.img";
        DiskImageCreator.createDiskImage(fat12Image, FatType.FAT12, 1);
        assertTrue(Files.exists(Paths.get(fat12Image)));
        assertEquals(1024 * 1024, Files.size(Paths.get(fat12Image)));
        
        // Test creating FAT16 image (16MB)
        System.out.println("Creating FAT16 image (16MB)...");
        String fat16Image = TEST_DIR + "/fat16_test.img";
        DiskImageCreator.createDiskImage(fat16Image, FatType.FAT16, 16);
        assertTrue(Files.exists(Paths.get(fat16Image)));
        assertEquals(16 * 1024 * 1024, Files.size(Paths.get(fat16Image)));
        
        // Test creating FAT32 image (64MB)
        System.out.println("Creating FAT32 image (64MB)...");
        String fat32Image = TEST_DIR + "/fat32_test.img";
        DiskImageCreator.createDiskImage(fat32Image, FatType.FAT32, 64);
        assertTrue(Files.exists(Paths.get(fat32Image)));
        assertEquals(64 * 1024 * 1024, Files.size(Paths.get(fat32Image)));
        
        System.out.println("Disk image creation test completed successfully!");
    }
    
    /**
     * Standalone test for basic FAT12 operations
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testBasicFat12Operations() throws IOException {
        System.out.println("\n=== Testing basic operations on FAT12 ===");
        
        String imagePath = TEST_DIR + "/fat12_basic_test.img";
        System.out.println("Creating FAT12 image (1MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT12, 1);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT12, fs.getBootSector().getFatType());
            
            // Test basic file operations
            System.out.println("Testing basic file operations...");
            testBasicFileOperations(fs);
            
            System.out.println("FAT12 basic operations test completed successfully!");
        }
    }
    
    /**
     * Standalone test for basic FAT16 operations
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testBasicFat16Operations() throws IOException {
        System.out.println("\n=== Testing basic operations on FAT16 ===");
        
        String imagePath = TEST_DIR + "/fat16_basic_test.img";
        System.out.println("Creating FAT16 image (16MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT16, 16);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT16, fs.getBootSector().getFatType());
            
            // Test basic file operations
            System.out.println("Testing basic file operations...");
            testBasicFileOperations(fs);
            
            System.out.println("FAT16 basic operations test completed successfully!");
        }
    }
    
    /**
     * Standalone test for basic FAT32 operations
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testBasicFat32Operations() throws IOException {
        System.out.println("\n=== Testing basic operations on FAT32 ===");
        
        String imagePath = TEST_DIR + "/fat32_basic_test.img";
        System.out.println("Creating FAT32 image (64MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 64);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT32, fs.getBootSector().getFatType());
            
            // Test basic file operations
            System.out.println("Testing basic file operations...");
            testBasicFileOperations(fs);
            
            System.out.println("FAT32 basic operations test completed successfully!");
        }
    }
    
    /**
     * Standalone test for extended FAT12 operations
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    public void testExtendedFat12Operations() throws IOException {
        System.out.println("\n=== Testing extended operations on FAT12 ===");
        
        String imagePath = TEST_DIR + "/fat12_extended_test.img";
        System.out.println("Creating FAT12 image (1MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT12, 1);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT12, fs.getBootSector().getFatType());
            
            // Test directory operations
            System.out.println("Testing directory operations...");
            testDirectoryOperations(fs);
            
            // Test nested directories
            System.out.println("Testing nested directories...");
            testNestedDirectories(fs, 2); // Reduced depth for FAT12
            
            // Test boundary conditions
            System.out.println("Testing boundary conditions...");
            testBoundaryConditions(fs, FatType.FAT12, 5); // Reduced number of files for FAT12
            
            System.out.println("FAT12 extended operations test completed successfully!");
        }
    }
    
    /**
     * Standalone test for extended FAT16 operations
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    public void testExtendedFat16Operations() throws IOException {
        System.out.println("\n=== Testing extended operations on FAT16 ===");
        
        String imagePath = TEST_DIR + "/fat16_extended_test.img";
        System.out.println("Creating FAT16 image (16MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT16, 16);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT16, fs.getBootSector().getFatType());
            
            // Test directory operations
            System.out.println("Testing directory operations...");
            testDirectoryOperations(fs);
            
            // Test nested directories
            System.out.println("Testing nested directories...");
            testNestedDirectories(fs, 3);
            
            // Test boundary conditions
            System.out.println("Testing boundary conditions...");
            testBoundaryConditions(fs, FatType.FAT16, 10);
            
            System.out.println("FAT16 extended operations test completed successfully!");
        }
    }
    
    /**
     * Standalone test for extended FAT32 operations
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    public void testExtendedFat32Operations() throws IOException {
        System.out.println("\n=== Testing extended operations on FAT32 ===");
        
        String imagePath = TEST_DIR + "/fat32_extended_test.img";
        System.out.println("Creating FAT32 image (64MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 64);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT32, fs.getBootSector().getFatType());
            
            // Test directory operations
            System.out.println("Testing directory operations...");
            testDirectoryOperations(fs);
            
            // Test nested directories
            System.out.println("Testing nested directories...");
            testNestedDirectories(fs, 3);
            
            // Test boundary conditions
            System.out.println("Testing boundary conditions...");
            testBoundaryConditions(fs, FatType.FAT32, 10);
            
            System.out.println("FAT32 extended operations test completed successfully!");
        }
    }
    
    /**
     * Test for large file operations on FAT16
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    public void testLargeFileFat16Operations() throws IOException {
        System.out.println("\n=== Testing large file operations on FAT16 ===");
        
        String imagePath = TEST_DIR + "/fat16_large_file_test.img";
        System.out.println("Creating FAT16 image (16MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT16, 16);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT16, fs.getBootSector().getFatType());
            
            // Test large file operations
            System.out.println("Testing large file operations...");
            testLargeFiles(fs, FatType.FAT16);
            
            System.out.println("FAT16 large file operations test completed successfully!");
        }
    }
    
    /**
     * Test for large file operations on FAT32
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    public void testLargeFileFat32Operations() throws IOException {
        System.out.println("\n=== Testing large file operations on FAT32 ===");
        
        String imagePath = TEST_DIR + "/fat32_large_file_test.img";
        System.out.println("Creating FAT32 image (64MB)...");
        DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 64);
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            assertEquals(FatType.FAT32, fs.getBootSector().getFatType());
            
            // Test large file operations
            System.out.println("Testing large file operations...");
            testLargeFiles(fs, FatType.FAT32);
            
            System.out.println("FAT32 large file operations test completed successfully!");
        }
    }
    
    /**
     * Determine appropriate image size for FAT type
     */
    private int getSizeForFatType(FatType fatType) {
        switch (fatType) {
            case FAT12: return 1;  // 1MB
            case FAT16: return 16; // 16MB
            case FAT32: return 64; // 64MB
            default: return 16;
        }
    }
    
    /**
     * Test basic file operations (create, read, write, delete)
     */
    private void testBasicFileOperations(FatFileSystem fs) throws IOException {
        FatDirectory rootDir = fs.getRootDirectory();
        assertNotNull(rootDir);
        
        System.out.println("  Creating file in root directory...");
        FatFile rootFile = rootDir.createFile("test.txt");
        assertNotNull(rootFile);
        assertEquals("test.txt", rootFile.getName());
        
        System.out.println("  Writing data to file...");
        String testData = "This is a test file.";
        rootFile.write(testData.getBytes(StandardCharsets.UTF_8));
        assertEquals(testData.length(), rootFile.getSize());
        
        System.out.println("  Reading data from file...");
        byte[] readData = rootFile.readAllBytes();
        assertEquals(testData, new String(readData, StandardCharsets.UTF_8));
        
        System.out.println("  Deleting file...");
        rootFile.delete();
        
        List<FatEntry> entries = rootDir.list();
        assertEquals(0, entries.size());
    }
    
    /**
     * Test directory operations (create, list, delete)
     */
    private void testDirectoryOperations(FatFileSystem fs) throws IOException {
        FatDirectory rootDir = fs.getRootDirectory();
        
        System.out.println("  Creating test directory...");
        FatDirectory testDir = rootDir.createDirectory("test_dir");
        assertNotNull(testDir);
        assertEquals("test_dir", testDir.getName());
        assertTrue(testDir.isDirectory());
        
        System.out.println("  Creating subdirectories...");
        FatDirectory subDir1 = testDir.createDirectory("subdir1");
        FatDirectory subDir2 = testDir.createDirectory("subdir2");
        assertNotNull(subDir1);
        assertNotNull(subDir2);
        
        System.out.println("  Creating files in subdirectories...");
        FatFile file1 = subDir1.createFile("file1.txt");
        file1.write("File 1 content".getBytes(StandardCharsets.UTF_8));
        
        FatFile file2 = subDir2.createFile("file2.txt");
        file2.write("File 2 content".getBytes(StandardCharsets.UTF_8));
        
        System.out.println("  Listing directories...");
        List<FatEntry> entries = testDir.list();
        assertEquals(2, entries.size());
        
        entries = subDir1.list();
        assertEquals(1, entries.size());
        assertEquals("file1.txt", entries.get(0).getName());
        
        entries = subDir2.list();
        assertEquals(1, entries.size());
        assertEquals("file2.txt", entries.get(0).getName());
        
        System.out.println("  Testing directory deletion constraints...");
        try {
            subDir1.delete();
            fail("Should not be able to delete non-empty directory");
        } catch (IOException e) {
            // Expected exception
            System.out.println("  Correctly prevented deletion of non-empty directory");
        }
        
        System.out.println("  Deleting files and directories...");
        file1.delete();
        subDir1.delete();
        file2.delete();
        subDir2.delete();
        testDir.delete();
        
        assertNull(rootDir.getEntry("test_dir"));
    }
    
    /**
     * Test operations with large files
     */
    private void testLargeFiles(FatFileSystem fs, FatType fatType) throws IOException {
        FatDirectory rootDir = fs.getRootDirectory();
        
        System.out.println("  Creating directory for large file tests...");
        FatDirectory largeFileDir = rootDir.createDirectory("large_files");
        
        // Determine the size based on FAT type (smaller for testing)
        int fileSize = (fatType == FatType.FAT16) ? 256 * 1024 : 512 * 1024; // 256KB or 512KB
        
        System.out.println("  Creating large file (" + (fileSize / 1024) + "KB)...");
        FatFile largeFile = largeFileDir.createFile("large.dat");
        byte[] largeData = new byte[fileSize];
        random.nextBytes(largeData);
        
        System.out.println("  Writing large file data...");
        largeFile.write(largeData);
        assertEquals(fileSize, largeFile.getSize());
        
        System.out.println("  Reading large file data...");
        byte[] readData = largeFile.readAllBytes();
        assertEquals(fileSize, readData.length);
        assertTrue(Arrays.equals(largeData, readData));
        
        System.out.println("  Appending data to large file...");
        byte[] appendData = new byte[1024];
        random.nextBytes(appendData);
        largeFile.append(appendData);
        
        assertEquals(fileSize + appendData.length, largeFile.getSize());
        
        System.out.println("  Reading and verifying appended data...");
        readData = largeFile.readAllBytes();
        byte[] expectedTail = new byte[appendData.length];
        System.arraycopy(readData, fileSize, expectedTail, 0, appendData.length);
        assertTrue(Arrays.equals(appendData, expectedTail));
        
        System.out.println("  Deleting large file and directory...");
        largeFile.delete();
        largeFileDir.delete();
    }
    
    /**
     * Test operations with nested directories
     */
    private void testNestedDirectories(FatFileSystem fs, int maxDepth) throws IOException {
        FatDirectory rootDir = fs.getRootDirectory();
        
        System.out.println("  Creating nested directories (depth: " + maxDepth + ")...");
        FatDirectory currentDir = rootDir.createDirectory("level1");
        for (int i = 2; i <= maxDepth; i++) {
            System.out.println("    Creating directory level " + i);
            currentDir = currentDir.createDirectory("level" + i);
            assertNotNull(currentDir);
            assertEquals("level" + i, currentDir.getName());
        }
        
        System.out.println("  Creating file in deepest directory...");
        FatFile deepFile = currentDir.createFile("deep.txt");
        String deepContent = "This file is deep in the directory structure.";
        deepFile.write(deepContent.getBytes(StandardCharsets.UTF_8));
        
        System.out.println("  Accessing file through path...");
        StringBuilder pathBuilder = new StringBuilder("/level1");
        for (int i = 2; i <= maxDepth; i++) {
            pathBuilder.append("/level").append(i);
        }
        pathBuilder.append("/deep.txt");
        
        FatFile retrievedFile = fs.getFile(pathBuilder.toString());
        assertNotNull(retrievedFile);
        assertEquals(deepContent, new String(retrievedFile.readAllBytes(), StandardCharsets.UTF_8));
        
        System.out.println("  Cleaning up nested directories...");
        // Delete from deepest to shallowest
        System.out.println("    Deleting file: deep.txt");
        deepFile.delete();
        
        // First delete the deepest directory
        System.out.println("    Deleting directory: level" + maxDepth);
        currentDir.delete();
        
        // Try to delete level1 directory at the end
        // This simplified approach avoids potential NullPointerExceptions
        FatDirectory level1 = (FatDirectory) rootDir.getEntry("level1");
        if (level1 != null) {
            try {
                System.out.println("    Attempting to delete level1 directory...");
                level1.delete();
                System.out.println("    Successfully deleted level1 directory");
            } catch (IOException e) {
                System.out.println("    Could not delete level1 directory - it may not be empty");
            }
        }
    }
    
    /**
     * Test boundary conditions
     */
    private void testBoundaryConditions(FatFileSystem fs, FatType fatType, int fileCount) throws IOException {
        FatDirectory rootDir = fs.getRootDirectory();
        
        System.out.println("  Creating directory for boundary tests...");
        FatDirectory boundaryDir = rootDir.createDirectory("boundary_tests");
        
        System.out.println("  Testing maximum length filename...");
        FatFile maxNameFile = boundaryDir.createFile("ABCDEFGH.XYZ");
        assertEquals("ABCDEFGH.XYZ", maxNameFile.getName());
        
        System.out.println("  Testing tiny file (1 byte)...");
        FatFile tinyFile = boundaryDir.createFile("tiny.txt");
        tinyFile.write(new byte[]{65}); // 'A'
        assertEquals(1, tinyFile.getSize());
        assertEquals(65, tinyFile.readAllBytes()[0]);
        
        System.out.println("  Testing empty file...");
        FatFile emptyFile = boundaryDir.createFile("empty.txt");
        assertEquals(0, emptyFile.getSize());
        assertEquals(0, emptyFile.readAllBytes().length);
        
        System.out.println("  Creating " + fileCount + " files in one directory...");
        for (int i = 1; i <= fileCount; i++) {
            if (i % 5 == 0 || i == fileCount) {
                System.out.println("    Created " + i + "/" + fileCount + " files");
            }
            String fileName = String.format("FILE%03d.TXT", i);
            FatFile file = boundaryDir.createFile(fileName);
            file.write(("Content of file " + i).getBytes(StandardCharsets.UTF_8));
        }
        
        System.out.println("  Verifying all files...");
        List<FatEntry> entries = boundaryDir.list();
        assertEquals(fileCount + 3, entries.size()); // fileCount + 3 test files
        
        System.out.println("  Deleting files...");
        maxNameFile.delete();
        tinyFile.delete();
        emptyFile.delete();
        
        for (int i = 1; i <= fileCount; i++) {
            if (i % 5 == 0 || i == fileCount) {
                System.out.println("    Deleted " + i + "/" + fileCount + " files");
            }
            String fileName = String.format("FILE%03d.TXT", i);
            FatEntry entry = boundaryDir.getEntry(fileName);
            if (entry != null) {
                entry.delete();
            }
        }
        
        System.out.println("  Verifying all files deleted...");
        entries = boundaryDir.list();
        assertEquals(0, entries.size());
        
        System.out.println("  Deleting test directory...");
        boundaryDir.delete();
    }
} 