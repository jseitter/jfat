package net.seitter.jfat.web.websocket;

import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatEntry;
import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.analysis.FragmentationAnalyzer;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket handler for real-time communication with web clients
 * Includes interactive shell functionality
 */
public class FatWebSocketHandler {
    
    private static final String IMAGES_DIR = "images";
    private final Map<Object, String> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<Object, ShellSession> shellSessions = new ConcurrentHashMap<>();
    
    // Shell session state
    private static class ShellSession {
        String imageName;
        String currentPath = "/";
        FatFileSystem fileSystem;
        DeviceAccess device;
        FatDirectory currentDirectory;
        
        public void close() {
            try {
                if (fileSystem != null) {
                    fileSystem.close();
                }
                if (device != null) {
                    device.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing shell session: " + e.getMessage());
            }
        }
    }
    
    public void onConnect(Object session) {
        try {
            System.out.println("WebSocket connected: " + getSessionId(session));
            sendMessage(session, new WebSocketMessage("connected", "WebSocket connection established"));
        } catch (Exception e) {
            System.err.println("Error on WebSocket connect: " + e.getMessage());
        }
    }
    
    public void onMessage(Object session, String message) {
        try {
            System.out.println("WebSocket message received: " + message);
            
            // Parse simple JSON message
            WebSocketMessage wsMessage = parseMessage(message);
            
            switch (wsMessage.type) {
                case "subscribe_image":
                    handleSubscribeImage(session, String.valueOf(wsMessage.payload));
                    break;
                case "shell_mount":
                    handleShellMount(session, String.valueOf(wsMessage.payload));
                    break;
                case "shell_command":
                    handleShellCommand(session, String.valueOf(wsMessage.payload));
                    break;
                case "shell_unmount":
                    handleShellUnmount(session);
                    break;
                case "operation_request":
                    handleOperationRequest(session, String.valueOf(wsMessage.payload));
                    break;
                case "ping":
                    sendMessage(session, new WebSocketMessage("pong", "pong"));
                    break;
                default:
                    sendMessage(session, new WebSocketMessage("error", "Unknown message type: " + wsMessage.type));
                    break;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing WebSocket message: " + e.getMessage());
            e.printStackTrace();
            sendMessage(session, new WebSocketMessage("error", "Failed to process message: " + e.getMessage()));
        }
    }
    
    public void onClose(Object session, int statusCode, String reason) {
        try {
            System.out.println("WebSocket disconnected: " + getSessionId(session) + " (code: " + statusCode + ", reason: " + reason + ")");
            
            // Clean up shell session
            ShellSession shellSession = shellSessions.remove(session);
            if (shellSession != null) {
                shellSession.close();
            }
            
            sessionSubscriptions.remove(session);
        } catch (Exception e) {
            System.err.println("Error on WebSocket close: " + e.getMessage());
        }
    }
    
    public void onError(Object session, Throwable throwable) {
        System.err.println("WebSocket error for session " + getSessionId(session) + ": " + throwable.getMessage());
        throwable.printStackTrace();
    }
    
    private void handleShellMount(Object session, String imageName) {
        try {
            imageName = imageName.replace("\"", "").trim();
            
            // Close existing shell session if any
            ShellSession existingSession = shellSessions.get(session);
            if (existingSession != null) {
                existingSession.close();
            }
            
            String imagePath = IMAGES_DIR + "/" + imageName + ".img";
            if (!new File(imagePath).exists()) {
                sendMessage(session, new WebSocketMessage("shell_error", "Image not found: " + imageName));
                return;
            }
            
            // Mount filesystem
            DeviceAccess device = new DeviceAccess(imagePath);
            FatFileSystem fs = FatFileSystem.mount(device);
            
            ShellSession shellSession = new ShellSession();
            shellSession.imageName = imageName;
            shellSession.device = device;
            shellSession.fileSystem = fs;
            shellSession.currentDirectory = fs.getRootDirectory();
            shellSession.currentPath = "/";
            
            shellSessions.put(session, shellSession);
            
            sendMessage(session, new WebSocketMessage("shell_mounted", Map.of(
                "imageName", imageName,
                "fatType", fs.getBootSector().getFatType().toString(),
                "currentPath", "/"
            )));
            
            // Send welcome message
            String welcome = String.format("JFAT Interactive Shell - %s Filesystem\nMounted: %s\nType 'help' for commands\n", 
                fs.getBootSector().getFatType(), imageName);
            sendMessage(session, new WebSocketMessage("shell_output", welcome));
            
        } catch (Exception e) {
            sendMessage(session, new WebSocketMessage("shell_error", "Failed to mount image: " + e.getMessage()));
        }
    }
    
    private void handleShellCommand(Object session, String command) {
        ShellSession shellSession = shellSessions.get(session);
        if (shellSession == null) {
            sendMessage(session, new WebSocketMessage("shell_error", "No shell session active. Mount an image first."));
            return;
        }
        
        try {
            command = command.replace("\"", "").trim();
            String output = processShellCommand(shellSession, command);
            
            sendMessage(session, new WebSocketMessage("shell_output", output));
            
            // Notify about filesystem changes for commands that modify the filesystem
            String[] parts = command.split("\\s+");
            if (parts.length > 0) {
                String cmd = parts[0].toUpperCase();
                if (isFilesystemModifyingCommand(cmd)) {
                    broadcastFilesystemChange(shellSession.imageName, shellSession.currentPath, "modified");
                }
            }
            
        } catch (Exception e) {
            sendMessage(session, new WebSocketMessage("shell_error", "Command failed: " + e.getMessage()));
        }
    }
    
    private void handleShellUnmount(Object session) {
        ShellSession shellSession = shellSessions.remove(session);
        if (shellSession != null) {
            shellSession.close();
            sendMessage(session, new WebSocketMessage("shell_unmounted", "Shell session closed"));
        }
    }
    
    private String processShellCommand(ShellSession session, String line) throws IOException {
        if (line.trim().isEmpty()) {
            return "";
        }
        
        StringWriter output = new StringWriter();
        PrintWriter out = new PrintWriter(output);
        
        String[] parts = parseCommand(line);
        if (parts.length == 0) return "";
        
        String command = parts[0].toUpperCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        switch (command) {
            case "DIR":
            case "LS":
                handleDir(session, args, out);
                break;
            case "CD":
                handleCd(session, args, out);
                break;
            case "MD":
            case "MKDIR":
                handleMkdir(session, args, out);
                break;
            case "RD":
            case "RMDIR":
                handleRmdir(session, args, out);
                break;
            case "DEL":
            case "DELETE":
                handleDelete(session, args, out);
                break;
            case "TYPE":
            case "CAT":
                handleType(session, args, out);
                break;
            case "PWD":
                out.println(session.currentPath);
                break;
            case "FRAG":
                handleFragmentation(session, args, out);
                break;
            case "HELP":
                handleHelp(out);
                break;
            case "CLS":
            case "CLEAR":
                return "CLEAR_SCREEN";
            default:
                out.println("Unknown command: " + command);
                out.println("Type 'help' for available commands");
        }
        
        return output.toString();
    }
    
    private void handleDir(ShellSession session, String[] args, PrintWriter out) throws IOException {
        FatDirectory targetDir = session.currentDirectory;
        String targetPath = session.currentPath;
        
        if (args.length > 0) {
            FatEntry entry = resolveEntry(session, args[0]);
            if (entry == null) {
                out.println("Directory not found: " + args[0]);
                return;
            }
            if (!entry.isDirectory()) {
                out.println("Not a directory: " + args[0]);
                return;
            }
            targetDir = (FatDirectory) entry;
            targetPath = resolvePath(session, args[0]);
        }
        
        out.println("Directory of " + targetPath);
        out.println();
        
        List<FatEntry> entries = targetDir.list();
        if (entries.isEmpty()) {
            out.println("(empty directory)");
            return;
        }
        
        out.printf("%-20s %-10s %-15s%n", "Name", "Type", "Size");
        out.println("-".repeat(45));
        
        long totalSize = 0;
        int fileCount = 0;
        int dirCount = 0;
        
        for (FatEntry entry : entries) {
            String type = entry.isDirectory() ? "<DIR>" : "";
            String size = entry.isDirectory() ? "" : formatSize(entry.getSize());
            
            out.printf("%-20s %-10s %-15s%n", entry.getName(), type, size);
            
            if (entry.isDirectory()) {
                dirCount++;
            } else {
                fileCount++;
                totalSize += entry.getSize();
            }
        }
        
        out.println();
        out.printf("%d file(s) %s bytes%n", fileCount, formatSize(totalSize));
        out.printf("%d dir(s)%n", dirCount);
    }
    
    private void handleCd(ShellSession session, String[] args, PrintWriter out) throws IOException {
        if (args.length == 0) {
            out.println(session.currentPath);
            return;
        }
        
        String targetPath = args[0];
        
        // Handle special cases
        if ("..".equals(targetPath)) {
            if (!"/".equals(session.currentPath)) {
                int lastSlash = session.currentPath.lastIndexOf('/', session.currentPath.length() - 2);
                if (lastSlash <= 0) {
                    session.currentPath = "/";
                    session.currentDirectory = session.fileSystem.getRootDirectory();
                } else {
                    session.currentPath = session.currentPath.substring(0, lastSlash + 1);
                    session.currentDirectory = (FatDirectory) resolveEntry(session, session.currentPath);
                }
            }
            return;
        }
        
        if ("/".equals(targetPath) || "\\".equals(targetPath)) {
            session.currentPath = "/";
            session.currentDirectory = session.fileSystem.getRootDirectory();
            return;
        }
        
        FatEntry entry = resolveEntry(session, targetPath);
        if (entry == null) {
            out.println("Directory not found: " + targetPath);
            return;
        }
        
        if (!entry.isDirectory()) {
            out.println("Not a directory: " + targetPath);
            return;
        }
        
        session.currentDirectory = (FatDirectory) entry;
        session.currentPath = resolvePath(session, targetPath);
    }
    
    private void handleMkdir(ShellSession session, String[] args, PrintWriter out) throws IOException {
        if (args.length == 0) {
            out.println("Usage: MD <directory>");
            return;
        }
        
        for (String dirName : args) {
            try {
                session.currentDirectory.createDirectory(dirName);
                out.println("Directory created: " + dirName);
            } catch (IOException e) {
                out.println("Error creating directory " + dirName + ": " + e.getMessage());
            }
        }
    }
    
    private void handleRmdir(ShellSession session, String[] args, PrintWriter out) throws IOException {
        if (args.length == 0) {
            out.println("Usage: RD <directory>");
            return;
        }
        
        for (String dirName : args) {
            FatEntry entry = resolveEntry(session, dirName);
            if (entry == null) {
                out.println("Directory not found: " + dirName);
                continue;
            }
            
            if (!entry.isDirectory()) {
                out.println("Not a directory: " + dirName);
                continue;
            }
            
            try {
                entry.delete();
                out.println("Directory removed: " + dirName);
            } catch (IOException e) {
                out.println("Error removing directory " + dirName + ": " + e.getMessage());
            }
        }
    }
    
    private void handleDelete(ShellSession session, String[] args, PrintWriter out) throws IOException {
        if (args.length == 0) {
            out.println("Usage: DEL <filename>");
            return;
        }
        
        for (String filename : args) {
            FatEntry entry = resolveEntry(session, filename);
            if (entry == null) {
                out.println("File not found: " + filename);
                continue;
            }
            
            if (entry.isDirectory()) {
                out.println("Cannot delete directory with DEL. Use RD instead: " + filename);
                continue;
            }
            
            try {
                entry.delete();
                out.println("Deleted: " + filename);
            } catch (IOException e) {
                out.println("Error deleting " + filename + ": " + e.getMessage());
            }
        }
    }
    
    private void handleType(ShellSession session, String[] args, PrintWriter out) throws IOException {
        if (args.length == 0) {
            out.println("Usage: TYPE <filename>");
            return;
        }
        
        String filename = args[0];
        FatEntry entry = resolveEntry(session, filename);
        if (entry == null) {
            out.println("File not found: " + filename);
            return;
        }
        
        if (entry.isDirectory()) {
            out.println("Cannot display directory: " + filename);
            return;
        }
        
        FatFile file = (FatFile) entry;
        try {
            byte[] content = file.readAllBytes();
            // Try to display as text
            String text = new String(content);
            out.println(text);
        } catch (Exception e) {
            out.println("Error reading file " + filename + ": " + e.getMessage());
        }
    }
    
    private void handleFragmentation(ShellSession session, String[] args, PrintWriter out) throws IOException {
        if (session.fileSystem == null) {
            out.println("❌ No filesystem mounted");
            return;
        }
        
        String subCommand = args.length > 0 ? args[0].toUpperCase() : "";
        
        try {
            FragmentationAnalyzer analyzer = new FragmentationAnalyzer(session.fileSystem);
            
            switch (subCommand) {
                case "":
                    showFragmentationOverview(analyzer, out);
                    break;
                case "FILES":
                    showFragmentedFiles(analyzer, out);
                    break;
                case "FREE":
                    showFreeSpaceFragmentation(analyzer, out);
                    break;
                case "WORST":
                    showWorstFragmentedFiles(analyzer, out);
                    break;
                case "RECOMMENDATIONS":
                    showDefragmentationRecommendations(analyzer, out);
                    break;
                default:
                    out.println("Usage: FRAG [FILES|FREE|WORST|RECOMMENDATIONS]");
                    out.println("");
                    out.println("Commands:");
                    out.println("  FRAG                     - Show fragmentation overview");
                    out.println("  FRAG FILES              - List all fragmented files");
                    out.println("  FRAG FREE               - Show free space fragmentation");
                    out.println("  FRAG WORST              - Show most fragmented files");
                    out.println("  FRAG RECOMMENDATIONS    - Show defragmentation suggestions");
            }
        } catch (Exception e) {
            out.println("❌ Fragmentation analysis failed: " + e.getMessage());
        }
    }
    
    private void showFragmentationOverview(FragmentationAnalyzer analyzer, PrintWriter out) throws IOException {
        FragmentationAnalyzer.FragmentationAnalysis analysis = analyzer.analyzeFragmentation();
        
        out.println("🔧 Fragmentation Analysis");
        out.println("==========================");
        out.printf("File Fragmentation: %.1f%% (%d fragmented files)%n",
            analysis.fileFragmentation.fragmentationRatio,
            analysis.fileFragmentation.worstFiles.size());
        out.printf("Performance Impact: %.0f/100 (%s)%n",
            analysis.performanceImpact.fragmentationImpactScore,
            getImpactDescription(analysis.performanceImpact.fragmentationImpactScore));
        out.printf("Avg Fragments/File: %.1f%n", analysis.fileFragmentation.averageFragmentsPerFile);
        out.printf("Free Space Fragmentation: %.1f%%%n", analysis.freeSpaceFragmentation.freeSpaceFragmentationRatio);
        
        if (!analysis.recommendations.isEmpty()) {
            out.println("");
            out.printf("Recommendations: %d suggestions available (use 'FRAG RECOMMENDATIONS')%n", 
                analysis.recommendations.size());
        }
    }
    
    private void showFragmentedFiles(FragmentationAnalyzer analyzer, PrintWriter out) throws IOException {
        FragmentationAnalyzer.FragmentationAnalysis analysis = analyzer.analyzeFragmentation();
        
        out.println("📁 Fragmented Files");
        out.println("===================");
        
        if (analysis.fileFragmentation.worstFiles.isEmpty()) {
            out.println("✅ No fragmented files found!");
            return;
        }
        
        out.printf("%-50s %8s %5s %8s %s%n", "File Path", "Size", "Frags", "Avg Gap", "Severity");
        out.println("-".repeat(85));
        
        for (FragmentationAnalyzer.FileFragmentationInfo file : analysis.fileFragmentation.worstFiles) {
            out.printf("%-50s %8s %5d %8.1f %s%n",
                truncatePath(file.path, 50),
                formatSize(file.size),
                file.fragmentCount,
                file.averageGap,
                getSeveritySymbol(file.severity));
        }
    }
    
    private void showFreeSpaceFragmentation(FragmentationAnalyzer analyzer, PrintWriter out) throws IOException {
        FragmentationAnalyzer.FragmentationAnalysis analysis = analyzer.analyzeFragmentation();
        
        out.println("💾 Free Space Analysis");
        out.println("======================");
        out.printf("Free Space Fragmentation: %.1f%%%n", analysis.freeSpaceFragmentation.freeSpaceFragmentationRatio);
        out.printf("Largest Free Block: %d clusters%n", analysis.freeSpaceFragmentation.largestContiguousFreeBlock);
        out.printf("Average Block Size: %.1f clusters%n", analysis.freeSpaceFragmentation.averageFreeBlockSize);
        out.printf("Total Free Blocks: %d%n", analysis.freeSpaceFragmentation.freeBlockCount);
        
        if (analysis.freeSpaceFragmentation.freeBlockCount <= 10) {
            out.println("");
            out.println("Free Space Distribution:");
            for (FragmentationAnalyzer.FreeSpaceBlock block : analysis.freeSpaceFragmentation.freeSpaceMap) {
                out.printf("  Cluster %d-%d (%d clusters)%n", 
                    block.startCluster, 
                    block.startCluster + block.size - 1, 
                    block.size);
            }
        }
    }
    
    private void showWorstFragmentedFiles(FragmentationAnalyzer analyzer, PrintWriter out) throws IOException {
        FragmentationAnalyzer.FragmentationAnalysis analysis = analyzer.analyzeFragmentation();
        
        out.println("🚨 Most Fragmented Files");
        out.println("========================");
        
        List<FragmentationAnalyzer.FileFragmentationInfo> worstFiles = analysis.fileFragmentation.worstFiles.stream()
            .sorted((a, b) -> Integer.compare(b.fragmentCount, a.fragmentCount))
            .limit(5)
            .collect(Collectors.toList());
        
        if (worstFiles.isEmpty()) {
            out.println("✅ No fragmented files found!");
            return;
        }
        
        for (int i = 0; i < worstFiles.size(); i++) {
            FragmentationAnalyzer.FileFragmentationInfo file = worstFiles.get(i);
            out.printf("%d. %s%n", i + 1, file.path);
            out.printf("   Size: %s, Fragments: %d, Avg Gap: %.1f clusters%n",
                formatSize(file.size), file.fragmentCount, file.averageGap);
            out.printf("   Severity: %s %s%n", getSeveritySymbol(file.severity), file.severity);
            out.println("");
        }
    }
    
    private void showDefragmentationRecommendations(FragmentationAnalyzer analyzer, PrintWriter out) throws IOException {
        FragmentationAnalyzer.FragmentationAnalysis analysis = analyzer.analyzeFragmentation();
        
        out.println("💡 Defragmentation Recommendations");
        out.println("==================================");
        
        if (analysis.recommendations.isEmpty()) {
            out.println("✅ No defragmentation needed! Your filesystem is well optimized.");
            return;
        }
        
        for (FragmentationAnalyzer.FragmentationRecommendation rec : analysis.recommendations) {
            String prioritySymbol = getPrioritySymbol(rec.priority);
            out.printf("%s %s: %s%n", prioritySymbol, rec.priority, rec.description);
            
            if (!rec.affectedFiles.isEmpty() && rec.affectedFiles.size() <= 5) {
                for (String file : rec.affectedFiles) {
                    out.printf("    - %s%n", file);
                }
            } else if (!rec.affectedFiles.isEmpty()) {
                out.printf("    - %d files affected%n", rec.affectedFiles.size());
            }
            out.println("");
        }
    }
    
    // Helper methods for fragmentation analysis display
    private String getImpactDescription(double score) {
        if (score < 20) return "Low";
        else if (score < 50) return "Moderate";
        else if (score < 80) return "High";
        else return "Severe";
    }
    
    private String getSeveritySymbol(FragmentationAnalyzer.FragmentationSeverity severity) {
        switch (severity) {
            case NONE: return "✅";
            case LIGHT: return "🟡";
            case MODERATE: return "🟠";
            case HEAVY: return "🔴";
            case SEVERE: return "💥";
            default: return "❓";
        }
    }
    
    private String getPrioritySymbol(FragmentationAnalyzer.Priority priority) {
        switch (priority) {
            case LOW: return "🔵";
            case MEDIUM: return "🟡";
            case HIGH: return "🔴";
            default: return "❓";
        }
    }
    
    private String truncatePath(String path, int maxLength) {
        if (path.length() <= maxLength) return path;
        return "..." + path.substring(path.length() - maxLength + 3);
    }
    
    private void handleHelp(PrintWriter out) {
        out.println("Available commands:");
        out.println("  DIR [path]          - List directory contents (alias: LS)");
        out.println("  CD <path>           - Change directory");
        out.println("  MD <dir>            - Create directory (alias: MKDIR)");
        out.println("  RD <dir>            - Remove directory (alias: RMDIR)");
        out.println("  DEL <file>          - Delete file (alias: DELETE)");
        out.println("  TYPE <file>         - Display file contents (alias: CAT)");
        out.println("  PWD                 - Show current directory");
        out.println("  CLS                 - Clear screen (alias: CLEAR)");
        out.println("  HELP                - Show this help");
        out.println();
        out.println("Fragmentation Analysis:");
        out.println("  FRAG                     - Show fragmentation overview");
        out.println("  FRAG FILES              - List all fragmented files");
        out.println("  FRAG FREE               - Show free space fragmentation");
        out.println("  FRAG WORST              - Show most fragmented files");
        out.println("  FRAG RECOMMENDATIONS    - Show defragmentation suggestions");
        out.println();
        out.println("Special paths:");
        out.println("  /                   - Root directory");
        out.println("  ..                  - Parent directory");
    }
    
    private boolean isFilesystemModifyingCommand(String command) {
        return Arrays.asList("MD", "MKDIR", "RD", "RMDIR", "DEL", "DELETE", "COPY", "CP").contains(command);
    }
    
    private FatEntry resolveEntry(ShellSession session, String path) throws IOException {
        if (path.startsWith("/")) {
            // Absolute path
            return getEntryByPath(session.fileSystem, path);
        } else {
            // Relative path
            String fullPath = session.currentPath;
            if (!fullPath.endsWith("/")) {
                fullPath += "/";
            }
            fullPath += path;
            return getEntryByPath(session.fileSystem, fullPath);
        }
    }
    
    private String resolvePath(ShellSession session, String path) {
        if (path.startsWith("/")) {
            return path.endsWith("/") ? path : path + "/";
        } else {
            String fullPath = session.currentPath;
            if (!fullPath.endsWith("/")) {
                fullPath += "/";
            }
            fullPath += path;
            return fullPath.endsWith("/") ? fullPath : fullPath + "/";
        }
    }
    
    private static FatEntry getEntryByPath(FatFileSystem fs, String path) throws IOException {
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
    
    private String[] parseCommand(String line) {
        // Simple command parsing - handles quoted arguments
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts.toArray(new String[0]);
    }
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private void handleSubscribeImage(Object session, String payload) {
        try {
            // Simple parsing to extract image name
            String imageName = payload.replace("\"", "").trim();
            sessionSubscriptions.put(session, imageName);
            
            sendMessage(session, new WebSocketMessage("subscribed", "Subscribed to image: " + imageName));
            
            // Send initial status
            sendMessage(session, new WebSocketMessage("image_status", 
                Map.of("imageName", imageName, "status", "ready")));
                
        } catch (Exception e) {
            sendMessage(session, new WebSocketMessage("error", "Failed to subscribe to image: " + e.getMessage()));
        }
    }
    
    private void handleOperationRequest(Object session, String payload) {
        try {
            // Parse operation request
            // For now, just acknowledge
            sendMessage(session, new WebSocketMessage("operation_started", 
                Map.of("operation", "unknown", "status", "started")));
            
            // Simulate progress updates
            new Thread(() -> {
                try {
                    for (int i = 0; i <= 100; i += 20) {
                        Thread.sleep(500);
                        sendMessage(session, new WebSocketMessage("operation_progress", 
                            Map.of("progress", i, "message", "Processing... " + i + "%")));
                    }
                    sendMessage(session, new WebSocketMessage("operation_completed", 
                        Map.of("operation", "unknown", "status", "completed")));
                } catch (Exception e) {
                    System.err.println("Error in operation simulation: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            sendMessage(session, new WebSocketMessage("error", "Failed to process operation: " + e.getMessage()));
        }
    }
    
    public void broadcastFilesystemChange(String imageName, String path, String changeType) {
        WebSocketMessage message = new WebSocketMessage("filesystem_changed", 
            Map.of("imageName", imageName, "path", path, "change", changeType));
        
        sessionSubscriptions.entrySet().stream()
            .filter(entry -> imageName.equals(entry.getValue()))
            .forEach(entry -> sendMessage(entry.getKey(), message));
    }
    
    public void broadcastGraphUpdate(String imageName, String graphContent) {
        WebSocketMessage message = new WebSocketMessage("graph_updated", 
            Map.of("imageName", imageName, "content", graphContent));
        
        sessionSubscriptions.entrySet().stream()
            .filter(entry -> imageName.equals(entry.getValue()))
            .forEach(entry -> sendMessage(entry.getKey(), message));
    }
    
    private void sendMessage(Object session, WebSocketMessage message) {
        try {
            String json = serializeMessage(message);
            // Use reflection to send message since we can't import Javalin classes yet
            session.getClass().getMethod("send", String.class).invoke(session, json);
        } catch (Exception e) {
            System.err.println("Error sending WebSocket message: " + e.getMessage());
        }
    }
    
    private String getSessionId(Object session) {
        try {
            return (String) session.getClass().getMethod("sessionId").invoke(session);
        } catch (Exception e) {
            return session.toString();
        }
    }
    
    private WebSocketMessage parseMessage(String json) {
        // Simple JSON parsing for WebSocket messages
        WebSocketMessage message = new WebSocketMessage();
        
        // Remove brackets and quotes
        json = json.trim().replaceAll("[{}\"\\s]", "");
        String[] pairs = json.split(",");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length >= 2) {
                String key = keyValue[0];
                String value = String.join(":", java.util.Arrays.copyOfRange(keyValue, 1, keyValue.length));
                
                switch (key) {
                    case "type":
                        message.type = value;
                        break;
                    case "payload":
                        message.payload = value;
                        break;
                }
            }
        }
        
        return message;
    }
    
    private String serializeMessage(WebSocketMessage message) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"").append(message.type).append("\"");
        
        if (message.payload instanceof String) {
            json.append(",\"payload\":\"").append(message.payload).append("\"");
        } else if (message.payload instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) message.payload;
            json.append(",\"payload\":{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
            json.append("}");
        }
        
        json.append("}");
        return json.toString();
    }
    
    // Message classes
    public static class WebSocketMessage {
        public String type;
        public Object payload;
        
        public WebSocketMessage() {}
        
        public WebSocketMessage(String type, Object payload) {
            this.type = type;
            this.payload = payload;
        }
    }
} 