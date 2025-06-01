package net.seitter.jfat.core;

/**
 * Represents the type of FAT filesystem.
 */
public enum FatType {
    /**
     * FAT12 - Used for small storage devices (< 16MB)
     */
    FAT12,
    
    /**
     * FAT16 - Used for medium-sized storage devices (16MB - 2GB)
     */
    FAT16,
    
    /**
     * FAT32 - Used for larger storage devices (> 2GB)
     */
    FAT32
} 