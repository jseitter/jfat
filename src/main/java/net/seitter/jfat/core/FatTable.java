package net.seitter.jfat.core;

import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.FatUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the File Allocation Table (FAT) of a FAT filesystem.
 * The FAT is used to track which clusters are allocated and the chain of clusters for each file.
 */
public class FatTable {
    private final DeviceAccess device;
    private final BootSector bootSector;
    
    // FAT entry markers
    private static final long FAT12_EOF_MARKER = 0xFFF;
    private static final long FAT16_EOF_MARKER = 0xFFFF;
    private static final long FAT32_EOF_MARKER = 0x0FFFFFFF;
    
    private static final long FAT12_BAD_CLUSTER = 0xFF7;
    private static final long FAT16_BAD_CLUSTER = 0xFFF7;
    private static final long FAT32_BAD_CLUSTER = 0x0FFFFFF7;
    
    /**
     * Creates a new FatTable for the given device and boot sector.
     *
     * @param device     The device to access
     * @param bootSector The boot sector
     */
    public FatTable(DeviceAccess device, BootSector bootSector) {
        this.device = device;
        this.bootSector = bootSector;
    }
    
    /**
     * Gets the value of a cluster entry in the FAT.
     *
     * @param clusterNumber The cluster number
     * @return The value of the entry
     * @throws IOException If the entry cannot be read
     */
    public long getClusterEntry(long clusterNumber) throws IOException {
        FatType fatType = bootSector.getFatType();
        int bytesPerSector = bootSector.getBytesPerSector();
        int fatOffset;
        
        switch (fatType) {
            case FAT12:
                fatOffset = (int) (clusterNumber + (clusterNumber / 2)); // 1.5 bytes per entry
                break;
            case FAT16:
                fatOffset = (int) (clusterNumber * 2); // 2 bytes per entry
                break;
            case FAT32:
                fatOffset = (int) (clusterNumber * 4); // 4 bytes per entry
                break;
            default:
                throw new IOException("Unsupported FAT type");
        }
        
        // Calculate the sector and offset within the sector
        int fatSector = bootSector.getReservedSectorCount() + (fatOffset / bytesPerSector);
        int offset = fatOffset % bytesPerSector;
        
        // Read the sector containing the FAT entry
        byte[] sectorData = device.read(fatSector * bytesPerSector, bytesPerSector);
        
        // Extract the entry value based on the FAT type
        long entryValue;
        switch (fatType) {
            case FAT12:
                // For FAT12, entries are 1.5 bytes, so we might need to read two bytes
                if (offset == bytesPerSector - 1) {
                    // The entry spans two sectors, need to read the next sector
                    byte[] nextSectorData = device.read((fatSector + 1) * bytesPerSector, bytesPerSector);
                    int value = (sectorData[offset] & 0xFF) | ((nextSectorData[0] & 0xFF) << 8);
                    entryValue = (clusterNumber & 1) == 1 ? (value >> 4) : (value & 0xFFF);
                } else {
                    int value = (sectorData[offset] & 0xFF) | ((sectorData[offset + 1] & 0xFF) << 8);
                    entryValue = (clusterNumber & 1) == 1 ? (value >> 4) : (value & 0xFFF);
                }
                break;
            case FAT16:
                entryValue = FatUtils.readUInt16(sectorData, offset);
                break;
            case FAT32:
                entryValue = FatUtils.readUInt32(sectorData, offset) & 0x0FFFFFFF; // Mask out the reserved bits
                break;
            default:
                throw new IOException("Unsupported FAT type");
        }
        
        return entryValue;
    }
    
    /**
     * Sets the value of a cluster entry in the FAT.
     *
     * @param clusterNumber The cluster number
     * @param value         The value to set
     * @throws IOException If the entry cannot be written
     */
    public void setClusterEntry(long clusterNumber, long value) throws IOException {
        FatType fatType = bootSector.getFatType();
        int bytesPerSector = bootSector.getBytesPerSector();
        int fatOffset;
        
        switch (fatType) {
            case FAT12:
                fatOffset = (int) (clusterNumber + (clusterNumber / 2)); // 1.5 bytes per entry
                break;
            case FAT16:
                fatOffset = (int) (clusterNumber * 2); // 2 bytes per entry
                break;
            case FAT32:
                fatOffset = (int) (clusterNumber * 4); // 4 bytes per entry
                break;
            default:
                throw new IOException("Unsupported FAT type");
        }
        
        // Calculate the sector and offset within the sector
        int fatSector = bootSector.getReservedSectorCount() + (fatOffset / bytesPerSector);
        int offset = fatOffset % bytesPerSector;
        
        // For each FAT table (usually there are 2)
        for (int fatNumber = 0; fatNumber < bootSector.getNumberOfFats(); fatNumber++) {
            int currentFatSector = fatSector + (fatNumber * bootSector.getSectorsPerFat());
            
            // Read the sector containing the FAT entry
            byte[] sectorData = device.read(currentFatSector * bytesPerSector, bytesPerSector);
            
            // Update the entry value based on the FAT type
            switch (fatType) {
                case FAT12:
                    // For FAT12, entries are 1.5 bytes, so we might need to update two bytes
                    if (offset == bytesPerSector - 1) {
                        // The entry spans two sectors
                        byte[] nextSectorData = device.read((currentFatSector + 1) * bytesPerSector, bytesPerSector);
                        
                        if ((clusterNumber & 1) == 1) {
                            // Odd cluster number
                            sectorData[offset] = (byte) ((sectorData[offset] & 0x0F) | ((value & 0x0F) << 4));
                            nextSectorData[0] = (byte) ((value & 0xFF0) >> 4);
                        } else {
                            // Even cluster number
                            sectorData[offset] = (byte) (value & 0xFF);
                            nextSectorData[0] = (byte) ((nextSectorData[0] & 0xF0) | ((value & 0xF00) >> 8));
                        }
                        
                        // Write back both sectors
                        device.write(currentFatSector * bytesPerSector, sectorData);
                        device.write((currentFatSector + 1) * bytesPerSector, nextSectorData);
                    } else {
                        if ((clusterNumber & 1) == 1) {
                            // Odd cluster number
                            sectorData[offset] = (byte) ((sectorData[offset] & 0x0F) | ((value & 0x0F) << 4));
                            sectorData[offset + 1] = (byte) ((value & 0xFF0) >> 4);
                        } else {
                            // Even cluster number
                            sectorData[offset] = (byte) (value & 0xFF);
                            sectorData[offset + 1] = (byte) ((sectorData[offset + 1] & 0xF0) | ((value & 0xF00) >> 8));
                        }
                        
                        // Write back the sector
                        device.write(currentFatSector * bytesPerSector, sectorData);
                    }
                    break;
                case FAT16:
                    FatUtils.writeUInt16(sectorData, offset, (int) value);
                    device.write(currentFatSector * bytesPerSector, sectorData);
                    break;
                case FAT32:
                    // Preserve the reserved bits
                    long currentValue = FatUtils.readUInt32(sectorData, offset) & 0xF0000000L;
                    FatUtils.writeUInt32(sectorData, offset, (value & 0x0FFFFFFF) | currentValue);
                    device.write(currentFatSector * bytesPerSector, sectorData);
                    break;
            }
        }
    }
    
    /**
     * Gets the end-of-chain marker for the current FAT type.
     *
     * @return The EOF marker
     */
    public long getEndOfChainMarker() {
        switch (bootSector.getFatType()) {
            case FAT12:
                return FAT12_EOF_MARKER;
            case FAT16:
                return FAT16_EOF_MARKER;
            case FAT32:
                return FAT32_EOF_MARKER;
            default:
                return 0; // Should never happen
        }
    }
    
    /**
     * Gets the bad cluster marker for the current FAT type.
     *
     * @return The bad cluster marker
     */
    public long getBadClusterMarker() {
        switch (bootSector.getFatType()) {
            case FAT12:
                return FAT12_BAD_CLUSTER;
            case FAT16:
                return FAT16_BAD_CLUSTER;
            case FAT32:
                return FAT32_BAD_CLUSTER;
            default:
                return 0; // Should never happen
        }
    }
    
    /**
     * Checks if a cluster is the end of a chain.
     *
     * @param clusterValue The value of the cluster entry
     * @return True if it's an EOF marker
     */
    public boolean isEndOfChain(long clusterValue) {
        switch (bootSector.getFatType()) {
            case FAT12:
                return clusterValue >= 0xFF8 && clusterValue <= 0xFFF;
            case FAT16:
                return clusterValue >= 0xFFF8 && clusterValue <= 0xFFFF;
            case FAT32:
                return clusterValue >= 0x0FFFFFF8 && clusterValue <= 0x0FFFFFFF;
            default:
                return false; // Should never happen
        }
    }
    
    /**
     * Follows a cluster chain and returns all clusters in the chain.
     *
     * @param startCluster The starting cluster of the chain
     * @return A list of all clusters in the chain
     * @throws IOException If the chain cannot be followed
     */
    public List<Long> getClusterChain(long startCluster) throws IOException {
        List<Long> chain = new ArrayList<>();
        long currentCluster = startCluster;
        
        while (currentCluster != 0 && !isEndOfChain(currentCluster) && currentCluster != getBadClusterMarker()) {
            chain.add(currentCluster);
            currentCluster = getClusterEntry(currentCluster);
        }
        
        return chain;
    }
    
    /**
     * Allocates a new cluster and marks it as the end of a chain.
     *
     * @return The allocated cluster number
     * @throws IOException If a cluster cannot be allocated
     */
    public long allocateCluster() throws IOException {
        // Start searching from cluster 2 (the first valid cluster)
        long cluster = 2;
        long totalClusters = bootSector.getTotalSectors() / bootSector.getSectorsPerCluster();
        
        // Search for a free cluster
        while (cluster < totalClusters) {
            long entryValue = getClusterEntry(cluster);
            if (entryValue == 0) {
                // Found a free cluster, mark it as end of chain
                setClusterEntry(cluster, getEndOfChainMarker());
                return cluster;
            }
            cluster++;
        }
        
        throw new IOException("No free clusters available");
    }
    
    /**
     * Frees a cluster chain starting from the given cluster.
     *
     * @param startCluster The first cluster in the chain
     * @throws IOException If the chain cannot be freed
     */
    public void freeClusterChain(long startCluster) throws IOException {
        if (startCluster == 0 || startCluster == getBadClusterMarker() || isEndOfChain(startCluster)) {
            return;
        }
        
        long currentCluster = startCluster;
        
        while (currentCluster != 0 && !isEndOfChain(currentCluster) && currentCluster != getBadClusterMarker()) {
            long nextCluster = getClusterEntry(currentCluster);
            setClusterEntry(currentCluster, 0); // Mark as free
            currentCluster = nextCluster;
        }
    }
} 