package net.seitter.jfat.examples;

import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.io.DeviceAccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Example demonstrating the usage of the JFAT library.
 */
public class FatExample {
    
    /**
     * Main method demonstrating usage of the JFAT library.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: FatExample <device_or_image_path>");
            System.exit(1);
        }
        
        String devicePath = args[0];
        
        try {
            // Open the device or image file
            DeviceAccess device = new DeviceAccess(devicePath);
            
            // Mount the FAT filesystem
            try (FatFileSystem fs = FatFileSystem.mount(device)) {
                System.out.println("FAT filesystem mounted successfully.");
                System.out.println("FAT Type: " + fs.getBootSector().getFatType());
                System.out.println("Bytes per sector: " + fs.getBootSector().getBytesPerSector());
                System.out.println("Sectors per cluster: " + fs.getBootSector().getSectorsPerCluster());
                System.out.println("Cluster size: " + fs.getBootSector().getClusterSizeBytes() + " bytes");
                
                // List the root directory
                System.out.println("\nRoot directory contents:");
                listDirectory(fs.getRootDirectory());
                
                // Create a new directory
                System.out.println("\nCreating a new directory 'jfat_test'...");
                FatDirectory newDir = fs.getRootDirectory().createDirectory("jfat_test");
                
                // Create a file in the new directory
                System.out.println("Creating a new file 'hello.txt'...");
                FatFile newFile = newDir.createFile("hello.txt");
                
                // Write data to the file
                String content = "Hello, FAT filesystem! This file was created using JFAT.";
                System.out.println("Writing to the file...");
                newFile.write(content.getBytes(StandardCharsets.UTF_8));
                
                // Read the file back
                System.out.println("Reading the file back...");
                byte[] readData = newFile.readAllBytes();
                System.out.println("File content: " + new String(readData, StandardCharsets.UTF_8));
                
                // List the new directory
                System.out.println("\nContents of the new directory:");
                listDirectory(newDir);
                
                // Create another file
                System.out.println("\nCreating another file 'data.bin'...");
                FatFile dataFile = newDir.createFile("data.bin");
                
                // Write binary data
                byte[] binaryData = new byte[1024];
                for (int i = 0; i < binaryData.length; i++) {
                    binaryData[i] = (byte) (i % 256);
                }
                dataFile.write(binaryData);
                
                // List the directory again
                System.out.println("\nContents of the directory after creating the second file:");
                listDirectory(newDir);
                
                // Delete a file
                System.out.println("\nDeleting 'hello.txt'...");
                newFile.delete();
                
                // List the directory after deletion
                System.out.println("\nContents of the directory after deletion:");
                listDirectory(newDir);
                
                System.out.println("\nExample completed successfully.");
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Lists the contents of a directory with details.
     *
     * @param directory The directory to list
     * @throws IOException If the directory cannot be read
     */
    private static void listDirectory(FatDirectory directory) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<FatEntry> entries = directory.list();
        
        if (entries.isEmpty()) {
            System.out.println("  <empty directory>");
            return;
        }
        
        System.out.println("  Name                  Size      Modified             Attributes");
        System.out.println("  --------------------  --------  -------------------  ----------");
        
        for (FatEntry entry : entries) {
            StringBuilder attributes = new StringBuilder();
            if (entry.isDirectory()) attributes.append('D');
            if (entry.isReadOnly()) attributes.append('R');
            if (entry.isHidden()) attributes.append('H');
            if (entry.isSystem()) attributes.append('S');
            if (entry.isArchive()) attributes.append('A');
            
            System.out.printf("  %-20s  %-8d  %-19s  %s%n",
                    entry.getName(),
                    entry.getSize(),
                    dateFormat.format(entry.getModifyTime()),
                    attributes.toString());
        }
    }
} 