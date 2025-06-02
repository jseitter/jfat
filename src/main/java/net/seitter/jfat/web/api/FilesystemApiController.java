package net.seitter.jfat.web.api;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.io.DeviceAccess;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for filesystem operations
 */
public class FilesystemApiController {
    
    private static final String IMAGES_DIR = "images";
    
    public void listDirectory(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String path = extractPath(ctx);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null) {
                    ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                    ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                        Map.of("error", "Path not found", "message", "Path not found: " + path));
                    return;
                }
                
                if (!entry.isDirectory()) {
                    ctx.getClass().getMethod("status", int.class).invoke(ctx, 400);
                    ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                        Map.of("error", "Not a directory", "message", "Path is not a directory: " + path));
                    return;
                }
                
                FatDirectory directory = (FatDirectory) entry;
                List<FileSystemEntry> entries = new ArrayList<>();
                
                for (FatEntry child : directory.list()) {
                    entries.add(createFileSystemEntry(child, path));
                }
                
                DirectoryListing listing = new DirectoryListing();
                listing.path = path;
                listing.entries = entries;
                
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, listing);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to list directory", e);
        }
    }
    
    public void createEntry(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String path = extractPath(ctx);
            String body = (String) ctx.getClass().getMethod("body").invoke(ctx);
            
            CreateEntryRequest request = parseCreateEntryRequest(body);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry;
                if ("directory".equals(request.type)) {
                    // Create directory through parent directory
                    String parentPath = getParentPath(path + "/" + request.name);
                    String dirName = request.name;
                    
                    FatEntry parentEntry = getEntryByPath(fs, parentPath);
                    if (parentEntry == null || !parentEntry.isDirectory()) {
                        ctx.getClass().getMethod("status", int.class).invoke(ctx, 400);
                        ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                            Map.of("error", "Invalid parent directory", "message", "Parent directory not found: " + parentPath));
                        return;
                    }
                    
                    FatDirectory parentDir = (FatDirectory) parentEntry;
                    entry = parentDir.createDirectory(dirName);
                } else {
                    // Create file through parent directory
                    String parentPath = getParentPath(path + "/" + request.name);
                    String fileName = request.name;
                    
                    FatEntry parentEntry = getEntryByPath(fs, parentPath);
                    if (parentEntry == null || !parentEntry.isDirectory()) {
                        ctx.getClass().getMethod("status", int.class).invoke(ctx, 400);
                        ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                            Map.of("error", "Invalid parent directory", "message", "Parent directory not found: " + parentPath));
                        return;
                    }
                    
                    FatDirectory parentDir = (FatDirectory) parentEntry;
                    entry = parentDir.createFile(fileName);
                    if (request.content != null && !request.content.isEmpty()) {
                        ((FatFile) entry).write(request.content.getBytes());
                    }
                }
                
                FileSystemEntry result = createFileSystemEntry(entry, path);
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 201);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, result);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to create entry", e);
        }
    }
    
    public void updateFile(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String path = extractPath(ctx);
            String content = (String) ctx.getClass().getMethod("body").invoke(ctx);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null) {
                    ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                    ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                        Map.of("error", "File not found", "message", "File not found: " + path));
                    return;
                }
                
                if (entry.isDirectory()) {
                    ctx.getClass().getMethod("status", int.class).invoke(ctx, 400);
                    ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                        Map.of("error", "Cannot update directory", "message", "Path is a directory: " + path));
                    return;
                }
                
                FatFile file = (FatFile) entry;
                file.write(content.getBytes());
                
                FileSystemEntry result = createFileSystemEntry(file, getParentPath(path));
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, result);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to update file", e);
        }
    }
    
    public void deleteEntry(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String path = extractPath(ctx);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null) {
                    ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                    ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                        Map.of("error", "Entry not found", "message", "Entry not found: " + path));
                    return;
                }
                
                entry.delete();
                ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                    Map.of("message", "Entry deleted successfully"));
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to delete entry", e);
        }
    }
    
    public void uploadFile(Object ctx) {
        try {
            // File upload implementation would go here
            // For now, return not implemented
            ctx.getClass().getMethod("status", int.class).invoke(ctx, 501);
            ctx.getClass().getMethod("json", Object.class).invoke(ctx, 
                Map.of("error", "Not implemented", "message", "File upload not yet implemented"));
        } catch (Exception e) {
            handleError(ctx, "Failed to upload file", e);
        }
    }
    
    public void downloadFile(Object ctx) {
        try {
            String imageName = (String) ctx.getClass().getMethod("pathParam", String.class).invoke(ctx, "image");
            String path = extractPath(ctx);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                ctx.getClass().getMethod("result", String.class).invoke(ctx, "Image not found");
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null || entry.isDirectory()) {
                    ctx.getClass().getMethod("status", int.class).invoke(ctx, 404);
                    ctx.getClass().getMethod("result", String.class).invoke(ctx, "File not found");
                    return;
                }
                
                FatFile file = (FatFile) entry;
                byte[] content = file.readAllBytes();
                
                String filename = entry.getName();
                ctx.getClass().getMethod("header", String.class, String.class)
                   .invoke(ctx, "Content-Disposition", "attachment; filename=\"" + filename + "\"");
                ctx.getClass().getMethod("contentType", String.class).invoke(ctx, "application/octet-stream");
                ctx.getClass().getMethod("result", byte[].class).invoke(ctx, content);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to download file", e);
        }
    }
    
    private String extractPath(Object ctx) throws Exception {
        String splat = (String) ctx.getClass().getMethod("splat").invoke(ctx);
        return splat != null && !splat.isEmpty() ? "/" + splat : "/";
    }
    
    private String getParentPath(String path) {
        if (path.equals("/")) return "/";
        int lastSlash = path.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : path.substring(0, lastSlash);
    }
    
    private FatEntry getEntryByPath(FatFileSystem fs, String path) throws IOException {
        if (path.equals("/")) {
            return fs.getRootDirectory();
        }
        
        String[] components = path.split("/");
        FatDirectory currentDir = fs.getRootDirectory();
        
        for (String component : components) {
            if (component.isEmpty()) continue;
            
            FatEntry entry = currentDir.getEntry(component);
            if (entry == null) {
                return null;
            }
            
            if (entry.isDirectory()) {
                currentDir = (FatDirectory) entry;
            } else {
                return entry;
            }
        }
        
        return currentDir;
    }
    
    private FileSystemEntry createFileSystemEntry(FatEntry entry, String parentPath) {
        FileSystemEntry fsEntry = new FileSystemEntry();
        fsEntry.name = entry.getName();
        fsEntry.type = entry.isDirectory() ? "directory" : "file";
        fsEntry.size = entry.isDirectory() ? 0 : entry.getSize();
        fsEntry.path = parentPath.equals("/") ? "/" + entry.getName() : parentPath + "/" + entry.getName();
        
        if (entry.getModifyTime() != null) {
            fsEntry.modified = LocalDateTime.ofInstant(
                entry.getModifyTime().toInstant(), 
                ZoneId.systemDefault()
            );
        }
        
        return fsEntry;
    }
    
    private CreateEntryRequest parseCreateEntryRequest(String json) {
        CreateEntryRequest request = new CreateEntryRequest();
        
        // Simple JSON parsing
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
                    case "type":
                        request.type = value;
                        break;
                    case "content":
                        request.content = value;
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
    public static class DirectoryListing {
        public String path;
        public List<FileSystemEntry> entries;
    }
    
    public static class FileSystemEntry {
        public String name;
        public String type; // "file" or "directory"
        public long size;
        public String path;
        public LocalDateTime modified;
    }
    
    public static class CreateEntryRequest {
        public String name;
        public String type; // "file" or "directory"
        public String content; // For files
    }
} 