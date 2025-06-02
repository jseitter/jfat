# JFAT API Guide

This guide shows how to use JFAT as a Java library for programmatic access to FAT filesystems.

## Installation

### Gradle

Add the JFAT dependency to your `build.gradle` file:

```gradle
repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/jseitter/jfat")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'net.seitter.jfat:jfat:0.1.0'
}
```

### Maven

Add the JFAT dependency to your `pom.xml` file:

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub jorgenseitter Apache Maven Packages</name>
        <url>https://maven.pkg.github.com/jorgenseitter/jfat</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>net.seitter.jfat</groupId>
        <artifactId>jfat</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

### Authentication for GitHub Package Registry

To access packages from GitHub Package Registry, you need to authenticate. Create a personal access token with `read:packages` permission and use it in one of these ways:

#### Option 1: Environment Variables
```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=your-personal-access-token
```

#### Option 2: Gradle Properties
Create or update `~/.gradle/gradle.properties`:
```properties
gpr.user=your-github-username
gpr.key=your-personal-access-token
```

#### Option 3: Maven Settings
Add to your `~/.m2/settings.xml`:
```xml
<servers>
    <server>
        <id>github</id>
        <username>your-github-username</username>
        <password>your-personal-access-token</password>
    </server>
</servers>
```

## Basic Usage

### Mounting a Filesystem

```java
import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.core.FatDirectory;
import net.seitter.jfat.io.DeviceAccess;

// Open a FAT filesystem from a device or file
try (DeviceAccess device = new DeviceAccess("/dev/sda1");
     FatFileSystem fs = FatFileSystem.mount(device)) {
    
    // Access filesystem...
    System.out.println("Mounted " + fs.getBootSector().getFatType() + " filesystem");
}

// Mount from disk image file
try (DeviceAccess device = new DeviceAccess("disk.img");
     FatFileSystem fs = FatFileSystem.mount(device)) {
    
    // Access filesystem...
}
```

### Working with Files

```java
// List root directory contents
FatDirectory root = fs.getRootDirectory();
for (FatEntry entry : root.list()) {
    System.out.println(entry.getName() + " (" + 
        (entry.isDirectory() ? "DIR" : entry.getSize() + " bytes") + ")");
}

// Read a file
FatFile file = fs.getFile("/path/to/file.txt");
byte[] content = file.readAllBytes();
String text = new String(content, StandardCharsets.UTF_8);

// Write to a file
FatFile newFile = fs.createFile("/path/to/new_file.txt");
newFile.write("Hello, FAT filesystem!".getBytes(StandardCharsets.UTF_8));

// Create a file in a subdirectory
FatDirectory subdir = root.getEntry("documents");
if (subdir == null) {
    subdir = root.createDirectory("documents");
}
FatFile docFile = subdir.createFile("readme.txt");
docFile.write("Documentation content".getBytes());
```

### Working with Directories

```java
// Create directory structure
FatDirectory root = fs.getRootDirectory();
FatDirectory docs = root.createDirectory("documents");
FatDirectory projects = docs.createDirectory("projects");

// Navigate directories
FatDirectory targetDir = root.getEntry("documents").getEntry("projects");

// List directory recursively
void listRecursively(FatDirectory dir, String indent) throws IOException {
    for (FatEntry entry : dir.list()) {
        System.out.println(indent + entry.getName());
        if (entry.isDirectory()) {
            listRecursively((FatDirectory) entry, indent + "  ");
        }
    }
}
```

## Creating Disk Images Programmatically

### With Default Cluster Sizes

JFAT automatically selects optimal cluster sizes following Microsoft specifications:

```java
import net.seitter.jfat.util.DiskImageCreator;
import net.seitter.jfat.core.FatType;

// Create images with Microsoft-recommended cluster sizes
DiskImageCreator.createDiskImage("fat12.img", FatType.FAT12, 2);      // 2MB with 512-byte clusters
DiskImageCreator.createDiskImage("fat16.img", FatType.FAT16, 128);    // 128MB with 2KB clusters  
DiskImageCreator.createDiskImage("fat32.img", FatType.FAT32, 1024);   // 1GB with 4KB clusters
```

### With Custom Cluster Sizes

For advanced use cases, you can specify custom sector sizes and cluster configurations:

```java
// Create FAT32 with custom 32KB clusters (64 sectors × 512 bytes)
DiskImageCreator.createDiskImage("custom.img", FatType.FAT32, 2048, 512, 64);

// Create FAT16 with 4096-byte sectors and 8KB clusters (2 sectors)
DiskImageCreator.createDiskImage("large_sector.img", FatType.FAT16, 256, 4096, 2);

// Get valid cluster sizes for a sector size
int[] validSizes = DiskImageCreator.getValidClusterSizes(512);
for (int size : validSizes) {
    System.out.println("Valid cluster size: " + DiskImageCreator.formatClusterSize(size));
}
```

### Cluster Size Information

```java
// Display cluster size recommendations
DiskImageCreator.displayClusterSizeInfo(1024, FatType.FAT32, 512);

// Get recommended cluster configuration
long volumeSize = 1024L * 1024 * 1024; // 1GB
int recommended = BootSector.getRecommendedSectorsPerCluster(volumeSize, FatType.FAT32, 512);
System.out.println("Recommended sectors per cluster: " + recommended);
```

## Filesystem Analysis

### Boot Sector Information

```java
BootSector bootSector = fs.getBootSector();

// Basic information
System.out.println("FAT Type: " + bootSector.getFatType());
System.out.println("Total sectors: " + bootSector.getTotalSectors());
System.out.println("Bytes per sector: " + bootSector.getBytesPerSector());
System.out.println("Sectors per cluster: " + bootSector.getSectorsPerCluster());
System.out.println("Cluster size: " + bootSector.getClusterSizeBytes() + " bytes");

// Detailed cluster information
System.out.println(bootSector.getClusterSizeInfo());

// Validation
boolean validSectors = BootSector.isValidSectorsPerCluster(bootSector.getSectorsPerCluster());
boolean validBytes = BootSector.isValidBytesPerSector(bootSector.getBytesPerSector());
System.out.println("Cluster configuration valid: " + (validSectors && validBytes));
```

### FAT Table Access

```java
FatTable fatTable = fs.getFatTable();

// Get cluster chain for a file
FatFile file = fs.getFile("/large_file.dat");
List<Long> clusterChain = fatTable.getClusterChain(file.getFirstCluster());
System.out.println("File spans " + clusterChain.size() + " clusters: " + clusterChain);

// Check cluster status
long cluster = 100;
long entry = fatTable.getClusterEntry(cluster);
if (entry == 0) {
    System.out.println("Cluster " + cluster + " is free");
} else if (entry == fatTable.getEndOfChainMarker()) {
    System.out.println("Cluster " + cluster + " is end of chain");
} else if (entry == fatTable.getBadClusterMarker()) {
    System.out.println("Cluster " + cluster + " is marked as bad");
} else {
    System.out.println("Cluster " + cluster + " points to cluster " + entry);
}
```

## Advanced Operations

### Long Filename Support

JFAT supports long filenames (LFN) automatically:

```java
// Create files with long names and Unicode characters
FatFile unicodeFile = root.createFile("Документ с очень длинным именем файла.txt");
FatFile emojiFile = root.createFile("My 🚀 Project File.doc");
FatFile mixedFile = root.createFile("Mixed-文档-αρχείο.pdf");

// The API handles LFN entries transparently
for (FatEntry entry : root.list()) {
    System.out.println("Long name: " + entry.getName());
    // Short names are generated automatically (e.g., ДОКУМЕ~1.TXT)
}
```

### File Attributes and Metadata

```java
FatFile file = (FatFile) root.getEntry("example.txt");

// File timestamps
System.out.println("Created: " + file.getCreateTime());
System.out.println("Modified: " + file.getModifyTime());
System.out.println("Accessed: " + file.getAccessDate());

// File attributes
System.out.println("Size: " + file.getSize() + " bytes");
System.out.println("First cluster: " + file.getFirstCluster());
System.out.println("Is read-only: " + file.isReadOnly());
System.out.println("Is hidden: " + file.isHidden());
```

### Custom Device Access

```java
// Implement custom device access for special use cases
import net.seitter.jfat.io.DeviceAccess;

public class CustomDeviceAccess extends DeviceAccess {
    // Custom implementation for network storage, encrypted devices, etc.
    
    @Override
    public byte[] read(long offset, int length) throws IOException {
        // Custom read implementation
        return new byte[length];
    }
    
    @Override
    public void write(long offset, byte[] data) throws IOException {
        // Custom write implementation
    }
}

// Use custom device access
try (CustomDeviceAccess device = new CustomDeviceAccess();
     FatFileSystem fs = FatFileSystem.mount(device)) {
    // Work with filesystem through custom device
}
```

## Error Handling

### Validation Errors

```java
try {
    // This will throw IOException if cluster configuration is invalid
    DiskImageCreator.createDiskImage("invalid.img", FatType.FAT32, 1024, 512, 3); // 3 is not power of 2
} catch (IOException e) {
    System.err.println("Cluster validation failed: " + e.getMessage());
    // Error: Invalid sectors per cluster: 3. Must be a power of 2 between 1 and 128 inclusive
}

try {
    // This will throw IOException if cluster size exceeds 32MB
    DiskImageCreator.createDiskImage("too_large.img", FatType.FAT32, 1024, 4096, 128); // 512KB clusters
} catch (IOException e) {
    System.err.println("Cluster size too large: " + e.getMessage());
}
```

### File System Errors

```java
try (DeviceAccess device = new DeviceAccess("disk.img");
     FatFileSystem fs = FatFileSystem.mount(device)) {
    
    // Handle file not found
    FatEntry entry = fs.getRootDirectory().getEntry("nonexistent.txt");
    if (entry == null) {
        System.out.println("File not found");
    }
    
    // Handle invalid paths
    try {
        FatFile file = fs.getFile("/invalid/path/file.txt");
    } catch (IOException e) {
        System.err.println("Path error: " + e.getMessage());
    }
    
} catch (IOException e) {
    System.err.println("Filesystem error: " + e.getMessage());
}
```

## Performance Considerations

### Optimal Cluster Sizes

```java
// Use recommended cluster sizes for best performance
long volumeSize = 16L * 1024 * 1024 * 1024; // 16GB
int optimalSectors = BootSector.getRecommendedSectorsPerCluster(volumeSize, FatType.FAT32, 512);
// Returns 32 (16KB clusters) for optimal performance on 16GB FAT32

// Compare performance impact
DiskImageCreator.createDiskImage("small_clusters.img", FatType.FAT32, 16384, 512, 8);  // 4KB clusters
DiskImageCreator.createDiskImage("large_clusters.img", FatType.FAT32, 16384, 512, 32); // 16KB clusters
// Large clusters = fewer FAT entries = better performance for large files
```

### Memory Usage

```java
// For large files, read in chunks rather than all at once
FatFile largeFile = fs.getFile("/large_video.mp4");
byte[] buffer = new byte[64 * 1024]; // 64KB buffer
try (InputStream in = largeFile.getInputStream()) {
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
        // Process chunk
        processChunk(buffer, bytesRead);
    }
}
```

### Directory Operations

```java
// Cache directory listings for better performance
Map<String, List<FatEntry>> directoryCache = new HashMap<>();
List<FatEntry> entries = directoryCache.computeIfAbsent(
    dirPath, 
    k -> {
        try {
            return directory.list();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
);
```

## Integration Examples

### Spring Boot Integration

```java
@Service
public class FatFilesystemService {
    
    public void processImage(String imagePath) {
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            // Process filesystem
            analyzeFilesystem(fs);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to process FAT image", e);
        }
    }
    
    private void analyzeFilesystem(FatFileSystem fs) throws IOException {
        BootSector bootSector = fs.getBootSector();
        
        // Log filesystem information
        log.info("Mounted {} filesystem with {} clusters", 
                bootSector.getFatType(), 
                bootSector.getClusterSizeBytes());
        
        // Process files...
    }
}
```

### Command-Line Tool Integration

```java
public class CustomFatTool {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: CustomFatTool <operation> <image>");
            System.exit(1);
        }
        
        String operation = args[0];
        String imagePath = args[1];
        
        try (DeviceAccess device = new DeviceAccess(imagePath);
             FatFileSystem fs = FatFileSystem.mount(device)) {
            
            switch (operation) {
                case "analyze":
                    analyzeClusterUsage(fs);
                    break;
                case "optimize":
                    suggestOptimizations(fs);
                    break;
                default:
                    System.err.println("Unknown operation: " + operation);
            }
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    private static void analyzeClusterUsage(FatFileSystem fs) throws IOException {
        BootSector bs = fs.getBootSector();
        FatTable fat = fs.getFatTable();
        
        // Analyze cluster allocation patterns
        System.out.println("Cluster Analysis:");
        System.out.println("Cluster size: " + DiskImageCreator.formatClusterSize(bs.getClusterSizeBytes()));
        
        // Count cluster usage
        long totalClusters = (bs.getTotalSectors() - bs.getFirstDataSector()) / bs.getSectorsPerCluster();
        long usedClusters = 0;
        
        for (long cluster = 2; cluster < totalClusters + 2; cluster++) {
            if (fat.getClusterEntry(cluster) != 0) {
                usedClusters++;
            }
        }
        
        double usage = (double) usedClusters / totalClusters * 100;
        System.out.printf("Cluster usage: %d/%d (%.1f%%)%n", usedClusters, totalClusters, usage);
    }
}
```

This API guide provides comprehensive examples for integrating JFAT into your Java applications, with special emphasis on the new cluster size management features. 