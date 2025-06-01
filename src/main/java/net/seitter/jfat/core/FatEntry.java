package net.seitter.jfat.core;

import net.seitter.jfat.util.FatUtils;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

/**
 * Base class for entries in a FAT filesystem (files and directories).
 */
public abstract class FatEntry {
    // FAT directory entry constants
    public static final int ENTRY_SIZE = 32;
    public static final int ATTR_READ_ONLY = 0x01;
    public static final int ATTR_HIDDEN = 0x02;
    public static final int ATTR_SYSTEM = 0x04;
    public static final int ATTR_VOLUME_ID = 0x08;
    public static final int ATTR_DIRECTORY = 0x10;
    public static final int ATTR_ARCHIVE = 0x20;
    
    protected final FatFileSystem fileSystem;
    protected final FatDirectory parent;
    protected final String name;
    protected long firstCluster;
    protected long size;
    protected Date createTime;
    protected Date modifyTime;
    protected Date accessDate;
    protected int attributes;
    protected int entryOffset; // Offset in the parent directory
    
    /**
     * Creates a new entry with the given parameters.
     *
     * @param fileSystem  The filesystem
     * @param parent      The parent directory
     * @param name        The name of the entry
     * @param firstCluster The first cluster of the entry
     * @param size        The size of the entry
     * @param attributes  The attributes of the entry
     * @param createTime  The creation time
     * @param modifyTime  The last modification time
     * @param accessDate  The last access date
     * @param entryOffset The offset in the parent directory
     */
    protected FatEntry(FatFileSystem fileSystem, FatDirectory parent, String name,
                      long firstCluster, long size, int attributes,
                      Date createTime, Date modifyTime, Date accessDate,
                      int entryOffset) {
        this.fileSystem = fileSystem;
        this.parent = parent;
        this.name = name;
        this.firstCluster = firstCluster;
        this.size = size;
        this.attributes = attributes;
        this.createTime = createTime;
        this.modifyTime = modifyTime;
        this.accessDate = accessDate;
        this.entryOffset = entryOffset;
    }
    
    /**
     * Creates an entry from a directory entry.
     *
     * @param fileSystem  The filesystem
     * @param parent      The parent directory
     * @param entryData   The directory entry data (32 bytes)
     * @param entryOffset The offset in the parent directory
     * @return A new FatEntry (either a file or directory)
     */
    public static FatEntry fromDirectoryEntry(FatFileSystem fileSystem, FatDirectory parent,
                                             byte[] entryData, int entryOffset) {
        if (entryData.length < ENTRY_SIZE) {
            throw new IllegalArgumentException("Entry data too short: " + entryData.length + " bytes, expected " + ENTRY_SIZE);
        }
        
        String name = FatUtils.readFatName(entryData);
        int attributes = entryData[11] & 0xFF;
        
        // Get creation time/date
        int createTimeRaw = FatUtils.readUInt16(entryData, 14);
        int createDateRaw = FatUtils.readUInt16(entryData, 16);
        Date createTime = fatDateTimeToJavaDate(createDateRaw, createTimeRaw);
        
        // Get access date
        int accessDateRaw = FatUtils.readUInt16(entryData, 18);
        Date accessDate = fatDateToJavaDate(accessDateRaw);
        
        // Get modification time/date
        int modifyTimeRaw = FatUtils.readUInt16(entryData, 22);
        int modifyDateRaw = FatUtils.readUInt16(entryData, 24);
        Date modifyTime = fatDateTimeToJavaDate(modifyDateRaw, modifyTimeRaw);
        
        // Get first cluster (high word in FAT32, 0 in FAT12/16)
        int firstClusterHigh = 0;
        if (fileSystem.getBootSector().getFatType() == FatType.FAT32) {
            firstClusterHigh = FatUtils.readUInt16(entryData, 20);
        }
        int firstClusterLow = FatUtils.readUInt16(entryData, 26);
        long firstCluster = ((long) firstClusterHigh << 16) | firstClusterLow;
        
        // Get file size
        long size = FatUtils.readUInt32(entryData, 28);
        
        // Create the appropriate entry type
        if ((attributes & ATTR_DIRECTORY) != 0) {
            return new FatDirectory(fileSystem, parent, name, firstCluster, attributes,
                                   createTime, modifyTime, accessDate, entryOffset);
        } else {
            return new FatFile(fileSystem, parent, name, firstCluster, size, attributes,
                              createTime, modifyTime, accessDate, entryOffset);
        }
    }
    
    /**
     * Writes this entry to its directory entry.
     *
     * @throws IOException If the entry cannot be written
     */
    public void updateDirectoryEntry() throws IOException {
        if (parent == null) {
            // Can't update the root directory entry
            return;
        }
        
        // Find the directory data
        byte[] dirData = parent.readDirectoryData();
        
        // Update the entry data
        byte[] nameBytes = FatUtils.writeFatName(name);
        System.arraycopy(nameBytes, 0, dirData, entryOffset, 11);
        
        // Set attributes
        dirData[entryOffset + 11] = (byte) attributes;
        
        // Set times
        Calendar cal = Calendar.getInstance();
        
        // Create time
        cal.setTime(createTime);
        int createDateRaw = FatUtils.encodeFatDate(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        int createTimeRaw = FatUtils.encodeFatTime(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND));
        FatUtils.writeUInt16(dirData, entryOffset + 14, createTimeRaw);
        FatUtils.writeUInt16(dirData, entryOffset + 16, createDateRaw);
        
        // Access date
        cal.setTime(accessDate);
        int accessDateRaw = FatUtils.encodeFatDate(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        FatUtils.writeUInt16(dirData, entryOffset + 18, accessDateRaw);
        
        // First cluster high (FAT32 only)
        if (fileSystem.getBootSector().getFatType() == FatType.FAT32) {
            FatUtils.writeUInt16(dirData, entryOffset + 20, (int) (firstCluster >> 16));
        }
        
        // Modify time
        cal.setTime(modifyTime);
        int modifyDateRaw = FatUtils.encodeFatDate(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        int modifyTimeRaw = FatUtils.encodeFatTime(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND));
        FatUtils.writeUInt16(dirData, entryOffset + 22, modifyTimeRaw);
        FatUtils.writeUInt16(dirData, entryOffset + 24, modifyDateRaw);
        
        // First cluster low
        FatUtils.writeUInt16(dirData, entryOffset + 26, (int) (firstCluster & 0xFFFF));
        
        // File size
        FatUtils.writeUInt32(dirData, entryOffset + 28, size);
        
        // Write the directory data back
        parent.writeDirectoryData(dirData);
    }
    
    /**
     * Converts a FAT date and time to a Java Date.
     *
     * @param fatDate The FAT date
     * @param fatTime The FAT time
     * @return A Java Date
     */
    private static Date fatDateTimeToJavaDate(int fatDate, int fatTime) {
        int[] date = FatUtils.decodeFatDate(fatDate);
        int[] time = FatUtils.decodeFatTime(fatTime);
        
        Calendar cal = Calendar.getInstance();
        cal.set(date[0], date[1] - 1, date[2], time[0], time[1], time[2]);
        cal.set(Calendar.MILLISECOND, 0);
        
        return cal.getTime();
    }
    
    /**
     * Converts a FAT date to a Java Date.
     *
     * @param fatDate The FAT date
     * @return A Java Date
     */
    private static Date fatDateToJavaDate(int fatDate) {
        int[] date = FatUtils.decodeFatDate(fatDate);
        
        Calendar cal = Calendar.getInstance();
        cal.set(date[0], date[1] - 1, date[2], 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        return cal.getTime();
    }
    
    /**
     * Gets the name of this entry.
     *
     * @return The name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the size of this entry.
     *
     * @return The size in bytes
     */
    public long getSize() {
        return size;
    }
    
    /**
     * Gets the first cluster of this entry.
     *
     * @return The first cluster
     */
    public long getFirstCluster() {
        return firstCluster;
    }
    
    /**
     * Gets the creation time of this entry.
     *
     * @return The creation time
     */
    public Date getCreateTime() {
        return createTime;
    }
    
    /**
     * Gets the last modification time of this entry.
     *
     * @return The modification time
     */
    public Date getModifyTime() {
        return modifyTime;
    }
    
    /**
     * Gets the last access date of this entry.
     *
     * @return The access date
     */
    public Date getAccessDate() {
        return accessDate;
    }
    
    /**
     * Gets the attributes of this entry.
     *
     * @return The attributes
     */
    public int getAttributes() {
        return attributes;
    }
    
    /**
     * Gets the parent directory of this entry.
     *
     * @return The parent directory
     */
    public FatDirectory getParent() {
        return parent;
    }
    
    /**
     * Checks if this entry is a directory.
     *
     * @return True if this is a directory
     */
    public boolean isDirectory() {
        return (attributes & ATTR_DIRECTORY) != 0;
    }
    
    /**
     * Checks if this entry is read-only.
     *
     * @return True if this is read-only
     */
    public boolean isReadOnly() {
        return (attributes & ATTR_READ_ONLY) != 0;
    }
    
    /**
     * Checks if this entry is hidden.
     *
     * @return True if this is hidden
     */
    public boolean isHidden() {
        return (attributes & ATTR_HIDDEN) != 0;
    }
    
    /**
     * Checks if this entry is a system file.
     *
     * @return True if this is a system file
     */
    public boolean isSystem() {
        return (attributes & ATTR_SYSTEM) != 0;
    }
    
    /**
     * Checks if this entry has the archive attribute.
     *
     * @return True if this has the archive attribute
     */
    public boolean isArchive() {
        return (attributes & ATTR_ARCHIVE) != 0;
    }
    
    /**
     * Deletes this entry from the filesystem.
     *
     * @throws IOException If the entry cannot be deleted
     */
    public abstract void delete() throws IOException;
} 