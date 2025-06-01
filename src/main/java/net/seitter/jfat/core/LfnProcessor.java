package net.seitter.jfat.core;

import net.seitter.jfat.util.FatUtils;
import java.util.*;

/**
 * Processes Long Filename (LFN) entries to reconstruct long filenames
 * and manages the creation of LFN entry sequences for storing long filenames.
 */
public class LfnProcessor {
    
    /**
     * Represents a parsed directory entry that may have an associated long filename.
     */
    public static class DirectoryEntryResult {
        private final FatEntry fatEntry;
        private final String longFilename;
        private final String shortFilename;
        private final int entryOffset;
        private final int totalEntriesUsed;
        
        public DirectoryEntryResult(FatEntry fatEntry, String longFilename, String shortFilename, 
                                  int entryOffset, int totalEntriesUsed) {
            this.fatEntry = fatEntry;
            this.longFilename = longFilename;
            this.shortFilename = shortFilename;
            this.entryOffset = entryOffset;
            this.totalEntriesUsed = totalEntriesUsed;
        }
        
        public FatEntry getFatEntry() { return fatEntry; }
        public String getLongFilename() { return longFilename; }
        public String getShortFilename() { return shortFilename; }
        public String getDisplayName() { return longFilename != null ? longFilename : shortFilename; }
        public int getEntryOffset() { return entryOffset; }
        public int getTotalEntriesUsed() { return totalEntriesUsed; }
        public boolean hasLongFilename() { return longFilename != null; }
    }
    
    /**
     * Parses directory data and extracts entries with their long filenames.
     *
     * @param directoryData The raw directory data
     * @param fileSystem    The filesystem instance
     * @param parentDir     The parent directory
     * @return List of parsed directory entries with long filenames
     */
    public static List<DirectoryEntryResult> parseDirectoryWithLfn(byte[] directoryData, 
                                                                  FatFileSystem fileSystem, 
                                                                  FatDirectory parentDir) {
        List<DirectoryEntryResult> results = new ArrayList<>();
        List<LfnEntry> lfnEntries = new ArrayList<>();
        
        for (int i = 0; i < directoryData.length; i += FatEntry.ENTRY_SIZE) {
            if (i + FatEntry.ENTRY_SIZE > directoryData.length) {
                break; // Incomplete entry
            }
            
            // Check for end of directory
            if (directoryData[i] == 0) {
                break;
            }
            
            // Skip deleted entries
            if ((directoryData[i] & 0xFF) == 0xE5) {
                lfnEntries.clear(); // Reset LFN sequence on deleted entry
                continue;
            }
            
            byte[] entryData = new byte[FatEntry.ENTRY_SIZE];
            System.arraycopy(directoryData, i, entryData, 0, FatEntry.ENTRY_SIZE);
            
            int attributes = entryData[11] & 0xFF;
            
            // Check if this is an LFN entry
            if (attributes == FatEntry.ATTR_LONG_NAME) {
                try {
                    LfnEntry lfnEntry = LfnEntry.fromDirectoryEntry(entryData);
                    lfnEntries.add(lfnEntry);
                } catch (Exception e) {
                    // Invalid LFN entry, reset sequence
                    lfnEntries.clear();
                }
                continue;
            }
            
            // Skip volume label entries
            if ((attributes & FatEntry.ATTR_VOLUME_ID) != 0) {
                lfnEntries.clear();
                continue;
            }
            
            // This is a regular 8.3 entry
            try {
                FatEntry fatEntry = FatEntry.fromDirectoryEntry(fileSystem, parentDir, entryData, i);
                String shortName = fatEntry.getName();
                String longName = null;
                int totalEntries = 1; // At least the 8.3 entry
                
                // Check if we have valid LFN entries for this file
                if (!lfnEntries.isEmpty()) {
                    longName = reconstructLongFilename(lfnEntries, entryData);
                    if (longName != null) {
                        totalEntries += lfnEntries.size();
                    }
                }
                
                // Calculate entry offset (accounting for LFN entries)
                int entryOffset = i - (lfnEntries.size() * FatEntry.ENTRY_SIZE);
                
                results.add(new DirectoryEntryResult(fatEntry, longName, shortName, 
                                                   entryOffset, totalEntries));
                
                // Reset LFN entries for next file
                lfnEntries.clear();
                
            } catch (Exception e) {
                // Error processing entry, skip it
                lfnEntries.clear();
            }
        }
        
        return results;
    }
    
    /**
     * Reconstructs a long filename from a sequence of LFN entries.
     *
     * @param lfnEntries The LFN entries (in the order they appear in the directory)
     * @param shortEntryData The 8.3 directory entry data for checksum verification
     * @return The reconstructed long filename, or null if invalid
     */
    private static String reconstructLongFilename(List<LfnEntry> lfnEntries, byte[] shortEntryData) {
        if (lfnEntries.isEmpty()) {
            return null;
        }
        
        // Calculate expected checksum from 8.3 name
        byte[] shortName = new byte[11];
        System.arraycopy(shortEntryData, 0, shortName, 0, 11);
        int expectedChecksum = LfnEntry.calculateChecksum(shortName);
        
        // Verify all entries have the same checksum
        for (LfnEntry entry : lfnEntries) {
            if (entry.getChecksum() != expectedChecksum) {
                return null; // Checksum mismatch, invalid LFN sequence
            }
        }
        
        // Sort entries by sequence number (they appear in reverse order in directory)
        List<LfnEntry> sortedEntries = new ArrayList<>(lfnEntries);
        sortedEntries.sort(Comparator.comparingInt(LfnEntry::getSequenceNumber));
        
        // Verify sequence integrity
        boolean foundLastEntry = false;
        for (int i = 0; i < sortedEntries.size(); i++) {
            LfnEntry entry = sortedEntries.get(i);
            
            // Check sequence number continuity
            if (entry.getSequenceNumber() != i + 1) {
                return null; // Sequence number gap
            }
            
            // Check for last entry flag
            if (entry.isLastEntry()) {
                if (i != sortedEntries.size() - 1) {
                    return null; // Last entry flag in wrong position
                }
                foundLastEntry = true;
            }
        }
        
        if (!foundLastEntry) {
            return null; // No last entry flag found
        }
        
        // Reconstruct the filename
        StringBuilder filename = new StringBuilder();
        for (LfnEntry entry : sortedEntries) {
            filename.append(entry.getNameFragment());
        }
        
        // Remove null terminator and any trailing 0xFFFF characters
        String result = filename.toString();
        int nullIndex = result.indexOf('\0');
        if (nullIndex >= 0) {
            result = result.substring(0, nullIndex);
        }
        
        return result;
    }
    
    /**
     * Generates LFN entries for a given long filename.
     *
     * @param longFilename The long filename to store
     * @param shortName    The corresponding 8.3 filename (11 bytes)
     * @return List of LFN entries to be written before the 8.3 entry
     */
    public static List<LfnEntry> generateLfnEntries(String longFilename, byte[] shortName) {
        if (longFilename == null || !FatUtils.requiresLongFilename(longFilename)) {
            return Collections.emptyList(); // No LFN needed for valid short names
        }
        
        if (longFilename.length() > FatEntry.LFN_MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("Filename too long: " + longFilename.length() + 
                                             " characters (max " + FatEntry.LFN_MAX_FILENAME_LENGTH + ")");
        }
        
        int checksum = LfnEntry.calculateChecksum(shortName);
        List<LfnEntry> entries = new ArrayList<>();
        
        // Calculate number of LFN entries needed
        int numEntries = (longFilename.length() + FatEntry.LFN_CHARS_PER_ENTRY - 1) / FatEntry.LFN_CHARS_PER_ENTRY;
        
        // Generate entries (they will be stored in reverse order in the directory)
        for (int i = 0; i < numEntries; i++) {
            int startPos = i * FatEntry.LFN_CHARS_PER_ENTRY;
            int endPos = Math.min(startPos + FatEntry.LFN_CHARS_PER_ENTRY, longFilename.length());
            
            String fragment = longFilename.substring(startPos, endPos);
            boolean isLastEntry = (i == numEntries - 1);
            
            LfnEntry entry = new LfnEntry(i + 1, fragment, checksum, isLastEntry);
            entries.add(entry);
        }
        
        // Return entries in reverse order (as they should appear in directory)
        Collections.reverse(entries);
        return entries;
    }
    
    /**
     * Generates a unique 8.3 filename for a long filename.
     * Uses the Windows-style algorithm with numeric tails (~1, ~2, etc.).
     *
     * @param longFilename The original long filename
     * @param existingNames Set of existing 8.3 names in the directory
     * @return A unique 8.3 filename
     */
    public static String generateShortName(String longFilename, Set<String> existingNames) {
        if (longFilename == null || longFilename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        
        // Remove invalid characters and convert to uppercase
        String baseName = longFilename.toUpperCase()
                                    .replaceAll("[^A-Z0-9!#$%&'()\\-@^_`{}~.]", "")
                                    .replaceAll("\\s+", "");
        
        // Split into name and extension
        String name, extension;
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < baseName.length() - 1) {
            name = baseName.substring(0, lastDot);
            extension = baseName.substring(lastDot + 1);
        } else {
            name = baseName.replace(".", ""); // Remove any dots if no valid extension
            extension = "";
        }
        
        // Truncate extension to 3 characters
        if (extension.length() > 3) {
            extension = extension.substring(0, 3);
        }
        
        // If the name is 8 characters or less and no invalid chars, try as-is first
        if (name.length() <= 8 && longFilename.equals(longFilename.toUpperCase()) && 
            !longFilename.contains(" ") && longFilename.matches("[A-Z0-9!#$%&'()\\-@^_`{}~.]*")) {
            String candidate = formatShortName(name, extension);
            if (!existingNames.contains(candidate.toUpperCase())) {
                return candidate;
            }
        }
        
        // For long names or names with invalid chars, use tilde pattern
        // Generate base name for tilde generation (first 6 characters to leave room for ~##)
        String baseForTilde = name.length() > 6 ? name.substring(0, 6) : name;
        
        // Look for existing pattern and use consistent prefix
        String prefix = baseForTilde;
        int maxExistingTail = 0;
        
        // Check existing names for similar patterns
        for (String existing : existingNames) {
            String existingUpper = existing.toUpperCase();
            if (existingUpper.startsWith(prefix) && existingUpper.contains("~")) {
                // Found existing tilde pattern, extract tail number
                int tildeIndex = existingUpper.indexOf('~');
                if (tildeIndex > 0) {
                    String beforeTilde = existingUpper.substring(0, tildeIndex);
                    if (beforeTilde.equals(prefix)) {
                        // Extract the number after ~
                        String afterTilde = existingUpper.substring(tildeIndex + 1);
                        int dotIndex = afterTilde.indexOf('.');
                        String numberPart = dotIndex > 0 ? afterTilde.substring(0, dotIndex) : afterTilde;
                        try {
                            int tailNumber = Integer.parseInt(numberPart);
                            maxExistingTail = Math.max(maxExistingTail, tailNumber);
                        } catch (NumberFormatException e) {
                            // Ignore invalid numbers
                        }
                    }
                }
            }
        }
        
        // Generate tilde names starting from the next available number
        int startTail = Math.max(1, maxExistingTail + 1);
        for (int tail = startTail; tail <= 999999; tail++) {
            String tailStr = "~" + tail;
            String candidateName = prefix + tailStr;
            String candidate = formatShortName(candidateName, extension);
            
            if (!existingNames.contains(candidate.toUpperCase())) {
                return candidate;
            }
        }
        
        throw new RuntimeException("Could not generate unique short name for: " + longFilename);
    }
    
    /**
     * Formats a name and extension into an 8.3 format string.
     */
    private static String formatShortName(String name, String extension) {
        if (extension.isEmpty()) {
            return name;
        } else {
            return name + "." + extension;
        }
    }
} 