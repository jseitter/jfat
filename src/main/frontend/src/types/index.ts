// Common types for JFAT frontend application

export interface ImageInfo {
  name: string;
  filename: string;
  sizeMB: number;
  created: string;
  fatType: string;
  clusterSize: number;
  totalSectors: number;
  sectorsPerCluster: number;
  bytesPerSector: number;
}

export interface CreateImageRequest {
  name: string;
  fatType: 'FAT12' | 'FAT16' | 'FAT32';
  sizeMB: number;
}

export interface MountedImage {
  name: string;
  fatType: string;
  sizeMB: number;
  mountedAt: string;
}

export interface FileSystemEntry {
  name: string;
  type: 'file' | 'directory';
  size: number;
  path: string;
  modified?: string;
}

export interface DirectoryListing {
  path: string;
  entries: FileSystemEntry[];
}

export interface CreateEntryRequest {
  name: string;
  type: 'file' | 'directory';
  content?: string;
}

export interface ImageSelectorProps {
  selectedImage?: string;
  onImageSelect: (imageName: string | null) => void;
  className?: string;
}

// Graph and Analysis types
export interface GraphResponse {
  imageName: string;
  format: string;
  content: string;
  expertMode: boolean;
}

export interface AnalysisResponse {
  imageName: string;
  fatType: string;
  totalSectors: number;
  bytesPerSector: number;
  sectorsPerCluster: number;
  clusterSize: number;
  totalSize: number;
  totalClusters: number;
  usedClusters: number;
  freeClusters: number;
  clusterUtilization: number;
  recommendedClusterSize: number;
  isOptimalClusterSize: boolean;
  clusterSizeInfo: string;
} 