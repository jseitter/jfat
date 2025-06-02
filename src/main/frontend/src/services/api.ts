// API service for JFAT backend communication

const API_BASE_URL = '/api';

// Helper function to handle API responses
async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
  }
  
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return response.json();
  }
  
  return response.text() as unknown as T;
}

// Image Management API
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

export const imageApi = {
  async listImages(): Promise<ImageInfo[]> {
    const response = await fetch(`${API_BASE_URL}/images`);
    return handleResponse<ImageInfo[]>(response);
  },

  async createImage(request: CreateImageRequest): Promise<ImageInfo> {
    const response = await fetch(`${API_BASE_URL}/images`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });
    return handleResponse<ImageInfo>(response);
  },

  async deleteImage(name: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/images/${name}`, {
      method: 'DELETE',
    });
    await handleResponse<void>(response);
  },

  async getImageInfo(name: string): Promise<ImageInfo> {
    const response = await fetch(`${API_BASE_URL}/images/${name}/info`);
    return handleResponse<ImageInfo>(response);
  },
};

// Filesystem API
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

export const filesystemApi = {
  async listDirectory(imageName: string, path: string = '/'): Promise<DirectoryListing> {
    const encodedPath = encodeURIComponent(path.substring(1)); // Remove leading slash
    const url = encodedPath ? `${API_BASE_URL}/fs/${imageName}/${encodedPath}` : `${API_BASE_URL}/fs/${imageName}`;
    const response = await fetch(url);
    return handleResponse<DirectoryListing>(response);
  },

  async createEntry(imageName: string, path: string, request: CreateEntryRequest): Promise<FileSystemEntry> {
    const encodedPath = encodeURIComponent(path.substring(1));
    const url = encodedPath ? `${API_BASE_URL}/fs/${imageName}/${encodedPath}` : `${API_BASE_URL}/fs/${imageName}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });
    return handleResponse<FileSystemEntry>(response);
  },

  async updateFile(imageName: string, path: string, content: string): Promise<FileSystemEntry> {
    const encodedPath = encodeURIComponent(path.substring(1));
    const response = await fetch(`${API_BASE_URL}/fs/${imageName}/${encodedPath}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'text/plain',
      },
      body: content,
    });
    return handleResponse<FileSystemEntry>(response);
  },

  async deleteEntry(imageName: string, path: string): Promise<void> {
    const encodedPath = encodeURIComponent(path.substring(1));
    const response = await fetch(`${API_BASE_URL}/fs/${imageName}/${encodedPath}`, {
      method: 'DELETE',
    });
    await handleResponse<void>(response);
  },

  async downloadFile(imageName: string, path: string): Promise<Blob> {
    const encodedPath = encodeURIComponent(path.substring(1));
    const response = await fetch(`${API_BASE_URL}/fs/${imageName}/download/${encodedPath}`);
    if (!response.ok) {
      throw new Error(`Failed to download file: ${response.statusText}`);
    }
    return response.blob();
  },
};

// Graph and Analysis API
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

export const graphApi = {
  async getGraph(imageName: string): Promise<GraphResponse> {
    const response = await fetch(`${API_BASE_URL}/graph/${imageName}`);
    return handleResponse<GraphResponse>(response);
  },

  async getExpertGraph(imageName: string): Promise<GraphResponse> {
    const response = await fetch(`${API_BASE_URL}/graph/${imageName}/expert`);
    return handleResponse<GraphResponse>(response);
  },

  async getAnalysis(imageName: string): Promise<AnalysisResponse> {
    const response = await fetch(`${API_BASE_URL}/analysis/${imageName}`);
    return handleResponse<AnalysisResponse>(response);
  },
};

// Health check
export const healthApi = {
  async check(): Promise<{ status: string; timestamp: number }> {
    const response = await fetch(`${API_BASE_URL}/health`);
    return handleResponse<{ status: string; timestamp: number }>(response);
  },
}; 