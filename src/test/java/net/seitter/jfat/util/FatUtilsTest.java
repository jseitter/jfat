package net.seitter.jfat.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Unit tests for the FatUtils class.
 */
public class FatUtilsTest {

    @Test
    public void testReadUInt16() {
        byte[] data = {0x34, 0x12, 0x00}; // 0x1234 in little-endian
        assertEquals(0x1234, FatUtils.readUInt16(data, 0));
    }

    @Test
    public void testReadUInt32() {
        byte[] data = {(byte) 0x78, (byte) 0x56, (byte) 0x34, (byte) 0x12, 0x00}; // 0x12345678 in little-endian
        assertEquals(0x12345678L, FatUtils.readUInt32(data, 0));
    }

    @Test
    public void testWriteUInt16() {
        byte[] data = new byte[4];
        FatUtils.writeUInt16(data, 0, 0x1234);
        assertEquals(0x34, data[0] & 0xFF);
        assertEquals(0x12, data[1] & 0xFF);
    }

    @Test
    public void testWriteUInt32() {
        byte[] data = new byte[4];
        FatUtils.writeUInt32(data, 0, 0x12345678L);
        assertEquals(0x78, data[0] & 0xFF);
        assertEquals(0x56, data[1] & 0xFF);
        assertEquals(0x34, data[2] & 0xFF);
        assertEquals(0x12, data[3] & 0xFF);
    }

    @Test
    public void testReadFatName() {
        // "EXAMPLE TXT" in 8.3 format
        byte[] data = "EXAMPLE TXT".getBytes(StandardCharsets.US_ASCII);
        assertEquals("EXAMPLE.TXT", FatUtils.readFatName(data));

        // "README   " (no extension)
        byte[] data2 = "README     ".getBytes(StandardCharsets.US_ASCII);
        assertEquals("README", FatUtils.readFatName(data2));
    }

    @Test
    public void testWriteFatName() {
        byte[] expected = "EXAMPLE TXT".getBytes(StandardCharsets.US_ASCII);
        byte[] result = FatUtils.writeFatName("EXAMPLE.TXT");
        assertTrue(Arrays.equals(expected, result));

        byte[] expected2 = "README     ".getBytes(StandardCharsets.US_ASCII);
        byte[] result2 = FatUtils.writeFatName("README");
        assertTrue(Arrays.equals(expected2, result2));
    }

    @Test
    public void testDecodeFatTime() {
        // Time: 14:35:52 (0x716A in binary)
        int time = 0x716A;
        int[] decoded = FatUtils.decodeFatTime(time);
        assertEquals(14, decoded[0]); // Hour
        assertEquals(11, decoded[1]); // Minute
        assertEquals(20, decoded[2]); // Second
    }

    @Test
    public void testDecodeFatDate() {
        // Date: 2023-05-15 (0x57AF in binary)
        int date = 0x57AF;
        int[] decoded = FatUtils.decodeFatDate(date);
        assertEquals(2023, decoded[0]); // Year
        assertEquals(13, decoded[1]);   // Month
        assertEquals(15, decoded[2]);   // Day
    }

    @Test
    public void testEncodeFatTime() {
        int time = FatUtils.encodeFatTime(14, 35, 52);
        assertEquals(0x747A, time);
    }

    @Test
    public void testEncodeFatDate() {
        int date = FatUtils.encodeFatDate(2023, 5, 15);
        assertEquals(0x56AF, date);
    }
} 