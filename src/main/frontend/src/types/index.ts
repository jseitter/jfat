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
  
  // Fragmentation summary fields
  fragmentationRatio: number;
  fragmentationImpactScore: number;
  defragmentationRecommended: boolean;
  worstFragmentedFileCount: number;
  freeSpaceFragmentationRatio: number;
}

// Fragmentation Analysis Types
export interface FragmentationAnalysis {
  fileFragmentation: FileFragmentationData;
  freeSpaceFragmentation: FreeSpaceFragmentationData;
  performanceImpact: PerformanceImpactData;
  recommendations: FragmentationRecommendation[];
}

export interface FileFragmentationData {
  fragmentationRatio: number;
  averageFragmentsPerFile: number;
  maxFileFragments: number;
  sequentialClusterRatio: number;
  averageClusterGap: number;
  worstFiles: FileFragmentationInfo[];
}

export interface FreeSpaceFragmentationData {
  freeSpaceFragmentationRatio: number;
  largestContiguousFreeBlock: number;
  averageFreeBlockSize: number;
  freeBlockCount: number;
  freeSpaceMap: FreeSpaceBlock[];
}

export interface PerformanceImpactData {
  seekDistanceScore: number;
  fragmentationImpactScore: number;
  readEfficiencyScore: number;
}

export interface FileFragmentationInfo {
  path: string;
  name: string;
  size: number;
  clusterChain: number[];
  fragmentCount: number;
  averageGap: number;
  severity: 'NONE' | 'LIGHT' | 'MODERATE' | 'HEAVY' | 'SEVERE';
}

export interface FragmentationRecommendation {
  type: 'DEFRAGMENT_FILES' | 'CONSOLIDATE_FREE_SPACE' | 'FULL_DEFRAGMENTATION' | 'OPTIMIZE_ALLOCATION';
  description: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  affectedFiles: string[];
}

export interface FreeSpaceBlock {
  startCluster: number;
  size: number;
} 