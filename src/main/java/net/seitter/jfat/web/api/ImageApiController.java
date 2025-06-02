package net.seitter.jfat.web.api;

import net.seitter.jfat.core.FatType;
import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.BootSector;
import net.seitter.jfat.util.DiskImageCreator;
import net.seitter.jfat.io.DeviceAccess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for disk image management
 */
public class ImageApiController {
    
    private static final String IMAGES_DIR = "images";
    
    public ImageApiController() {
        // Ensure images directory exists
        File imagesDir = new File(IMAGES_DIR);
        if (!imagesDir.exists()) {
            imagesDir.mkdirs();
        }
    }
    
    public void listImages(Object ctx) {
        try {
            List<ImageInfo> images = new ArrayList<>();
            File imagesDir = new File(IMAGES_DIR);
            
            if (imagesDir.exists() && imagesDir.isDirectory()) {
                File[] files = imagesDir.listFiles((dir, name) -> name.endsWith(".img"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            images.add(createImageInfo(file));
                        } catch (Exception e) {
                            System.err.println("Error reading image info for " + file.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
            
            // Using reflection to call json method since we can't import io.javalin yet
            ctx.getClass().getMethod("json", Object.class).invoke(ctx, images);
        } catch (Exception e) {
            handleError(ctx, "Failed to list images", e);
        }
    }
    
    public void createImage(Object ctx) {
        try {
            // Parse request body manually for now
            String body = (String) ctx.getClass().getMethod("body").invoke(ctx);
            CreateImageRequest request = parseCreateImageRequest(body);
            
            String filename = request.name + ".img";
            String imagePath = IMAGES_DIR + "/" + filename;
            
            // Check if image already exists
            if (new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 409);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image already exists", "message", "An image with this name already exists"));
                return;
            }
            
            // Create the image
            FatType fatType = FatType.valueOf(request.fatType.toUpperCase());
            DiskImageCreator.createDiskImage(imagePath, fatType, request.sizeMB);
            
            // Return created image info
            ImageInfo imageInfo = createImageInfo(new File(imagePath));
            ctx.getClass().getMethod("status", int.class).invoke(ctx, 201);
            ctx.getClass().getMethod("json", Object.class).invoke(ctx, imageInfo);
            
        } catch (Exception e) {
            handleError(ctx, "Failed to create image", e);
        }
    }
    
    public void deleteImage(Object ctx) {
        try {
            String name = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "name");
            String imagePath = IMAGES_DIR + "/" + name + ".img";
            
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found with name: " + name));
                return;
            }
            
            if (imageFile.delete()) {
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("message", "Image deleted successfully"));
            } else {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 500);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Failed to delete image", "message", "Could not delete the image file"));
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to delete image", e);
        }
    }
    
    public void getImageInfo(Object ctx) {
        try {
            String name = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "name");
            String imagePath = IMAGES_DIR + "/" + name + ".img";
            
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found with name: " + name));
                return;
            }
            
            ImageInfo imageInfo = createImageInfo(imageFile);
            ctx.getClass().getMethod("json", Object.class).invoke(ctx, imageInfo);
            
        } catch (Exception e) {
            handleError(ctx, "Failed to get image info", e);
        }
    }
    
    private ImageInfo createImageInfo(File file) throws IOException {
        ImageInfo info = new ImageInfo();
        info.name = file.getName().replace(".img", "");
        info.filename = file.getName();
        info.sizeMB = (int) (file.length() / (1024 * 1024));
        info.created = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(file.lastModified()), 
            ZoneId.systemDefault()
        );
        
        // Try to read FAT type and cluster info
        try (DeviceAccess device = new DeviceAccess(file.getAbsolutePath());
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            BootSector bootSector = fs.getBootSector();
            info.fatType = bootSector.getFatType().toString();
            info.clusterSize = bootSector.getClusterSizeBytes();
            info.totalSectors = bootSector.getTotalSectors();
            info.sectorsPerCluster = bootSector.getSectorsPerCluster();
            info.bytesPerSector = bootSector.getBytesPerSector();
            
        } catch (Exception e) {
            info.fatType = "Unknown";
            info.clusterSize = 0;
        }
        
        return info;
    }
    
    private CreateImageRequest parseCreateImageRequest(String json) {
        // Simple JSON parsing for now - in a real implementation, use Jackson
        CreateImageRequest request = new CreateImageRequest();
        
        // Extract values using simple string operations
        json = json.trim().replaceAll("[{}\"\\s]", "");
        String[] pairs = json.split(",");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];
                
                switch (key) {
                    case "name":
                        request.name = value;
                        break;
                    case "fatType":
                        request.fatType = value;
                        break;
                    case "sizeMB":
                        request.sizeMB = Integer.parseInt(value);
                        break;
                }
            }
        }
        
        return request;
    }
    
    private void handleError(Object ctx, String message, Exception e) {
        try {
            System.err.println(message + ": " + e.getMessage());
            e.printStackTrace();
            ctx.getClass().getMethod("status", int.class).invoke(ctx, 500);
            ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                Map.of("error", message, "message", e.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    // DTOs
    public static class ImageInfo {
        public String name;
        public String filename;
        public int sizeMB;
        public LocalDateTime created;
        public String fatType;
        public int clusterSize;
        public long totalSectors;
        public int sectorsPerCluster;
        public int bytesPerSector;
    }
    
    public static class CreateImageRequest {
        public String name;
        public String fatType;
        public int sizeMB;
    }
} 