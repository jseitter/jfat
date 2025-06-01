package net.seitter.jfat.core;

import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.FatUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a FAT filesystem.
 * This is the main entry point for accessing a FAT filesystem.
 */
public class FatFileSystem implements Closeable {
    private final DeviceAccess device;
    private final BootSector bootSector;
    private final FatTable fatTable;
    private FatDirectory rootDirectory;
    
    /**
     * Creates a new FAT filesystem from the given device.
     *
     * @param device The device to access
     * @throws IOException If the filesystem cannot be mounted
     */
    private FatFileSystem(DeviceAccess device) throws IOException {
        this.device = device;
        this.bootSector = new BootSector(device);
        this.fatTable = new FatTable(device, bootSector);
        initRootDirectory();
    }
    
    /**
     * Mounts a FAT filesystem from the given device.
     *
     * @param device The device to access
     * @return A new FatFileSystem instance
     * @throws IOException If the filesystem cannot be mounted
     */
    public static FatFileSystem mount(DeviceAccess device) throws IOException {
        return new FatFileSystem(device);
    }
    
    /**
     * Initializes the root directory based on the FAT type.
     *
     * @throws IOException If the root directory cannot be initialized
     */
    private void initRootDirectory() throws IOException {
        if (bootSector.getFatType() == FatType.FAT32) {
            // For FAT32, the root directory is stored in clusters
            rootDirectory = new FatDirectory(this, null, "", bootSector.getFat32RootCluster());
        } else {
            // For FAT12/FAT16, the root directory has a fixed location
            rootDirectory = new FatDirectory(this, null, "", -1);
        }
    }
    
    /**
     * Gets the root directory of the filesystem.
     *
     * @return The root directory
     */
    public FatDirectory getRootDirectory() {
        return rootDirectory;
    }
    
    /**
     * Gets a file from the filesystem by its path.
     *
     * @param path The path to the file
     * @return The file
     * @throws IOException If the file cannot be found
     */
    public FatFile getFile(String path) throws IOException {
        // Split the path into components
        String[] components = path.split("/");
        FatDirectory currentDir = rootDirectory;
        
        // Navigate to the parent directory
        for (int i = 0; i < components.length - 1; i++) {
            if (components[i].isEmpty()) {
                continue;
            }
            FatEntry entry = currentDir.getEntry(components[i]);
            if (entry == null || !entry.isDirectory()) {
                throw new IOException("Directory not found: " + components[i]);
            }
            currentDir = (FatDirectory) entry;
        }
        
        // Get the file from the parent directory
        String fileName = components[components.length - 1];
        FatEntry entry = currentDir.getEntry(fileName);
        if (entry == null) {
            throw new IOException("File not found: " + fileName);
        }
        if (entry.isDirectory()) {
            throw new IOException("Path refers to a directory, not a file: " + path);
        }
        
        return (FatFile) entry;
    }
    
    /**
     * Creates a new file in the filesystem.
     *
     * @param path The path to the new file
     * @return The created file
     * @throws IOException If the file cannot be created
     */
    public FatFile createFile(String path) throws IOException {
        // Split the path into components
        String[] components = path.split("/");
        FatDirectory currentDir = rootDirectory;
        
        // Navigate to the parent directory
        for (int i = 0; i < components.length - 1; i++) {
            if (components[i].isEmpty()) {
                continue;
            }
            FatEntry entry = currentDir.getEntry(components[i]);
            if (entry == null) {
                // Create the directory if it doesn't exist
                currentDir = currentDir.createDirectory(components[i]);
            } else if (entry.isDirectory()) {
                currentDir = (FatDirectory) entry;
            } else {
                throw new IOException("Path component is not a directory: " + components[i]);
            }
        }
        
        // Create the file in the parent directory
        String fileName = components[components.length - 1];
        return currentDir.createFile(fileName);
    }
    
    /**
     * Gets the boot sector of the filesystem.
     *
     * @return The boot sector
     */
    public BootSector getBootSector() {
        return bootSector;
    }
    
    /**
     * Gets the FAT table of the filesystem.
     *
     * @return The FAT table
     */
    public FatTable getFatTable() {
        return fatTable;
    }
    
    /**
     * Gets the device that this filesystem is mounted on.
     *
     * @return The device
     */
    public DeviceAccess getDevice() {
        return device;
    }
    
    /**
     * Reads data from the specified cluster.
     *
     * @param clusterNumber The cluster number to read
     * @return The data in the cluster
     * @throws IOException If the cluster cannot be read
     */
    public byte[] readCluster(long clusterNumber) throws IOException {
        long sectorNumber = bootSector.clusterToSector(clusterNumber);
        int clusterSize = bootSector.getClusterSizeBytes();
        return device.read(sectorNumber * bootSector.getBytesPerSector(), clusterSize);
    }
    
    /**
     * Writes data to the specified cluster.
     *
     * @param clusterNumber The cluster number to write to
     * @param data         The data to write
     * @throws IOException If the cluster cannot be written
     */
    public void writeCluster(long clusterNumber, byte[] data) throws IOException {
        long sectorNumber = bootSector.clusterToSector(clusterNumber);
        device.write(sectorNumber * bootSector.getBytesPerSector(), data);
    }
    
    /**
     * Allocates a new cluster in the filesystem.
     *
     * @return The allocated cluster number
     * @throws IOException If a cluster cannot be allocated
     */
    public long allocateCluster() throws IOException {
        return fatTable.allocateCluster();
    }
    
    /**
     * Allocates a chain of clusters in the filesystem.
     *
     * @param count The number of clusters to allocate
     * @return The first cluster in the chain
     * @throws IOException If the clusters cannot be allocated
     */
    public long allocateClusterChain(int count) throws IOException {
        if (count <= 0) {
            return 0;
        }
        
        long firstCluster = allocateCluster();
        long currentCluster = firstCluster;
        
        for (int i = 1; i < count; i++) {
            long nextCluster = allocateCluster();
            fatTable.setClusterEntry(currentCluster, nextCluster);
            currentCluster = nextCluster;
        }
        
        // Mark the end of the chain
        fatTable.setClusterEntry(currentCluster, fatTable.getEndOfChainMarker());
        
        return firstCluster;
    }
    
    /**
     * Frees a cluster chain starting from the given cluster.
     *
     * @param startCluster The first cluster in the chain
     * @throws IOException If the chain cannot be freed
     */
    public void freeClusterChain(long startCluster) throws IOException {
        fatTable.freeClusterChain(startCluster);
    }
    
    @Override
    public void close() throws IOException {
        device.close();
    }
} 