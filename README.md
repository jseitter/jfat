# JFAT - Java FAT Filesystem Library

A Java library for reading and writing FAT (File Allocation Table) filesystems. JFAT provides APIs to work with FAT12, FAT16, and FAT32 filesystem formats.

## Features

- Mount FAT filesystems from devices (`/dev/sdX`) or disk image files
- Read and write files on FAT filesystems
- Create, delete, and modify directories
- Support for FAT12, FAT16, and FAT32 formats
- Low-level access to FAT structures (boot sector, file allocation tables, etc.)
- Comprehensive test suite with performance benchmarks

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

## Usage

### Basic Example

```java
import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.core.FatFile;
import net.seitter.jfat.io.DeviceAccess;

// Open a FAT filesystem from a device or file
try (FatFileSystem fs = FatFileSystem.mount(new DeviceAccess("/dev/sda1"))) {
    // List root directory contents
    fs.getRootDirectory().list().forEach(System.out::println);
    
    // Read a file
    FatFile file = fs.getFile("/path/to/file.txt");
    byte[] content = file.readAllBytes();
    
    // Write to a file
    FatFile newFile = fs.createFile("/path/to/new_file.txt");
    newFile.write("Hello, FAT filesystem!".getBytes());
}
```

### Working with Disk Images

```java
import net.seitter.jfat.util.DiskImageCreator;
import net.seitter.jfat.core.FatType;

// Create a new FAT32 disk image (64MB)
DiskImageCreator.createDiskImage("test.img", FatType.FAT32, 64);

// Mount and use the image
try (DeviceAccess device = new DeviceAccess("test.img");
     FatFileSystem fs = FatFileSystem.mount(device)) {
    
    // Create directories and files
    FatDirectory myDir = fs.getRootDirectory().createDirectory("mydir");
    FatFile myFile = myDir.createFile("example.txt");
    myFile.write("Hello World!".getBytes());
}
```

## Building

This project uses Gradle as the build system:

```bash
./gradlew build
```

## Testing

JFAT includes a comprehensive test suite covering functionality, performance, and edge cases across all FAT variants.

### Test Structure

The test suite is organized into several categories:

- **Unit Tests**: Test individual utility functions and low-level operations
- **Integration Tests**: Test complete filesystem operations across FAT12, FAT16, and FAT32
- **Performance Tests**: Benchmark file operations, directory traversal, and large file handling
- **Type Detection Tests**: Verify correct FAT type identification

### Running Tests

#### Run All Tests
```bash
./gradlew test
```

#### Run Specific Test Categories
```bash
# Run only basic functionality tests
./gradlew testBasic

# Run only FAT filesystem tests
./gradlew runFatTests

# Run specific test class
./gradlew test --tests "net.seitter.jfat.core.FatFileSystemTest"

# Run specific test method
./gradlew test --tests "net.seitter.jfat.core.FatFileSystemTest.testBasicFat32Operations"
```

#### Verbose Test Output
For detailed test progress and debugging information:
```bash
./gradlew test --info
./gradlew test --debug    # Very detailed output
./gradlew test --stacktrace  # Include stack traces for failures
```

### Test Categories

#### 1. FAT Filesystem Tests (`FatFileSystemTest`)
Comprehensive tests for all FAT variants:

- **Basic Operations**: File creation, reading, writing, deletion
- **Extended Operations**: Directory operations, nested directories, boundary conditions
- **Large File Operations**: Testing with files up to several MB in size

```bash
# Test basic operations on all FAT types
./gradlew test --tests "net.seitter.jfat.core.FatFileSystemTest.testBasicFat*Operations"

# Test large file operations
./gradlew test --tests "net.seitter.jfat.core.FatFileSystemTest.testLargeFile*Operations"
```

#### 2. Performance Tests (`FatPerformanceTest`)
Benchmark filesystem performance:

- **File Creation Performance**: Measures time to create multiple files
- **Large File I/O Performance**: Tests read/write speed for large files
- **Directory Traversal Performance**: Benchmarks recursive directory operations

```bash
# Run performance benchmarks
./gradlew test --tests "net.seitter.jfat.core.FatPerformanceTest"
```

#### 3. Utility Tests (`FatUtilsTest`)
Tests for low-level utility functions:

- Date/time encoding and decoding
- FAT filename handling (8.3 format)
- Binary data operations (little-endian integers)

#### 4. Type Detection Tests (`FatTypeDetectionTest`)
Verifies correct FAT type identification:

```bash
# Test FAT type detection
./gradlew test --tests "net.seitter.jfat.core.FatTypeDetectionTest"
```

### Standalone Testing Tool

For interactive testing with existing FAT images, use the standalone test tool:

```bash
# Test with your own FAT image
./gradlew runFatfsTest

# Or run with a custom image
./gradlew runFatfsTest --args="path/to/your/image.img"
```

This tool performs a comprehensive test suite on the specified FAT image and provides detailed output.

### Test Examples and Output

#### Sample Test Output
```
=== Testing basic operations on FAT32 ===
Creating FAT32 image (64MB)...
Mounting filesystem...
Testing basic file operations...
  Creating file in root directory...
  Writing data to file...
  Reading data from file...
  Deleting file...
FAT32 basic operations test completed successfully!
```

#### Performance Test Output
```
=== Testing file creation performance ===
Creating FAT32 image (32MB)...
Starting file creation test - creating 50 files of 5KB each...
  Created 10/50 files
  Created 20/50 files
  ...
Created 50 files of 5KB each in 1250ms
Average time per file: 25.0ms
Write throughput: 200.00 KB/s
```

### Test Configuration

Tests create temporary disk images in `build/test-images/` which are automatically cleaned up after test completion. The `.gitignore` file ensures these artifacts are not tracked in version control.

#### Test Timeouts
- Basic tests: 60 seconds
- Extended tests: 120 seconds  
- Performance tests: 120 seconds

#### Test Data Sizes
- Small files: 1-10KB
- Large files: 256KB-2MB (varies by FAT type)
- Disk images: 1MB (FAT12), 16MB (FAT16), 64MB (FAT32)

### Troubleshooting Tests

#### Common Issues

1. **Test Timeouts**: Increase timeout values in test classes if needed
2. **Disk Space**: Ensure sufficient space in `build/` directory for test images
3. **Permissions**: Verify write permissions in project directory

#### Debug Mode
Run tests with debug output to troubleshoot issues:
```bash
./gradlew test --tests "ClassName.methodName" --info --stacktrace
```

## CI/CD Pipeline

JFAT uses GitHub Actions for continuous integration and automated releases. The CI/CD pipeline includes:

### Continuous Integration (CI)

The CI workflow (`.github/workflows/ci.yml`) runs on every push and pull request:

- **Multi-Java Testing**: Tests against Java 11, 17, and 21
- **Comprehensive Testing**: Runs the full test suite including performance tests  
- **Build Verification**: Ensures the project builds correctly across all supported Java versions
- **Test Reporting**: Generates detailed test reports and uploads artifacts
- **Code Coverage**: Collects and reports test coverage using JaCoCo

### Security Scanning

The security workflow (`.github/workflows/security.yml`) performs:

- **Dependency Vulnerability Scanning**: Uses OWASP Dependency Check to identify vulnerable dependencies
- **CodeQL Analysis**: GitHub's semantic code analysis for finding security vulnerabilities
- **Scheduled Scans**: Weekly automated security scans

### Automated Releases

The release workflow (`.github/workflows/release.yml`) handles:

- **GitHub Package Registry**: Automatically publishes releases to GitHub Package Registry
- **Multi-Artifact Publishing**: Creates and uploads main JAR, sources JAR, and Javadoc JAR
- **Version Management**: Automatically updates version numbers from Git tags
- **Release Assets**: Attaches all artifacts to GitHub releases

### Creating a Release

To create a new release:

1. **Create and push a version tag**:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. **Create a GitHub release** from the tag, which will:
   - Trigger the automated build and test pipeline
   - Generate all JAR artifacts (main, sources, javadoc)
   - Publish to GitHub Package Registry
   - Attach artifacts to the GitHub release

3. **Manual release** (alternative):
   ```bash
   # Use GitHub Actions workflow dispatch
   gh workflow run release.yml -f version=1.0.0
   ```

### Build Status

Check the current build status and recent CI runs:

- **CI Status**: [![CI](https://github.com/jorgenseitter/jfat/actions/workflows/ci.yml/badge.svg)](https://github.com/jorgenseitter/jfat/actions/workflows/ci.yml)
- **Security**: [![Security](https://github.com/jorgenseitter/jfat/actions/workflows/security.yml/badge.svg)](https://github.com/jorgenseitter/jfat/actions/workflows/security.yml)
- **Release**: [![Release](https://github.com/jorgenseitter/jfat/actions/workflows/release.yml/badge.svg)](https://github.com/jorgenseitter/jfat/actions/workflows/release.yml)

## Building and Running Examples

### Create Test Disk Images
```bash
./gradlew createDiskImages
```

### Run Example Code
```bash
# Run the main example with existing fatfs file
./gradlew runExample

# Run interactive test tool
./gradlew runFatfsTest
```

## Project Structure

```
src/
├── main/java/net/seitter/jfat/
│   ├── core/          # Core FAT filesystem classes
│   ├── io/            # I/O and device access
│   ├── util/          # Utility classes and disk image creation
│   └── examples/      # Example usage and standalone tools
└── test/java/net/seitter/jfat/
    ├── core/          # Core functionality tests
    └── util/          # Utility function tests
```

## License

This project is licensed under the MIT License - see the LICENSE file for details. 