# JFAT Development Guide

This guide covers building JFAT from source, understanding the project structure, and contributing to the codebase.

## Prerequisites

- **Java**: JDK 11 or higher (tested with JDK 11, 17, and 21)
- **Gradle**: 8.13 or higher (wrapper included)
- **Git**: For version control
- **Optional**: Graphviz for rendering DOT graph outputs

## Building from Source

### Clone the Repository
```bash
git clone https://github.com/jseitter/jfat.git
cd jfat
```

### Build the Project
```bash
# Build and run all tests
./gradlew build

# Build without tests (faster)
./gradlew assemble

# Clean and rebuild
./gradlew clean build
```

### Build Artifacts
After building, you'll find:
- **Main JAR**: `build/libs/jfat-<version>.jar`
- **Sources JAR**: `build/libs/jfat-<version>-sources.jar`
- **Javadoc JAR**: `build/libs/jfat-<version>-javadoc.jar`

## Project Structure

```
jfat/
├── src/
│   ├── main/java/net/seitter/jfat/
│   │   ├── core/              # Core FAT filesystem classes
│   │   │   ├── BootSector.java           # Boot sector parsing and validation
│   │   │   ├── FatDirectory.java         # Directory operations with LFN support
│   │   │   ├── FatEntry.java             # File/directory entries
│   │   │   ├── FatFile.java              # File operations
│   │   │   ├── FatFileSystem.java        # Main filesystem interface
│   │   │   ├── FatTable.java             # FAT table management
│   │   │   ├── FatType.java              # FAT type enumeration
│   │   │   ├── LfnEntry.java             # Long filename entry handling
│   │   │   └── LfnProcessor.java         # LFN processing engine
│   │   ├── io/                # I/O and device access
│   │   │   └── DeviceAccess.java         # Device/file access abstraction
│   │   ├── util/              # Utility classes
│   │   │   ├── DiskImageCreator.java     # Disk image creation with cluster size support
│   │   │   └── FatUtils.java             # Utility functions and LFN support
│   │   ├── cli/               # Command-line interface
│   │   │   ├── FatCLI.java               # Main CLI with enhanced info command
│   │   │   └── FatInteractiveShell.java  # Interactive shell
│   │   └── examples/          # Example usage and tools
│   │       └── FatfsTest.java            # Standalone testing tool
│   └── test/java/net/seitter/jfat/       # Test suite
│       ├── core/              # Core functionality tests
│       │   ├── FatFileSystemTest.java    # Main filesystem tests
│       │   ├── LfnSupportTest.java       # Long filename tests
│       │   ├── FatPerformanceTest.java   # Performance benchmarks
│       │   └── FatTypeDetectionTest.java # Type detection tests
│       └── util/              # Utility tests
│           └── FatUtilsTest.java         # Utility function tests
├── docs/                      # Documentation
│   ├── api-guide.md          # API usage guide
│   ├── cli-reference.md      # CLI command reference
│   ├── testing.md            # Testing guide
│   ├── development.md        # This file
│   └── ci-cd.md              # CI/CD documentation
├── .github/                   # GitHub configuration
│   └── workflows/            # GitHub Actions workflows
├── build.gradle              # Gradle build configuration
├── gradle.properties         # Gradle properties
├── version.txt               # Project version (single source of truth)
├── README.md                 # Main project documentation
└── LICENSE                   # MIT license
```

## Key Components

### Core Classes

#### BootSector
- **Purpose**: Boot sector parsing and filesystem parameter validation
- **New Features**: Cluster size validation and Microsoft-compliant recommendations
- **Key Methods**:
  - `isValidSectorsPerCluster()` - Validates cluster configuration
  - `getRecommendedSectorsPerCluster()` - Returns optimal cluster sizes
  - `getClusterSizeInfo()` - Detailed cluster information formatting

#### FatDirectory
- **Purpose**: Directory operations with full LFN support
- **Features**: 
  - Long filename creation and retrieval
  - Windows-style short name generation
  - Mixed 8.3 and LFN entry handling
- **Key Methods**:
  - `createFile()` and `createDirectory()` with LFN support
  - `list()` with LFN processing
  - `getEntry()` with both long and short name lookup

#### LfnProcessor
- **Purpose**: Central engine for long filename processing
- **Features**:
  - UTF-16LE encoding/decoding
  - Checksum validation
  - Entry sequence management
- **Key Classes**:
  - `DirectoryEntryResult` - Wrapper for processed entries
  - LFN entry generation and validation

#### DiskImageCreator
- **Purpose**: Creating FAT disk images with configurable cluster sizes
- **New Features**:
  - Microsoft-compliant cluster size recommendations
  - Custom cluster configuration support
  - Cluster size validation and information display
- **Key Methods**:
  - `createDiskImage()` with automatic cluster sizing
  - `getValidClusterSizes()` - Returns valid cluster configurations
  - `displayClusterSizeInfo()` - Shows cluster recommendations

### Architecture Patterns

#### Try-with-Resources Pattern
All I/O operations use proper resource management:
```java
try (DeviceAccess device = new DeviceAccess(imagePath);
     FatFileSystem fs = FatFileSystem.mount(device)) {
    // Operations automatically clean up resources
}
```

#### Validation Pattern
All input validation follows a consistent pattern:
```java
public static boolean isValidSectorsPerCluster(int sectorsPerCluster) {
    if (sectorsPerCluster < 1 || sectorsPerCluster > 128) {
        return false;
    }
    return (sectorsPerCluster & (sectorsPerCluster - 1)) == 0; // Power of 2 check
}
```

#### Builder Pattern
Complex objects use builder patterns for configuration:
```java
DiskImageCreator.createDiskImage(imagePath, fatType, sizeMB, bytesPerSector, sectorsPerCluster);
```

## Development Workflow

### 1. Setting Up Development Environment

```bash
# Clone and setup
git clone https://github.com/jseitter/jfat.git
cd jfat

# Verify Java version
java -version  # Should be 11+

# Build and test
./gradlew build

# Run CLI to verify installation
java -jar build/libs/jfat-*.jar help
```

### 2. Running Tests During Development

```bash
# Continuous testing (runs tests on file changes)
./gradlew test --continuous

# Fast feedback loop
./gradlew testClasses  # Compile tests without running
./gradlew test --tests "*ClusterSize*"  # Run specific tests

# Test with coverage
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html  # View coverage
```

### 3. Code Quality

```bash
# Check code style (if configured)
./gradlew checkstyleMain checkstyleTest

# Generate documentation
./gradlew javadoc
open build/docs/javadoc/index.html
```

### 4. Testing Changes

```bash
# Create test images
./gradlew createDiskImages

# Test CLI functionality
java -jar build/libs/jfat-*.jar create test.img fat32 64
java -jar build/libs/jfat-*.jar info test.img
java -jar build/libs/jfat-*.jar interactive test.img

# Run comprehensive tests
./gradlew runFatfsTest
```

## Gradle Tasks

### Core Tasks
```bash
./gradlew clean          # Clean build directory
./gradlew compileJava    # Compile main code
./gradlew compileTestJava # Compile test code
./gradlew classes        # Compile all classes
./gradlew test           # Run all tests
./gradlew jar            # Create main JAR
./gradlew build          # Full build and test
```

### Custom Tasks
```bash
./gradlew runCLI --args="help"                    # Run CLI with arguments
./gradlew runExample                              # Run example code
./gradlew runFatfsTest                           # Run standalone test tool
./gradlew createDiskImages                       # Create test disk images
./gradlew runCLI --args="create test.img fat32 64" # Create test image via CLI
```

### Publishing Tasks
```bash
./gradlew publishToMavenLocal                    # Publish to local Maven repository
./gradlew publish                                # Publish to configured repositories
```

## Contributing

### Code Style Guidelines

1. **Java Conventions**: Follow standard Java naming conventions
2. **Documentation**: All public methods must have Javadoc
3. **Error Handling**: Use specific exception types with descriptive messages
4. **Resource Management**: Always use try-with-resources for I/O
5. **Testing**: New features require comprehensive tests

### Example Code Contribution

```java
/**
 * Validates that the given cluster size configuration is optimal for the volume size.
 * 
 * @param volumeSizeBytes The total volume size in bytes
 * @param sectorsPerCluster The configured sectors per cluster
 * @param bytesPerSector The bytes per sector
 * @return True if the configuration is optimal, false otherwise
 */
public static boolean isOptimalClusterSize(long volumeSizeBytes, int sectorsPerCluster, int bytesPerSector) {
    // Determine FAT type based on volume size
    FatType fatType = determineFatType(volumeSizeBytes);
    
    // Get recommended configuration
    int recommendedSectors = getRecommendedSectorsPerCluster(volumeSizeBytes, fatType, bytesPerSector);
    
    // Allow some flexibility (within 2x of recommended)
    return sectorsPerCluster >= recommendedSectors / 2 && 
           sectorsPerCluster <= recommendedSectors * 2;
}
```

### Testing Contributions

```java
@Test
public void testOptimalClusterSizeValidation() {
    // Test optimal configuration
    assertTrue("1GB FAT32 should use 8 sectors per cluster", 
               isOptimalClusterSize(1024L * 1024 * 1024, 8, 512));
    
    // Test suboptimal but acceptable configuration  
    assertTrue("1GB FAT32 can use 4 sectors per cluster",
               isOptimalClusterSize(1024L * 1024 * 1024, 4, 512));
    
    // Test poor configuration
    assertFalse("1GB FAT32 should not use 1 sector per cluster",
                isOptimalClusterSize(1024L * 1024 * 1024, 1, 512));
}
```

### Pull Request Process

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/cluster-optimization`
3. **Implement** your changes with tests
4. **Verify** all tests pass: `./gradlew build`
5. **Update** documentation if needed
6. **Commit** with descriptive messages
7. **Push** to your fork and create a Pull Request

### Commit Message Guidelines

```bash
# Format: <type>(<scope>): <description>
git commit -m "feat(cluster): add cluster size optimization validation"
git commit -m "fix(lfn): resolve Unicode filename encoding issue"
git commit -m "docs(api): update cluster size configuration examples"
git commit -m "test(performance): add cluster size performance benchmarks"
```

## Debugging

### Enable Debug Logging

```bash
# Enable JFAT debug output
java -Djfat.debug=true -jar jfat.jar info test.img

# Enable with Gradle
./gradlew test -Djfat.debug=true --tests "FatFileSystemTest"
```

### Common Development Issues

1. **Test Failures**: 
   - Check `build/test-images/` for leftover test files
   - Verify disk space availability
   - Ensure proper file cleanup in tests

2. **Memory Issues**:
   - Increase heap size: `export GRADLE_OPTS="-Xmx2g"`
   - Monitor memory usage during large file tests

3. **Build Issues**:
   - Clean build directory: `./gradlew clean`
   - Check Java version compatibility
   - Verify Gradle wrapper integrity

### IDE Configuration

#### IntelliJ IDEA
1. Import project as Gradle project
2. Set Project SDK to JDK 11+
3. Enable annotation processing
4. Configure code style to match project conventions

#### Eclipse
1. Install Gradle plugin (Buildship)
2. Import as Gradle project
3. Set compiler compliance level to 11
4. Configure formatter settings

#### VS Code
1. Install Java Extension Pack
2. Install Gradle for Java extension
3. Open project folder
4. Configure Java path in settings

## Performance Optimization

### Profiling
```bash
# Profile test execution
./gradlew test --profile
open build/reports/profile/profile-*.html

# Profile specific operations
java -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=jfat-profile.jfr \
     -jar jfat.jar create large.img fat32 16384
```

### Memory Analysis
```bash
# Monitor memory usage during tests
java -XX:+PrintGCDetails -XX:+PrintGCTimeStamps \
     -jar jfat.jar create test.img fat32 1024
```

### Cluster Size Impact
```bash
# Test different cluster sizes for performance
java -jar jfat.jar create small-clusters.img fat32 1024   # Uses 4KB clusters
java -jar jfat.jar create large-clusters.img fat32 32768  # Uses 16KB clusters

# Benchmark operations on different configurations
./gradlew test --tests "FatPerformanceTest" -Djfat.debug=true
```

This development guide provides everything needed to build, test, and contribute to JFAT's codebase, with special focus on the new cluster size management features. 