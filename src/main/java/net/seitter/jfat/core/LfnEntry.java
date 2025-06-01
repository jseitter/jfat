package net.seitter.jfat.core;

import net.seitter.jfat.util.FatUtils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a Long Filename (LFN) directory entry in a FAT filesystem.
 * LFN entries allow filenames longer than the traditional 8.3 format
 * and support Unicode characters via UTF-16 encoding.
 */
public class LfnEntry {
    private final int sequenceNumber;    // Sequence number (1-based, with 0x40 flag for last entry)
    private final String nameFragment;   // The portion of the filename stored in this entry (up to 13 chars)
    private final int attributes;        // Always 0x0F for LFN entries
    private final int type;             // Always 0 for LFN entries
    private final int checksum;         // Checksum of the corresponding 8.3 filename
    private final int firstClusterLow;  // Always 0 for LFN entries
    
    /**
     * Creates a new LFN entry.
     *
     * @param sequenceNumber The sequence number (1-based)
     * @param nameFragment   The portion of the filename (up to 13 UTF-16 characters)
     * @param checksum       Checksum of the corresponding 8.3 filename
     * @param isLastEntry    True if this is the last entry in the sequence
     */
    public LfnEntry(int sequenceNumber, String nameFragment, int checksum, boolean isLastEntry) {
        this.sequenceNumber = isLastEntry ? (sequenceNumber | FatEntry.LFN_LAST_ENTRY_MASK) : sequenceNumber;
        this.nameFragment = nameFragment;
        this.attributes = FatEntry.ATTR_LONG_NAME;
        this.type = 0;
        this.checksum = checksum;
        this.firstClusterLow = 0;
    }
    
    /**
     * Creates an LFN entry from raw directory entry data.
     *
     * @param entryData The 32-byte directory entry data
     * @return The parsed LFN entry
     * @throws IllegalArgumentException If the data doesn't represent a valid LFN entry
     */
    public static LfnEntry fromDirectoryEntry(byte[] entryData) {
        if (entryData.length != FatEntry.LFN_ENTRY_SIZE) {
            throw new IllegalArgumentException("LFN entry data must be exactly 32 bytes");
        }
        
        // Verify this is an LFN entry
        int attributes = entryData[11] & 0xFF;
        if (attributes != FatEntry.ATTR_LONG_NAME) {
            throw new IllegalArgumentException("Not an LFN entry: attributes = 0x" + 
                                             Integer.toHexString(attributes));
        }
        
        int sequenceNumber = entryData[0] & 0xFF;
        int checksum = entryData[13] & 0xFF;
        
        // Extract name fragments from three locations in the entry
        StringBuilder nameBuilder = new StringBuilder();
        
        // Characters 1-5: bytes 1-10 (UTF-16LE)
        nameBuilder.append(extractUtf16String(entryData, 1, 5));
        
        // Characters 6-11: bytes 14-25 (UTF-16LE)
        nameBuilder.append(extractUtf16String(entryData, 14, 6));
        
        // Characters 12-13: bytes 28-31 (UTF-16LE)
        nameBuilder.append(extractUtf16String(entryData, 28, 2));
        
        boolean isLastEntry = (sequenceNumber & FatEntry.LFN_LAST_ENTRY_MASK) != 0;
        int actualSequenceNumber = sequenceNumber & FatEntry.LFN_SEQUENCE_MASK;
        
        return new LfnEntry(actualSequenceNumber, nameBuilder.toString(), checksum, isLastEntry);
    }
    
    /**
     * Converts this LFN entry to raw directory entry data.
     *
     * @return A 32-byte array representing this LFN entry
     */
    public byte[] toDirectoryEntry() {
        byte[] entryData = new byte[FatEntry.LFN_ENTRY_SIZE];
        
        // Sequence number (with last entry flag if applicable)
        entryData[0] = (byte) sequenceNumber;
        
        // Pad name fragment to 13 characters with null terminators and 0xFFFF padding
        String paddedName = nameFragment;
        if (paddedName.length() < FatEntry.LFN_CHARS_PER_ENTRY) {
            // Add null terminator
            paddedName += '\0';
            // Pad remaining characters with 0xFFFF
            while (paddedName.length() < FatEntry.LFN_CHARS_PER_ENTRY) {
                paddedName += '\uFFFF';
            }
        }
        
        // Store name fragments in UTF-16LE format
        byte[] nameBytes = paddedName.getBytes(StandardCharsets.UTF_16LE);
        
        // Characters 1-5: bytes 1-10
        System.arraycopy(nameBytes, 0, entryData, 1, Math.min(10, nameBytes.length));
        
        // Attributes (always 0x0F for LFN)
        entryData[11] = (byte) attributes;
        
        // Type (always 0)
        entryData[12] = (byte) type;
        
        // Checksum
        entryData[13] = (byte) checksum;
        
        // Characters 6-11: bytes 14-25
        if (nameBytes.length > 10) {
            System.arraycopy(nameBytes, 10, entryData, 14, Math.min(12, nameBytes.length - 10));
        } else {
            // Fill with 0xFFFF padding
            Arrays.fill(entryData, 14, 26, (byte) 0xFF);
        }
        
        // First cluster low (always 0)
        entryData[26] = 0;
        entryData[27] = 0;
        
        // Characters 12-13: bytes 28-31
        if (nameBytes.length > 22) {
            System.arraycopy(nameBytes, 22, entryData, 28, Math.min(4, nameBytes.length - 22));
        } else {
            // Fill with 0xFFFF padding
            Arrays.fill(entryData, 28, 32, (byte) 0xFF);
        }
        
        return entryData;
    }
    
    /**
     * Extracts a UTF-16 string from a byte array.
     *
     * @param data   The byte array
     * @param offset The starting offset
     * @param maxChars Maximum number of characters to extract
     * @return The extracted string
     */
    private static String extractUtf16String(byte[] data, int offset, int maxChars) {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < maxChars && offset + (i * 2) + 1 < data.length; i++) {
            int charCode = FatUtils.readUInt16(data, offset + (i * 2));
            
            // Stop at null terminator
            if (charCode == 0) {
                break;
            }
            
            // Skip 0xFFFF padding
            if (charCode == 0xFFFF) {
                continue;
            }
            
            result.append((char) charCode);
        }
        
        return result.toString();
    }
    
    /**
     * Calculates the checksum for an 8.3 filename.
     * This checksum is used to verify that LFN entries correspond to the correct 8.3 entry.
     *
     * @param shortName The 11-byte 8.3 filename (without null termination)
     * @return The calculated checksum
     */
    public static int calculateChecksum(byte[] shortName) {
        if (shortName.length != 11) {
            throw new IllegalArgumentException("Short name must be exactly 11 bytes");
        }
        
        int checksum = 0;
        for (byte b : shortName) {
            // Checksum algorithm: ((checksum & 1) << 7) + (checksum >> 1) + byte
            checksum = (((checksum & 1) << 7) + (checksum >> 1) + (b & 0xFF)) & 0xFF;
        }
        
        return checksum;
    }
    
    // Getters
    public int getSequenceNumber() {
        return sequenceNumber & FatEntry.LFN_SEQUENCE_MASK;
    }
    
    public boolean isLastEntry() {
        return (sequenceNumber & FatEntry.LFN_LAST_ENTRY_MASK) != 0;
    }
    
    public String getNameFragment() {
        return nameFragment;
    }
    
    public int getChecksum() {
        return checksum;
    }
    
    public int getAttributes() {
        return attributes;
    }
    
    @Override
    public String toString() {
        return String.format("LfnEntry{seq=%d, last=%s, fragment='%s', checksum=0x%02X}", 
                           getSequenceNumber(), isLastEntry(), nameFragment, checksum);
    }
} 