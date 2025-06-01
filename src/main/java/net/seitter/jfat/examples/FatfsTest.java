package net.seitter.jfat.examples;

import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.io.DeviceAccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A simple standalone test for the FAT filesystem using an existing fatfs file.
 */
public class FatfsTest {
    
    /**
     * Main method for testing the FAT filesystem with an existing fatfs file.
     *
     * @param args Command line arguments (fatfs file path)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: FatfsTest <fatfs_file_path>");
            System.exit(1);
        }
        
        String fatfsPath = args[0];
        System.out.println("Testing FAT filesystem with file: " + fatfsPath);
        
        try {
            // Open the fatfs file
            System.out.println("\n=== Mounting FAT filesystem ===");
            DeviceAccess device = new DeviceAccess(fatfsPath);
            
            // Mount the FAT filesystem
            try (FatFileSystem fs = FatFileSystem.mount(device)) {
                System.out.println("FAT filesystem mounted successfully.");
                System.out.println("FAT Type: " + fs.getBootSector().getFatType());
                System.out.println("Bytes per sector: " + fs.getBootSector().getBytesPerSector());
                System.out.println("Sectors per cluster: " + fs.getBootSector().getSectorsPerCluster());
                System.out.println("Cluster size: " + fs.getBootSector().getClusterSizeBytes() + " bytes");
                
                // List the root directory
                System.out.println("\n=== Root directory contents ===");
                listDirectory(fs.getRootDirectory(), "");
                
                // Run tests
                runTests(fs);
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Run a series of tests on the FAT filesystem.
     *
     * @param fs The mounted FAT filesystem
     * @throws IOException If an I/O error occurs
     */
    private static void runTests(FatFileSystem fs) throws IOException {
        System.out.println("\n=== Running FAT filesystem tests ===");
        
        // Test 1: Create a new directory
        System.out.println("\nTest 1: Creating directory 'test_dir'...");
        FatDirectory rootDir = fs.getRootDirectory();
        FatDirectory testDir = rootDir.createDirectory("test_dir");
        System.out.println("Directory created successfully.");
        
        // Test 2: Create a file in the directory
        System.out.println("\nTest 2: Creating file 'test.txt' in 'test_dir'...");
        FatFile testFile = testDir.createFile("test.txt");
        String content = "This is a test file created by FatfsTest.";
        testFile.write(content.getBytes(StandardCharsets.UTF_8));
        System.out.println("File created and written successfully.");
        
        // Test 3: Read the file back
        System.out.println("\nTest 3: Reading 'test.txt'...");
        byte[] readData = testFile.readAllBytes();
        String readContent = new String(readData, StandardCharsets.UTF_8);
        System.out.println("File content: " + readContent);
        
        if (content.equals(readContent)) {
            System.out.println("Content verification successful!");
        } else {
            System.out.println("Error: Content verification failed!");
        }
        
        // Test 4: Create multiple files
        System.out.println("\nTest 4: Creating multiple files...");
        for (int i = 1; i <= 5; i++) {
            String fileName = String.format("file%d.txt", i);
            System.out.println("  Creating " + fileName + "...");
            FatFile file = testDir.createFile(fileName);
            String fileContent = "This is file " + i + " content.";
            file.write(fileContent.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("Multiple files created successfully.");
        
        // Test 5: List directory contents
        System.out.println("\nTest 5: Listing 'test_dir' contents...");
        listDirectory(testDir, "  ");
        
        // Test 6: Append to a file
        System.out.println("\nTest 6: Appending to 'test.txt'...");
        String appendContent = "\nThis is appended content.";
        testFile.append(appendContent.getBytes(StandardCharsets.UTF_8));
        readData = testFile.readAllBytes();
        readContent = new String(readData, StandardCharsets.UTF_8);
        System.out.println("Updated file content: " + readContent);
        
        // Test 7: Delete some files
        System.out.println("\nTest 7: Deleting files...");
        System.out.println("  Deleting 'file1.txt'...");
        FatEntry file1 = testDir.getEntry("file1.txt");
        file1.delete();
        System.out.println("  Deleting 'file3.txt'...");
        FatEntry file3 = testDir.getEntry("file3.txt");
        file3.delete();
        
        // Test 8: Verify deletion
        System.out.println("\nTest 8: Verifying deletion...");
        listDirectory(testDir, "  ");
        
        // Test 9: Create a subdirectory
        System.out.println("\nTest 9: Creating subdirectory 'subdir'...");
        FatDirectory subDir = testDir.createDirectory("subdir");
        
        // Test 10: Create a file in the subdirectory
        System.out.println("\nTest 10: Creating file in subdirectory...");
        FatFile subFile = subDir.createFile("subfile.txt");
        subFile.write("This is a file in the subdirectory.".getBytes(StandardCharsets.UTF_8));
        
        // Test 11: Access file through path
        System.out.println("\nTest 11: Accessing file through path...");
        FatFile pathFile = fs.getFile("/test_dir/subdir/subfile.txt");
        readData = pathFile.readAllBytes();
        readContent = new String(readData, StandardCharsets.UTF_8);
        System.out.println("File content: " + readContent);
        
        // Final: Show the directory structure
        System.out.println("\n=== Final directory structure ===");
        listDirectory(rootDir, "");
        
        System.out.println("\n=== Tests completed successfully! ===");
    }
    
    /**
     * Lists the contents of a directory with details.
     *
     * @param directory The directory to list
     * @param indent    Indentation for the output
     * @throws IOException If the directory cannot be read
     */
    private static void listDirectory(FatDirectory directory, String indent) throws IOException {
        List<FatEntry> entries = directory.list();
        
        if (entries.isEmpty()) {
            System.out.println(indent + "<empty directory>");
            return;
        }
        
        for (FatEntry entry : entries) {
            String entryType = entry.isDirectory() ? "D" : "F";
            System.out.printf("%s%s %-20s %8d bytes%n",
                    indent, entryType, entry.getName(), entry.getSize());
            
            // Recursively list subdirectories
            if (entry.isDirectory()) {
                listDirectory((FatDirectory) entry, indent + "  ");
            }
        }
    }
} 