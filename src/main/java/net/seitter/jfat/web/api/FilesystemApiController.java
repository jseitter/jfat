package net.seitter.jfat.web.api;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.io.DeviceAccess;
import io.javalin.http.Context;

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
    
    public void listDirectory(Context ctx) {
        try {
            String imageName = ctx.pathParam("image");
            String path = extractPath(ctx);
            
            System.out.println("🔍 Backend listDirectory called:");
            System.out.println("  - imageName: " + imageName);
            System.out.println("  - extracted path: " + path);
            System.out.println("  - full request path: " + ctx.path());
            System.out.println("  - request method: " + ctx.method());
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            System.out.println("  - looking for image file: " + imagePath);
            
            if (!new File(imagePath).exists()) {
                System.err.println("❌ Image file not found: " + imagePath);
                ctx.status(404);
                ctx.json(Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            System.out.println("✅ Image file found, mounting filesystem...");
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null) {
                    System.err.println("❌ Path not found in filesystem: " + path);
                    ctx.status(404);
                    ctx.json(Map.of("error", "Path not found", "message", "Path not found: " + path));
                    return;
                }
                
                if (!entry.isDirectory()) {
                    System.err.println("❌ Path is not a directory: " + path);
                    ctx.status(400);
                    ctx.json(Map.of("error", "Not a directory", "message", "Path is not a directory: " + path));
                    return;
                }
                
                FatDirectory directory = (FatDirectory) entry;
                List<FileSystemEntry> entries = new ArrayList<>();
                
                System.out.println("📁 Listing directory contents...");
                for (FatEntry child : directory.list()) {
                    FileSystemEntry fsEntry = createFileSystemEntry(child, path);
                    entries.add(fsEntry);
                    System.out.println("  - " + fsEntry.type + ": " + fsEntry.name + " (path: " + fsEntry.path + ")");
                }
                
                DirectoryListing listing = new DirectoryListing();
                listing.path = path;
                listing.entries = entries;
                
                System.out.println("✅ Returning " + entries.size() + " entries for path: " + path);
                ctx.json(listing);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error in listDirectory: " + e.getMessage());
            e.printStackTrace();
            handleError(ctx, "Failed to list directory", e);
        }
    }
    
    public void createEntry(Context ctx) {
        try {
            String imageName = ctx.pathParam("image");
            String path = extractPath(ctx);
            String body = ctx.body();
            
            CreateEntryRequest request = parseCreateEntryRequest(body);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.status(404);
                ctx.json(Map.of("error", "Image not found", "message", "No image found: " + imageName));
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
                        ctx.status(400);
                        ctx.json(Map.of("error", "Invalid parent directory", "message", "Parent directory not found: " + parentPath));
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
                        ctx.status(400);
                        ctx.json(Map.of("error", "Invalid parent directory", "message", "Parent directory not found: " + parentPath));
                        return;
                    }
                    
                    FatDirectory parentDir = (FatDirectory) parentEntry;
                    entry = parentDir.createFile(fileName);
                    if (request.content != null && !request.content.isEmpty()) {
                        ((FatFile) entry).write(request.content.getBytes());
                    }
                }
                
                FileSystemEntry result = createFileSystemEntry(entry, path);
                ctx.status(201);
                ctx.json(result);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to create entry", e);
        }
    }
    
    public void updateFile(Context ctx) {
        try {
            String imageName = ctx.pathParam("image");
            String path = extractPath(ctx);
            String content = ctx.body();
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.status(404);
                ctx.json(Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null) {
                    ctx.status(404);
                    ctx.json(Map.of("error", "File not found", "message", "File not found: " + path));
                    return;
                }
                
                if (entry.isDirectory()) {
                    ctx.status(400);
                    ctx.json(Map.of("error", "Cannot update directory", "message", "Path is a directory: " + path));
                    return;
                }
                
                FatFile file = (FatFile) entry;
                file.write(content.getBytes());
                
                FileSystemEntry result = createFileSystemEntry(file, getParentPath(path));
                ctx.json(result);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to update file", e);
        }
    }
    
    public void deleteEntry(Context ctx) {
        try {
            String imageName = ctx.pathParam("image");
            String path = extractPath(ctx);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.status(404);
                ctx.json(Map.of("error", "Image not found", "message", "No image found: " + imageName));
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null) {
                    ctx.status(404);
                    ctx.json(Map.of("error", "Entry not found", "message", "Entry not found: " + path));
                    return;
                }
                
                entry.delete();
                ctx.json(Map.of("message", "Entry deleted successfully"));
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to delete entry", e);
        }
    }
    
    public void uploadFile(Context ctx) {
        try {
            // File upload implementation would go here
            // For now, return not implemented
            ctx.status(501);
            ctx.json(Map.of("error", "Not implemented", "message", "File upload not yet implemented"));
        } catch (Exception e) {
            handleError(ctx, "Failed to upload file", e);
        }
    }
    
    public void downloadFile(Context ctx) {
        try {
            String imageName = ctx.pathParam("image");
            String path = extractPath(ctx);
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                ctx.status(404);
                ctx.result("Image not found");
                return;
            }
            
            try (DeviceAccess device = new DeviceAccess(imagePath);
                 FatFileSystem fs = FatFileSystem.mount(device)) {
                
                FatEntry entry = getEntryByPath(fs, path);
                if (entry == null || entry.isDirectory()) {
                    ctx.status(404);
                    ctx.result("File not found");
                    return;
                }
                
                FatFile file = (FatFile) entry;
                byte[] content = file.readAllBytes();
                
                String filename = entry.getName();
                ctx.header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                ctx.contentType("application/octet-stream");
                ctx.result(content);
            }
            
        } catch (Exception e) {
            handleError(ctx, "Failed to download file", e);
        }
    }
    
    private String extractPath(Context ctx) {
        // In Javalin 5.6.3, wildcard paths are captured as path parameters
        // The route is defined as /api/fs/{image}/** so we need to get the wildcard part
        String pathInfo = ctx.path();
        String imageName = ctx.pathParam("image");
        
        System.out.println("🔧 extractPath debug:");
        System.out.println("  - full pathInfo: " + pathInfo);
        System.out.println("  - imageName: " + imageName);
        System.out.println("  - path params: " + ctx.pathParamMap());
        System.out.println("  - query params: " + ctx.queryParamMap());
        
        // Try to get the wildcard parameter directly
        try {
            String wildcardParam = ctx.pathParam("*");
            if (wildcardParam != null && !wildcardParam.isEmpty()) {
                String result = "/" + wildcardParam;
                System.out.println("  - found wildcard param: " + wildcardParam + " -> " + result);
                return result;
            }
        } catch (Exception e) {
            System.out.println("  - no wildcard param available");
        }
        
        // Handle both /api/fs/{image} and /api/fs/{image}/ patterns
        String basePathWithSlash = "/api/fs/" + imageName + "/";
        String basePathWithoutSlash = "/api/fs/" + imageName;
        
        if (pathInfo.startsWith(basePathWithSlash)) {
            String remainingPath = pathInfo.substring(basePathWithSlash.length());
            String result = remainingPath.isEmpty() ? "/" : "/" + remainingPath;
            System.out.println("  - matched with slash, extracted path: " + result);
            return result;
        } else if (pathInfo.equals(basePathWithoutSlash)) {
            System.out.println("  - matched without slash, returning root path: /");
            return "/";
        }
        
        System.out.println("  - no match, defaulting to root path: /");
        return "/";
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
    
    private void handleError(Context ctx, String message, Exception e) {
        try {
            System.err.println(message + ": " + e.getMessage());
            e.printStackTrace();
            ctx.status(500);
            ctx.json(Map.of("error", message, "message", e.getMessage()));
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