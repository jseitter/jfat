package net.seitter.jfat.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Utility methods for working with FAT filesystem data structures.
 */
public class FatUtils {

    /**
     * FAT date/time format constants
     */
    private static final int SECONDS_MASK = 0x1F;
    private static final int MINUTES_MASK = 0x7E0;
    private static final int HOURS_MASK = 0xF800;
    private static final int DAY_MASK = 0x1F;
    private static final int MONTH_MASK = 0x1E0;
    private static final int YEAR_MASK = 0xFE00;

    /**
     * Reads a little-endian unsigned 16-bit value from a byte array.
     *
     * @param data   The byte array
     * @param offset The offset to read from
     * @return The unsigned 16-bit value as an int
     */
    public static int readUInt16(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort() & 0xFFFF;
    }

    /**
     * Reads a little-endian unsigned 32-bit value from a byte array.
     *
     * @param data   The byte array
     * @param offset The offset to read from
     * @return The unsigned 32-bit value as a long
     */
    public static long readUInt32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt() & 0xFFFFFFFFL;
    }

    /**
     * Writes a little-endian unsigned 16-bit value to a byte array.
     *
     * @param data   The byte array
     * @param offset The offset to write to
     * @param value  The value to write
     */
    public static void writeUInt16(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) (value & 0xFFFF));
    }

    /**
     * Writes a little-endian unsigned 32-bit value to a byte array.
     *
     * @param data   The byte array
     * @param offset The offset to write to
     * @param value  The value to write
     */
    public static void writeUInt32(byte[] data, int offset, long value) {
        ByteBuffer.wrap(data, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) (value & 0xFFFFFFFFL));
    }

    /**
     * Reads a FAT directory entry name from a byte array.
     * Handles the 8.3 format of FAT filenames.
     *
     * @param data The byte array containing the directory entry
     * @return The filename
     */
    public static String readFatName(byte[] data) {
        // The name is stored in the first 11 bytes (8 for name, 3 for extension)
        StringBuilder name = new StringBuilder();
        
        // Read the base name (first 8 bytes)
        for (int i = 0; i < 8; i++) {
            char c = (char) (data[i] & 0xFF);
            if (c != ' ') {
                name.append(c);
            }
        }
        
        // Read the extension (next 3 bytes)
        if (data[8] != ' ') {
            name.append('.');
            for (int i = 8; i < 11; i++) {
                char c = (char) (data[i] & 0xFF);
                if (c != ' ') {
                    name.append(c);
                }
            }
        }
        
        return name.toString();
    }

    /**
     * Writes a filename to a directory entry in FAT 8.3 format.
     *
     * @param name The filename to write
     * @return A byte array containing the 8.3 format name (11 bytes)
     */
    public static byte[] writeFatName(String name) {
        byte[] result = new byte[11];
        Arrays.fill(result, (byte) ' ');
        
        // Split into name and extension parts
        int dotIndex = name.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? name.substring(0, dotIndex) : name;
        String extension = (dotIndex > 0) ? name.substring(dotIndex + 1) : "";
        
        // Copy the base name (up to 8 characters)
        byte[] baseNameBytes = baseName.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(baseNameBytes, 0, result, 0, Math.min(baseNameBytes.length, 8));
        
        // Copy the extension (up to 3 characters)
        byte[] extensionBytes = extension.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(extensionBytes, 0, result, 8, Math.min(extensionBytes.length, 3));
        
        return result;
    }

    /**
     * Decodes a FAT time value.
     *
     * @param time The FAT time value
     * @return An array of integers: [hour, minute, second]
     */
    public static int[] decodeFatTime(int time) {
        int second = (time & SECONDS_MASK) * 2;
        int minute = (time & MINUTES_MASK) >> 5;
        int hour = (time & HOURS_MASK) >> 11;
        return new int[]{hour, minute, second};
    }

    /**
     * Decodes a FAT date value.
     *
     * @param date The FAT date value
     * @return An array of integers: [year, month, day]
     */
    public static int[] decodeFatDate(int date) {
        int day = date & DAY_MASK;
        int month = (date & MONTH_MASK) >> 5;
        int year = ((date & YEAR_MASK) >> 9) + 1980;
        return new int[]{year, month, day};
    }

    /**
     * Encodes time values into a FAT time value.
     *
     * @param hour   Hour (0-23)
     * @param minute Minute (0-59)
     * @param second Second (0-59)
     * @return The FAT time value
     */
    public static int encodeFatTime(int hour, int minute, int second) {
        return ((hour & 0x1F) << 11) | ((minute & 0x3F) << 5) | ((second / 2) & 0x1F);
    }

    /**
     * Encodes date values into a FAT date value.
     *
     * @param year  Year (1980-2107)
     * @param month Month (1-12)
     * @param day   Day (1-31)
     * @return The FAT date value
     */
    public static int encodeFatDate(int year, int month, int day) {
        return (((year - 1980) & 0x7F) << 9) | ((month & 0xF) << 5) | (day & 0x1F);
    }

    /**
     * Checks if a filename requires Long Filename (LFN) support.
     *
     * @param filename The filename to check
     * @return True if LFN is required
     */
    public static boolean requiresLongFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        
        // Check length
        if (filename.length() > 12) {
            return true;
        }
        
        // Check for non-ASCII characters
        for (char c : filename.toCharArray()) {
            if (c > 127) {
                return true;
            }
        }
        
        // Check for invalid 8.3 characters
        if (filename.matches(".*[\\s\"*+,/:;<=>?\\[\\]|].*")) {
            return true;
        }
        
        // Check for multiple dots
        if (filename.indexOf('.') != filename.lastIndexOf('.')) {
            return true;
        }
        
        // Check 8.3 format
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            // No extension, name must be <= 8 characters
            return filename.length() > 8;
        } else {
            // Has extension, check format
            String baseName = filename.substring(0, dotIndex);
            String extension = filename.substring(dotIndex + 1);
            return baseName.length() > 8 || extension.length() > 3 || 
                   baseName.length() == 0 || extension.length() == 0;
        }
    }
    
    /**
     * Converts a filename to a valid 8.3 format name (uppercase, no invalid chars).
     *
     * @param filename The original filename
     * @return A cleaned 8.3 format name
     */
    public static String toValidShortName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        
        // Remove invalid characters and convert to uppercase
        String cleaned = filename.toUpperCase()
                                .replaceAll("[^A-Z0-9!#$%&'()\\-@^_`{}~]", "")
                                .replaceAll("\\s+", "");
        
        // Handle extension
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot > 0 && lastDot < cleaned.length() - 1) {
            String name = cleaned.substring(0, lastDot);
            String ext = cleaned.substring(lastDot + 1);
            
            // Truncate to 8.3 format
            if (name.length() > 8) {
                name = name.substring(0, 8);
            }
            if (ext.length() > 3) {
                ext = ext.substring(0, 3);
            }
            
            return name + "." + ext;
        } else {
            // No extension or invalid dot position
            cleaned = cleaned.replace(".", "");
            if (cleaned.length() > 8) {
                cleaned = cleaned.substring(0, 8);
            }
            return cleaned;
        }
    }
    
    /**
     * Reads UTF-16LE characters from a byte array.
     *
     * @param data   The byte array
     * @param offset The starting offset
     * @param count  The number of UTF-16 characters to read
     * @return The decoded string
     */
    public static String readUtf16Le(byte[] data, int offset, int count) {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < count && offset + (i * 2) + 1 < data.length; i++) {
            int charCode = readUInt16(data, offset + (i * 2));
            
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
     * Writes UTF-16LE characters to a byte array.
     *
     * @param data   The byte array
     * @param offset The starting offset
     * @param text   The text to write
     * @param count  The number of UTF-16 character slots available
     */
    public static void writeUtf16Le(byte[] data, int offset, String text, int count) {
        int textLen = Math.min(text.length(), count);
        
        // Write the text characters
        for (int i = 0; i < textLen; i++) {
            char c = text.charAt(i);
            writeUInt16(data, offset + (i * 2), c);
        }
        
        // Add null terminator if there's space
        if (textLen < count) {
            writeUInt16(data, offset + (textLen * 2), 0);
            textLen++;
        }
        
        // Fill remaining slots with 0xFFFF padding
        for (int i = textLen; i < count; i++) {
            writeUInt16(data, offset + (i * 2), 0xFFFF);
        }
    }
} 