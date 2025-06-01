package net.seitter.jfat.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
        
        System.out.println("Directory data size: " + dirData.length + " bytes");
        
        for (int i = 0; i < dirData.length; i += ENTRY_SIZE) {
            // Check if we have enough data for a complete entry
            if (i + ENTRY_SIZE > dirData.length) {
                System.out.println("Warning: Incomplete directory entry at offset " + i);
                break;
            }
            
            // Check if this is the end of the directory
            if (dirData[i] == 0) {
                System.out.println("End of directory marker found at offset " + i);
                break;
            }
            
            // Skip deleted entries
            if ((dirData[i] & 0xFF) == 0xE5) {
                System.out.println("Deleted entry found at offset " + i);
                continue;
            }
            
            // Skip volume label and long filename entries
            int attributes = dirData[i + 11] & 0xFF;
            if ((attributes & ATTR_VOLUME_ID) != 0) {
                System.out.println("Volume ID entry found at offset " + i);
                continue;
            }
            
            try {
                // Create the entry
                byte[] entryData = new byte[32];
                System.arraycopy(dirData, i, entryData, 0, 32);
                
                FatEntry entry = FatEntry.fromDirectoryEntry(fileSystem, this, entryData, i);
                
                // Skip "." and ".." entries
                if (entry.getName().equals(".") || entry.getName().equals("..")) {
                    System.out.println("Special entry found: " + entry.getName());
                    continue;
                }
                
                System.out.println("Entry found: " + entry.getName());
                entries.add(entry);
            } catch (Exception e) {
                System.out.println("Error processing directory entry at offset " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
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
     * @param name The name of the entry
     * @return The entry, or null if not found
     * @throws IOException If the directory cannot be read
     */
    public FatEntry getEntry(String name) throws IOException {
        byte[] dirData = readDirectoryData();
        
        for (int i = 0; i < dirData.length; i += ENTRY_SIZE) {
            // Check if we have enough data for a complete entry
            if (i + ENTRY_SIZE > dirData.length) {
                break;
            }
            
            // Check if this is the end of the directory
            if (dirData[i] == 0) {
                break;
            }
            
            // Skip deleted entries
            if ((dirData[i] & 0xFF) == 0xE5) {
                continue;
            }
            
            // Skip volume label and long filename entries
            int attributes = dirData[i + 11] & 0xFF;
            if ((attributes & ATTR_VOLUME_ID) != 0) {
                continue;
            }
            
            try {
                // Read the entry name
                byte[] nameData = new byte[11];
                System.arraycopy(dirData, i, nameData, 0, 11);
                String entryName = FatUtils.readFatName(nameData);
                
                if (entryName.equalsIgnoreCase(name)) {
                    // Found a matching entry, create the complete entry data
                    byte[] entryData = new byte[32];
                    System.arraycopy(dirData, i, entryData, 0, 32);
                    
                    // Create the entry
                    return FatEntry.fromDirectoryEntry(fileSystem, this, entryData, i);
                }
            } catch (Exception e) {
                System.out.println("Error processing directory entry while searching for '" + 
                                  name + "' at offset " + i + ": " + e.getMessage());
            }
        }
        
        // Entry not found
        return null;
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
     * Creates a new file in this directory.
     *
     * @param name The name of the file
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
        
        // Find a free entry slot
        int entryOffset = findFreeEntry();
        if (entryOffset == -1) {
            // Need to expand the directory
            expandDirectory();
            entryOffset = findFreeEntry();
        }
        
        // Create a new entry
        Date now = new Date();
        FatFile file = new FatFile(fileSystem, this, name, 0, 0, ATTR_ARCHIVE,
                                  now, now, now, entryOffset);
        
        // Write the entry to the directory
        file.updateDirectoryEntry();
        
        return file;
    }
    
    /**
     * Creates a new subdirectory in this directory.
     *
     * @param name The name of the subdirectory
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
        
        // Find a free entry slot
        int entryOffset = findFreeEntry();
        if (entryOffset == -1) {
            // Need to expand the directory
            expandDirectory();
            entryOffset = findFreeEntry();
        }
        
        // Allocate a cluster for the new directory
        long dirCluster = fileSystem.allocateCluster();
        
        // Create a new entry
        Date now = new Date();
        FatDirectory dir = new FatDirectory(fileSystem, this, name, dirCluster, ATTR_DIRECTORY,
                                          now, now, now, entryOffset);
        
        // Write the entry to the directory
        dir.updateDirectoryEntry();
        
        // Initialize the directory with "." and ".." entries
        int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
        byte[] dirData = new byte[clusterSize];
        
        // "." entry (points to itself)
        System.arraycopy(FatUtils.writeFatName("."), 0, dirData, 0, 11);
        dirData[11] = ATTR_DIRECTORY; // Attributes
        FatUtils.writeUInt16(dirData, 26, (int) (dirCluster & 0xFFFF)); // First cluster low
        if (fileSystem.getBootSector().getFatType() == FatType.FAT32) {
            FatUtils.writeUInt16(dirData, 20, (int) (dirCluster >> 16)); // First cluster high
        }
        
        // ".." entry (points to parent)
        System.arraycopy(FatUtils.writeFatName(".."), 0, dirData, ENTRY_SIZE, 11);
        dirData[ENTRY_SIZE + 11] = ATTR_DIRECTORY; // Attributes
        
        // Set parent cluster (or 0 for root)
        long parentCluster = (this.parent == null) ? 0 : this.firstCluster;
        FatUtils.writeUInt16(dirData, ENTRY_SIZE + 26, (int) (parentCluster & 0xFFFF)); // First cluster low
        if (fileSystem.getBootSector().getFatType() == FatType.FAT32) {
            FatUtils.writeUInt16(dirData, ENTRY_SIZE + 20, (int) (parentCluster >> 16)); // First cluster high
        }
        
        // Write the directory data
        fileSystem.writeCluster(dirCluster, dirData);
        
        return dir;
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
} 