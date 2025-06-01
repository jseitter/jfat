package net.seitter.jfat.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import net.seitter.jfat.util.FatUtils;

/**
 * Represents a directory in a FAT filesystem.
 */
public class FatDirectory extends FatEntry {

    /**
     * Creates a new directory with the given parameters.
     *
     * @param fileSystem   The filesystem
     * @param parent       The parent directory
     * @param name         The name of the directory
     * @param firstCluster The first cluster of the directory
     * @param attributes   The attributes of the directory
     * @param createTime   The creation time
     * @param modifyTime   The last modification time
     * @param accessDate   The last access date
     * @param entryOffset  The offset in the parent directory
     */
    protected FatDirectory(FatFileSystem fileSystem, FatDirectory parent, String name,
                         long firstCluster, int attributes,
                         Date createTime, Date modifyTime, Date accessDate,
                         int entryOffset) {
        super(fileSystem, parent, name, firstCluster, 0, attributes | ATTR_DIRECTORY,
              createTime, modifyTime, accessDate, entryOffset);
    }
    
    /**
     * Creates a new directory for the root directory.
     *
     * @param fileSystem   The filesystem
     * @param parent       The parent directory (null for root)
     * @param name         The name of the directory
     * @param firstCluster The first cluster of the directory
     */
    protected FatDirectory(FatFileSystem fileSystem, FatDirectory parent, String name, long firstCluster) {
        super(fileSystem, parent, name, firstCluster, 0, ATTR_DIRECTORY,
              new Date(), new Date(), new Date(), 0);
    }
    
    /**
     * Reads the directory data.
     *
     * @return The directory data as a byte array
     * @throws IOException If the directory cannot be read
     */
    public byte[] readDirectoryData() throws IOException {
        if (parent == null && fileSystem.getBootSector().getFatType() != FatType.FAT32) {
            // Root directory for FAT12/FAT16
            int rootEntryCount = fileSystem.getBootSector().getRootEntryCount();
            int rootDirSize = rootEntryCount * ENTRY_SIZE;
            int rootDirSector = fileSystem.getBootSector().getRootDirFirstSector();
            return fileSystem.getDevice().read(
                    rootDirSector * fileSystem.getBootSector().getBytesPerSector(),
                    rootDirSize);
        } else {
            // Regular directory or FAT32 root directory
            if (firstCluster == 0) {
                // Empty directory
                return new byte[0];
            }
            
            // Get the list of clusters
            List<Long> clusters = fileSystem.getFatTable().getClusterChain(firstCluster);
            int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
            byte[] result = new byte[clusters.size() * clusterSize];
            int destPos = 0;
            
            // Read data from each cluster
            for (Long cluster : clusters) {
                byte[] clusterData = fileSystem.readCluster(cluster);
                System.arraycopy(clusterData, 0, result, destPos, clusterSize);
                destPos += clusterSize;
            }
            
            return result;
        }
    }
    
    /**
     * Writes directory data.
     *
     * @param data The directory data to write
     * @throws IOException If the directory cannot be written
     */
    public void writeDirectoryData(byte[] data) throws IOException {
        if (parent == null && fileSystem.getBootSector().getFatType() != FatType.FAT32) {
            // Root directory for FAT12/FAT16
            int rootDirSector = fileSystem.getBootSector().getRootDirFirstSector();
            fileSystem.getDevice().write(
                    rootDirSector * fileSystem.getBootSector().getBytesPerSector(),
                    data);
        } else {
            // Regular directory or FAT32 root directory
            if (firstCluster == 0 && data.length > 0) {
                // Need to allocate clusters
                int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
                int numClusters = (data.length + clusterSize - 1) / clusterSize;
                firstCluster = fileSystem.allocateClusterChain(numClusters);
                
                // Update directory entry (if not root)
                if (parent != null) {
                    updateDirectoryEntry();
                }
            }
            
            if (firstCluster != 0) {
                // Write data to clusters
                List<Long> clusters = fileSystem.getFatTable().getClusterChain(firstCluster);
                int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
                int sourcePos = 0;
                
                for (Long cluster : clusters) {
                    byte[] clusterData = new byte[clusterSize];
                    int bytesToCopy = Math.min(data.length - sourcePos, clusterSize);
                    System.arraycopy(data, sourcePos, clusterData, 0, bytesToCopy);
                    fileSystem.writeCluster(cluster, clusterData);
                    sourcePos += clusterSize;
                    
                    if (sourcePos >= data.length) {
                        break;
                    }
                }
            }
        }
        
        // Update modification time
        modifyTime = new Date();
        accessDate = modifyTime;
        if (parent != null) {
            updateDirectoryEntry();
        }
    }
    
    /**
     * Gets a list of entries in this directory.
     *
     * @return A list of entries
     * @throws IOException If the directory cannot be read
     */
    public List<FatEntry> list() throws IOException {
        List<FatEntry> entries = new ArrayList<>();
        byte[] dirData = readDirectoryData();
        
        // Use LFN processor to parse directory with long filename support
        List<LfnProcessor.DirectoryEntryResult> results = 
            LfnProcessor.parseDirectoryWithLfn(dirData, fileSystem, this);
        
        for (LfnProcessor.DirectoryEntryResult result : results) {
            FatEntry entry = result.getFatEntry();
            
            // Skip "." and ".." entries
            if (entry.getName().equals(".") || entry.getName().equals("..")) {
                continue;
            }
            
            // Update the entry's displayed name if it has a long filename
            if (result.hasLongFilename()) {
                // Create a new entry with the long filename
                entry = createEntryWithLongFilename(entry, result.getLongFilename());
            }
            
            entries.add(entry);
        }
        
        // Update access date
        accessDate = new Date();
        if (parent != null) {
            updateDirectoryEntry();
        }
        
        return entries;
    }
    
    /**
     * Gets an entry in this directory by name.
     *
     * @param name The name of the entry (can be long filename or 8.3 name)
     * @return The entry, or null if not found
     * @throws IOException If the directory cannot be read
     */
    public FatEntry getEntry(String name) throws IOException {
        byte[] dirData = readDirectoryData();
        
        // Use LFN processor to parse directory with long filename support
        List<LfnProcessor.DirectoryEntryResult> results = 
            LfnProcessor.parseDirectoryWithLfn(dirData, fileSystem, this);
        
        for (LfnProcessor.DirectoryEntryResult result : results) {
            String displayName = result.getDisplayName();
            String shortName = result.getShortFilename();
            
            // Check both long filename and short filename
            if (displayName.equalsIgnoreCase(name) || shortName.equalsIgnoreCase(name)) {
                FatEntry entry = result.getFatEntry();
                
                // Return entry with long filename if available
                if (result.hasLongFilename()) {
                    return createEntryWithLongFilename(entry, result.getLongFilename());
                } else {
                    return entry;
                }
            }
        }
        
        return null; // Entry not found
    }
    
    /**
     * Finds a free entry slot in the directory.
     *
     * @return The offset of the free entry, or -1 if not found
     * @throws IOException If the directory cannot be read
     */
    private int findFreeEntry() throws IOException {
        byte[] dirData = readDirectoryData();
        
        for (int i = 0; i < dirData.length; i += ENTRY_SIZE) {
            // Check if this is a free entry (deleted or end of directory)
            if (dirData[i] == 0 || (dirData[i] & 0xFF) == 0xE5) {
                return i;
            }
        }
        
        // No free entry found
        return -1;
    }
    
    /**
     * Finds consecutive free entry slots in the directory.
     *
     * @param numEntries Number of consecutive entries needed
     * @return The offset of the first free entry, or -1 if not found
     * @throws IOException If the directory cannot be read
     */
    private int findConsecutiveFreeEntries(int numEntries) throws IOException {
        byte[] dirData = readDirectoryData();
        
        for (int i = 0; i <= dirData.length - (numEntries * ENTRY_SIZE); i += ENTRY_SIZE) {
            boolean allFree = true;
            
            // Check if all required consecutive entries are free
            for (int j = 0; j < numEntries; j++) {
                int offset = i + (j * ENTRY_SIZE);
                if (offset >= dirData.length || 
                    (dirData[offset] != 0 && (dirData[offset] & 0xFF) != 0xE5)) {
                    allFree = false;
                    break;
                }
            }
            
            if (allFree) {
                return i;
            }
        }
        
        return -1; // No consecutive free entries found
    }
    
    /**
     * Checks if a filename is a valid 8.3 short name.
     *
     * @param name The filename to check
     * @return True if it's a valid 8.3 name
     */
    private boolean isValidShortName(String name) {
        if (name == null || name.isEmpty() || name.length() > 12) {
            return false;
        }
        
        // Check for invalid characters
        if (name.matches(".*[\\s\"*+,/:;<=>?\\[\\]|].*")) {
            return false;
        }
        
        // Check 8.3 format
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) {
            // No extension, name must be <= 8 characters
            return name.length() <= 8;
        } else {
            // Has extension, check format
            String baseName = name.substring(0, dotIndex);
            String extension = name.substring(dotIndex + 1);
            return baseName.length() <= 8 && extension.length() <= 3 && 
                   baseName.length() > 0 && extension.length() > 0;
        }
    }
    
    /**
     * Gets the set of existing 8.3 short names in this directory.
     *
     * @return Set of existing short names
     * @throws IOException If the directory cannot be read
     */
    private Set<String> getExistingShortNames() throws IOException {
        Set<String> names = new HashSet<>();
        byte[] dirData = readDirectoryData();
        
        for (int i = 0; i < dirData.length; i += ENTRY_SIZE) {
            if (i + ENTRY_SIZE > dirData.length) {
                break;
            }
            
            // Check for end of directory
            if (dirData[i] == 0) {
                break;
            }
            
            // Skip deleted entries
            if ((dirData[i] & 0xFF) == 0xE5) {
                continue;
            }
            
            int attributes = dirData[i + 11] & 0xFF;
            
            // Skip LFN and volume label entries
            if (attributes == ATTR_LONG_NAME || (attributes & ATTR_VOLUME_ID) != 0) {
                continue;
            }
            
            // Extract 8.3 name
            byte[] nameData = new byte[11];
            System.arraycopy(dirData, i, nameData, 0, 11);
            String shortName = FatUtils.readFatName(nameData);
            names.add(shortName.toUpperCase());
        }
        
        return names;
    }
    
    /**
     * Creates a new file in this directory.
     *
     * @param name The name of the file (can be a long filename)
     * @return The created file
     * @throws IOException If the file cannot be created
     */
    public FatFile createFile(String name) throws IOException {
        // Check if the file already exists
        FatEntry existing = getEntry(name);
        if (existing != null) {
            if (existing.isDirectory()) {
                throw new IOException("Entry already exists as a directory: " + name);
            }
            return (FatFile) existing;
        }
        
        // Determine if we need LFN entries - use FatUtils helper
        boolean needsLfn = FatUtils.requiresLongFilename(name);
        String shortName = name;
        List<LfnEntry> lfnEntries = new ArrayList<>();
        
        if (needsLfn) {
            // Get existing 8.3 names to avoid conflicts
            Set<String> existingNames = getExistingShortNames();
            
            // Generate unique 8.3 name
            shortName = LfnProcessor.generateShortName(name, existingNames);
            
            // Generate LFN entries
            byte[] shortNameBytes = FatUtils.writeFatName(shortName);
            lfnEntries = LfnProcessor.generateLfnEntries(name, shortNameBytes);
        }
        
        // Calculate total entries needed
        int totalEntries = 1 + lfnEntries.size();
        
        // Find consecutive free entry slots
        int entryOffset = findConsecutiveFreeEntries(totalEntries);
        if (entryOffset == -1) {
            // Need to expand the directory
            expandDirectory();
            entryOffset = findConsecutiveFreeEntries(totalEntries);
            if (entryOffset == -1) {
                throw new IOException("Could not allocate directory entries for: " + name);
            }
        }
        
        // Read directory data and write LFN entries first
        byte[] dirData = readDirectoryData();
        int currentOffset = entryOffset;
        
        for (LfnEntry lfnEntry : lfnEntries) {
            byte[] lfnData = lfnEntry.toDirectoryEntry();
            System.arraycopy(lfnData, 0, dirData, currentOffset, ENTRY_SIZE);
            currentOffset += ENTRY_SIZE;
        }
        
        // Write the updated directory data with LFN entries
        if (!lfnEntries.isEmpty()) {
            writeDirectoryData(dirData);
        }
        
        // Create the 8.3 entry
        Date now = new Date();
        FatFile file = new FatFile(fileSystem, this, shortName, 0, 0, ATTR_ARCHIVE,
                                  now, now, now, currentOffset);
        
        // Write the 8.3 entry
        file.updateDirectoryEntry();
        
        // If we have a long filename, return a wrapper with the long name
        if (needsLfn) {
            return (FatFile) createEntryWithLongFilename(file, name);
        } else {
            return file;
        }
    }
    
    /**
     * Creates a new subdirectory in this directory.
     *
     * @param name The name of the subdirectory (can be a long filename)
     * @return The created directory
     * @throws IOException If the directory cannot be created
     */
    public FatDirectory createDirectory(String name) throws IOException {
        // Check if the directory already exists
        FatEntry existing = getEntry(name);
        if (existing != null) {
            if (!existing.isDirectory()) {
                throw new IOException("Entry already exists as a file: " + name);
            }
            return (FatDirectory) existing;
        }
        
        // Determine if we need LFN entries - use FatUtils helper
        boolean needsLfn = FatUtils.requiresLongFilename(name);
        String shortName = name;
        List<LfnEntry> lfnEntries = new ArrayList<>();
        
        if (needsLfn) {
            // Get existing 8.3 names to avoid conflicts
            Set<String> existingNames = getExistingShortNames();
            
            // Generate unique 8.3 name
            shortName = LfnProcessor.generateShortName(name, existingNames);
            
            // Generate LFN entries
            byte[] shortNameBytes = FatUtils.writeFatName(shortName);
            lfnEntries = LfnProcessor.generateLfnEntries(name, shortNameBytes);
        }
        
        // Calculate total entries needed
        int totalEntries = 1 + lfnEntries.size();
        
        // Find consecutive free entry slots
        int entryOffset = findConsecutiveFreeEntries(totalEntries);
        if (entryOffset == -1) {
            // Need to expand the directory
            expandDirectory();
            entryOffset = findConsecutiveFreeEntries(totalEntries);
            if (entryOffset == -1) {
                throw new IOException("Could not allocate directory entries for: " + name);
            }
        }
        
        // Allocate a cluster for the new directory
        long dirCluster = fileSystem.allocateCluster();
        
        // Read directory data and write LFN entries first
        byte[] dirData = readDirectoryData();
        int currentOffset = entryOffset;
        
        for (LfnEntry lfnEntry : lfnEntries) {
            byte[] lfnData = lfnEntry.toDirectoryEntry();
            System.arraycopy(lfnData, 0, dirData, currentOffset, ENTRY_SIZE);
            currentOffset += ENTRY_SIZE;
        }
        
        // Write the updated directory data with LFN entries
        if (!lfnEntries.isEmpty()) {
            writeDirectoryData(dirData);
        }
        
        // Create the 8.3 directory entry
        Date now = new Date();
        FatDirectory dir = new FatDirectory(fileSystem, this, shortName, dirCluster, ATTR_DIRECTORY,
                                          now, now, now, currentOffset);
        
        // Write the 8.3 entry
        dir.updateDirectoryEntry();
        
        // Initialize the directory with "." and ".." entries
        int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
        byte[] newDirData = new byte[clusterSize];
        
        // "." entry (points to itself)
        System.arraycopy(FatUtils.writeFatName("."), 0, newDirData, 0, 11);
        newDirData[11] = ATTR_DIRECTORY; // Attributes
        FatUtils.writeUInt16(newDirData, 26, (int) (dirCluster & 0xFFFF)); // First cluster low
        if (fileSystem.getBootSector().getFatType() == FatType.FAT32) {
            FatUtils.writeUInt16(newDirData, 20, (int) (dirCluster >> 16)); // First cluster high
        }
        
        // ".." entry (points to parent)
        System.arraycopy(FatUtils.writeFatName(".."), 0, newDirData, ENTRY_SIZE, 11);
        newDirData[ENTRY_SIZE + 11] = ATTR_DIRECTORY; // Attributes
        
        // Set parent cluster (or 0 for root)
        long parentCluster = (this.parent == null) ? 0 : this.firstCluster;
        FatUtils.writeUInt16(newDirData, ENTRY_SIZE + 26, (int) (parentCluster & 0xFFFF)); // First cluster low
        if (fileSystem.getBootSector().getFatType() == FatType.FAT32) {
            FatUtils.writeUInt16(newDirData, ENTRY_SIZE + 20, (int) (parentCluster >> 16)); // First cluster high
        }
        
        // Write the directory data
        fileSystem.writeCluster(dirCluster, newDirData);
        
        // If we have a long filename, return a wrapper with the long name
        if (needsLfn) {
            return (FatDirectory) createEntryWithLongFilename(dir, name);
        } else {
            return dir;
        }
    }
    
    /**
     * Expands the directory to accommodate more entries.
     *
     * @throws IOException If the directory cannot be expanded
     */
    private void expandDirectory() throws IOException {
        // Get the current directory data
        byte[] currentData = readDirectoryData();
        
        // For root directory in FAT12/FAT16, we can't expand it
        if (parent == null && fileSystem.getBootSector().getFatType() != FatType.FAT32) {
            throw new IOException("Cannot expand root directory in FAT12/FAT16");
        }
        
        // Allocate a new cluster
        long newCluster = fileSystem.allocateCluster();
        
        // If this is an empty directory, set the first cluster
        if (firstCluster == 0) {
            firstCluster = newCluster;
            if (parent != null) {
                updateDirectoryEntry();
            }
        } else {
            // Append the new cluster to the chain
            List<Long> clusters = fileSystem.getFatTable().getClusterChain(firstCluster);
            long lastCluster = clusters.get(clusters.size() - 1);
            fileSystem.getFatTable().setClusterEntry(lastCluster, newCluster);
            fileSystem.getFatTable().setClusterEntry(newCluster, fileSystem.getFatTable().getEndOfChainMarker());
        }
        
        // Initialize the new cluster with zeros
        int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
        byte[] newClusterData = new byte[clusterSize];
        fileSystem.writeCluster(newCluster, newClusterData);
    }
    
    @Override
    public void delete() throws IOException {
        if (parent == null) {
            throw new IOException("Cannot delete root directory");
        }
        
        // Check if the directory is empty
        List<FatEntry> entries = list();
        if (!entries.isEmpty()) {
            throw new IOException("Directory is not empty: " + name);
        }
        
        // Free the cluster chain
        if (firstCluster != 0) {
            fileSystem.freeClusterChain(firstCluster);
        }
        
        // Mark the directory entry as deleted
        byte[] dirData = parent.readDirectoryData();
        dirData[entryOffset] = (byte) 0xE5; // Mark as deleted
        parent.writeDirectoryData(dirData);
    }

    /**
     * Creates a new FatEntry with a long filename for display purposes.
     * This preserves the original entry data but overrides the name.
     */
    private FatEntry createEntryWithLongFilename(FatEntry originalEntry, String longFilename) {
        if (originalEntry.isDirectory()) {
            return new FatDirectory(fileSystem, this, longFilename, 
                                  originalEntry.getFirstCluster(), originalEntry.getAttributes(),
                                  originalEntry.getCreateTime(), originalEntry.getModifyTime(), 
                                  originalEntry.getAccessDate(), originalEntry.entryOffset) {
                
                private final FatEntry original = originalEntry;
                
                @Override
                public String getName() {
                    return longFilename;
                }
                
                @Override
                public void updateDirectoryEntry() throws IOException {
                    // Delegate to the original entry to ensure 8.3 name is written correctly
                    original.updateDirectoryEntry();
                }
            };
        } else {
            return new FatFile(fileSystem, this, longFilename, 
                             originalEntry.getFirstCluster(), originalEntry.getSize(), 
                             originalEntry.getAttributes(), originalEntry.getCreateTime(), 
                             originalEntry.getModifyTime(), originalEntry.getAccessDate(), 
                             originalEntry.entryOffset) {
                
                private final FatEntry original = originalEntry;
                
                @Override
                public String getName() {
                    return longFilename;
                }
                
                @Override
                public void updateDirectoryEntry() throws IOException {
                    // Delegate to the original entry to ensure 8.3 name is written correctly
                    original.updateDirectoryEntry();
                }
            };
        }
    }
} 