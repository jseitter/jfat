package net.seitter.jfat.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a file in a FAT filesystem.
 */
public class FatFile extends FatEntry {
    
    /**
     * Creates a new file with the given parameters.
     *
     * @param fileSystem   The filesystem
     * @param parent       The parent directory
     * @param name         The name of the file
     * @param firstCluster The first cluster of the file
     * @param size         The size of the file
     * @param attributes   The attributes of the file
     * @param createTime   The creation time
     * @param modifyTime   The last modification time
     * @param accessDate   The last access date
     * @param entryOffset  The offset in the parent directory
     */
    protected FatFile(FatFileSystem fileSystem, FatDirectory parent, String name,
                    long firstCluster, long size, int attributes,
                    Date createTime, Date modifyTime, Date accessDate,
                    int entryOffset) {
        super(fileSystem, parent, name, firstCluster, size, attributes, 
              createTime, modifyTime, accessDate, entryOffset);
    }
    
    /**
     * Reads the entire contents of the file.
     *
     * @return The file contents as a byte array
     * @throws IOException If the file cannot be read
     */
    public byte[] readAllBytes() throws IOException {
        // For empty files or unallocated files
        if (size == 0 || firstCluster == 0) {
            return new byte[0];
        }
        
        // Get the list of clusters
        List<Long> clusters = fileSystem.getFatTable().getClusterChain(firstCluster);
        
        // Calculate the number of bytes to read from each cluster
        int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
        byte[] result = new byte[(int) size];
        int bytesRemaining = (int) size;
        int destPos = 0;
        
        // Read data from each cluster
        for (Long cluster : clusters) {
            byte[] clusterData = fileSystem.readCluster(cluster);
            int bytesToCopy = Math.min(bytesRemaining, clusterSize);
            System.arraycopy(clusterData, 0, result, destPos, bytesToCopy);
            destPos += bytesToCopy;
            bytesRemaining -= bytesToCopy;
            
            if (bytesRemaining <= 0) {
                break;
            }
        }
        
        // Update last access date
        accessDate = new Date();
        updateDirectoryEntry();
        
        return result;
    }
    
    /**
     * Writes data to the file.
     *
     * @param data The data to write
     * @throws IOException If the file cannot be written
     */
    public void write(byte[] data) throws IOException {
        // Get the cluster size
        int clusterSize = fileSystem.getBootSector().getClusterSizeBytes();
        
        // Calculate the number of clusters needed
        int numClusters = (data.length + clusterSize - 1) / clusterSize;
        
        // If the file already has clusters, free them
        if (firstCluster != 0) {
            fileSystem.freeClusterChain(firstCluster);
        }
        
        // Allocate new clusters
        if (numClusters > 0) {
            firstCluster = fileSystem.allocateClusterChain(numClusters);
            
            // Write data to clusters
            List<Long> clusters = fileSystem.getFatTable().getClusterChain(firstCluster);
            int sourcePos = 0;
            
            for (Long cluster : clusters) {
                byte[] clusterData = new byte[clusterSize];
                int bytesToCopy = Math.min(data.length - sourcePos, clusterSize);
                System.arraycopy(data, sourcePos, clusterData, 0, bytesToCopy);
                fileSystem.writeCluster(cluster, clusterData);
                sourcePos += bytesToCopy;
                
                if (sourcePos >= data.length) {
                    break;
                }
            }
        } else {
            firstCluster = 0;
        }
        
        // Update file size and modification time
        size = data.length;
        modifyTime = new Date();
        accessDate = modifyTime;
        attributes |= ATTR_ARCHIVE; // Set archive bit
        
        // Update directory entry
        updateDirectoryEntry();
    }
    
    /**
     * Appends data to the end of the file.
     *
     * @param data The data to append
     * @throws IOException If the data cannot be appended
     */
    public void append(byte[] data) throws IOException {
        if (data.length == 0) {
            return;
        }
        
        // Read existing data and append the new data
        byte[] existingData = readAllBytes();
        byte[] newData = new byte[existingData.length + data.length];
        System.arraycopy(existingData, 0, newData, 0, existingData.length);
        System.arraycopy(data, 0, newData, existingData.length, data.length);
        
        // Write the combined data
        write(newData);
    }
    
    /**
     * Truncates the file to the given size.
     *
     * @param newSize The new size of the file
     * @throws IOException If the file cannot be truncated
     */
    public void truncate(long newSize) throws IOException {
        if (newSize < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
        
        if (newSize == size) {
            return;
        }
        
        if (newSize > size) {
            // Extend the file with zeros
            byte[] zeros = new byte[(int) (newSize - size)];
            append(zeros);
        } else {
            // Shrink the file
            byte[] data = readAllBytes();
            byte[] truncatedData = new byte[(int) newSize];
            System.arraycopy(data, 0, truncatedData, 0, truncatedData.length);
            write(truncatedData);
        }
    }
    
    @Override
    public void delete() throws IOException {
        if (parent == null) {
            throw new IOException("Cannot delete root directory");
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