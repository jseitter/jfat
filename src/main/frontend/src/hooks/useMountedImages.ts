import { useState, useEffect, useCallback } from 'react';
import { MountedImage, ImageInfo } from '../types';

const MOUNTED_IMAGES_KEY = 'jfat_mounted_images';

export function useMountedImages() {
  const [mountedImages, setMountedImages] = useState<MountedImage[]>([]);

  // Load mounted images from localStorage on init
  useEffect(() => {
    const stored = localStorage.getItem(MOUNTED_IMAGES_KEY);
    if (stored) {
      try {
        setMountedImages(JSON.parse(stored));
      } catch (error) {
        console.warn('Failed to parse mounted images from storage:', error);
        localStorage.removeItem(MOUNTED_IMAGES_KEY);
      }
    }
  }, []);

  // Save to localStorage whenever mounted images change
  useEffect(() => {
    localStorage.setItem(MOUNTED_IMAGES_KEY, JSON.stringify(mountedImages));
  }, [mountedImages]);

  const mountImage = useCallback((imageInfo: ImageInfo) => {
    const mountedImage: MountedImage = {
      name: imageInfo.name,
      fatType: imageInfo.fatType,
      sizeMB: imageInfo.sizeMB,
      mountedAt: new Date().toISOString(),
    };

    setMountedImages(prev => {
      // Remove if already mounted, then add to front
      const filtered = prev.filter(img => img.name !== imageInfo.name);
      return [mountedImage, ...filtered];
    });
  }, []);

  const unmountImage = useCallback((imageName: string) => {
    setMountedImages(prev => prev.filter(img => img.name !== imageName));
  }, []);

  const unmountAll = useCallback(() => {
    setMountedImages([]);
  }, []);

  const isMounted = useCallback((imageName: string) => {
    return mountedImages.some(img => img.name === imageName);
  }, [mountedImages]);

  const getMountedImage = useCallback((imageName: string) => {
    return mountedImages.find(img => img.name === imageName);
  }, [mountedImages]);

  return {
    mountedImages,
    mountImage,
    unmountImage,
    unmountAll,
    isMounted,
    getMountedImage,
  };
} 