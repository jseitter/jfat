package net.seitter.jfat.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test for FatUtils to see the actual values returned by the implementation.
 */
public class FatUtilsDebugTest {

    @Test
    public void debugFatTimeDecoding() {
        // Time: 14:35:52 (should be encoded as 0x716A)
        int time = 0x716A;
        int[] decoded = FatUtils.decodeFatTime(time);
        
        System.out.println("FAT Time Debug (0x716A):");
        System.out.println("Hour: " + decoded[0]);
        System.out.println("Minute: " + decoded[1]);
        System.out.println("Second: " + decoded[2]);
        
        // Try encoding and see what we get
        int encoded = FatUtils.encodeFatTime(14, 35, 52);
        System.out.println("Encoded time: 0x" + Integer.toHexString(encoded));
        
        // Now decode our encoded value
        int[] reDecoded = FatUtils.decodeFatTime(encoded);
        System.out.println("Re-decoded Hour: " + reDecoded[0]);
        System.out.println("Re-decoded Minute: " + reDecoded[1]);
        System.out.println("Re-decoded Second: " + reDecoded[2]);
    }
    
    @Test
    public void debugFatDateDecoding() {
        // Date: 2023-05-15 (should be encoded as 0x57AF)
        int date = 0x57AF;
        int[] decoded = FatUtils.decodeFatDate(date);
        
        System.out.println("FAT Date Debug (0x57AF):");
        System.out.println("Year: " + decoded[0]);
        System.out.println("Month: " + decoded[1]);
        System.out.println("Day: " + decoded[2]);
        
        // Try encoding and see what we get
        int encoded = FatUtils.encodeFatDate(2023, 5, 15);
        System.out.println("Encoded date: 0x" + Integer.toHexString(encoded));
        
        // Now decode our encoded value
        int[] reDecoded = FatUtils.decodeFatDate(encoded);
        System.out.println("Re-decoded Year: " + reDecoded[0]);
        System.out.println("Re-decoded Month: " + reDecoded[1]);
        System.out.println("Re-decoded Day: " + reDecoded[2]);
    }
} 