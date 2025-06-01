package net.seitter.jfat.core;

import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.DiskImageCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for Long Filename (LFN) support in JFAT.
 * Demonstrates creation, reading, and manipulation of files with long filenames.
 */
public class LfnSupportTest {
    
    private Path testImagePath;
    private FatFileSystem fileSystem;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary FAT32 image for testing
        testImagePath = Files.createTempFile("jfat_lfn_test", ".img");
        DiskImageCreator.createDiskImage(testImagePath.toString(), FatType.FAT32, 64);
        
        // Mount the filesystem
        DeviceAccess device = new DeviceAccess(testImagePath.toString());
        fileSystem = FatFileSystem.mount(device);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (fileSystem != null) {
            fileSystem.close();
        }
        if (testImagePath != null) {
            Files.deleteIfExists(testImagePath);
        }
    }
    
    @Test
    void testCreateFileWithLongFilename() throws IOException {
        FatDirectory root = fileSystem.getRootDirectory();
        
        // Create a file with a long filename
        String longFilename = "This is a very long filename that exceeds 8.3 format.txt";
        FatFile file = root.createFile(longFilename);
        
        assertNotNull(file);
        assertEquals(longFilename, file.getName());
        
        // Write some data
        String content = "Hello, Long Filename World!";
        file.write(content.getBytes());
        
        // Verify we can read it back
        byte[] readData = file.readAllBytes();
        assertEquals(content, new String(readData));
    }
    
    @Test
    void testCreateDirectoryWithLongFilename() throws IOException {
        FatDirectory root = fileSystem.getRootDirectory();
        
        // Create a directory with a long filename
        String longDirname = "Very Long Directory Name with Spaces and Special-Characters";
        FatDirectory dir = root.createDirectory(longDirname);
        
        assertNotNull(dir);
        assertEquals(longDirname, dir.getName());
        
        // Create a file inside the long-named directory
        FatFile file = dir.createFile("test.txt");
        file.write("Content in long-named directory".getBytes());
        
        // Verify structure
        List<FatEntry> entries = root.list();
        assertTrue(entries.stream().anyMatch(e -> e.getName().equals(longDirname)));
        
        List<FatEntry> dirEntries = dir.list();
        assertTrue(dirEntries.stream().anyMatch(e -> e.getName().equals("test.txt")));
    }
    
    @Test
    void testUnicodeFilenames() throws IOException {
        FatDirectory root = fileSystem.getRootDirectory();
        
        // Create files with Unicode characters
        String unicodeFilename = "Тест файл with émoji 🚀.txt";
        FatFile file = root.createFile(unicodeFilename);
        
        assertNotNull(file);
        assertEquals(unicodeFilename, file.getName());
        
        // Verify it appears in directory listing
        List<FatEntry> entries = root.list();
        assertTrue(entries.stream().anyMatch(e -> e.getName().equals(unicodeFilename)));
    }
    
    @Test
    void testMixedFilenames() throws IOException {
        FatDirectory root = fileSystem.getRootDirectory();
        
        // Create files with both short and long names
        root.createFile("SHORT.TXT");  // 8.3 format
        root.createFile("medium_length_filename.doc");  // Requires LFN
        root.createFile("Very Long Filename That Definitely Needs LFN Support.pdf");  // Long LFN
        
        List<FatEntry> entries = root.list();
        assertEquals(3, entries.size());
        
        // Verify all files are accessible by name
        assertNotNull(root.getEntry("SHORT.TXT"));
        assertNotNull(root.getEntry("medium_length_filename.doc"));
        assertNotNull(root.getEntry("Very Long Filename That Definitely Needs LFN Support.pdf"));
    }
    
    @Test
    void testLfnEntryGeneration() {
        // Test LFN entry creation for a long filename
        String longFilename = "This is a test of long filename generation.txt";
        byte[] shortName = {
            'T', 'H', 'I', 'S', 'I', 'S', '~', '1',  // THISIS~1
            'T', 'X', 'T'                             // TXT
        };
        
        List<LfnEntry> lfnEntries = LfnProcessor.generateLfnEntries(longFilename, shortName);
        
        assertFalse(lfnEntries.isEmpty());
        
        // Verify checksum consistency
        int expectedChecksum = LfnEntry.calculateChecksum(shortName);
        for (LfnEntry entry : lfnEntries) {
            assertEquals(expectedChecksum, entry.getChecksum());
        }
        
        // Verify sequence numbers
        assertEquals(1, lfnEntries.get(lfnEntries.size() - 1).getSequenceNumber());
        assertTrue(lfnEntries.get(0).isLastEntry());
    }
    
    @Test
    void testShortNameGeneration() {
        // Test short name generation for conflicts
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        existingNames.add("THISIS~1.TXT");
        existingNames.add("THISIS~2.TXT");
        
        String longFilename = "This is another test file.txt";
        String shortName = LfnProcessor.generateShortName(longFilename, existingNames);
        
        assertEquals("THISIS~3.TXT", shortName);
    }
    
    @Test
    void testChecksumCalculation() {
        // Test LFN checksum calculation
        byte[] shortName = {'T', 'E', 'S', 'T', ' ', ' ', ' ', ' ', 'T', 'X', 'T'};
        int checksum = LfnEntry.calculateChecksum(shortName);
        
        // Checksum should be consistent
        assertEquals(checksum, LfnEntry.calculateChecksum(shortName));
        
        // Different names should have different checksums
        byte[] differentName = {'O', 'T', 'H', 'E', 'R', ' ', ' ', ' ', 'T', 'X', 'T'};
        int differentChecksum = LfnEntry.calculateChecksum(differentName);
        
        assertNotEquals(checksum, differentChecksum);
    }
} 