# JFAT - Java FAT Filesystem Library

[![CI](https://github.com/jseitter/jfat/actions/workflows/ci.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/ci.yml)
[![Security](https://github.com/jseitter/jfat/actions/workflows/security.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/security.yml)
[![Release](https://github.com/jseitter/jfat/actions/workflows/release.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/release.yml)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/jseitter/jfat)](https://github.com/jseitter/jfat/releases/latest)
[![GitHub](https://img.shields.io/github/license/jseitter/jfat)](https://github.com/jseitter/jfat/blob/main/LICENSE)
[![Java](https://img.shields.io/badge/Java-11%2B-orange)](https://www.oracle.com/java/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-blue)](https://gradle.org/)
[![codecov](https://codecov.io/gh/jseitter/jfat/branch/main/graph/badge.svg)](https://codecov.io/gh/jseitter/jfat)

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

## Quick Start

JFAT provides both a Java library API and a command-line interface for working with FAT filesystems.

### Command-Line Interface

The easiest way to get started is using the command-line interface:

```bash
# Create a FAT32 filesystem image (64MB)
java -jar jfat.jar create mydisk.img fat32 64

# List filesystem contents
java -jar jfat.jar list mydisk.img

# Create directories
java -jar jfat.jar mkdir mydisk.img /documents

# Copy files from local filesystem to FAT image
java -jar jfat.jar copy README.md mydisk.img /documents/readme.txt

# Extract files from FAT image to local filesystem
java -jar jfat.jar extract mydisk.img /documents/readme.txt extracted_readme.txt

# Show filesystem information
java -jar jfat.jar info mydisk.img

# Export filesystem structure as DOT graph
java -jar jfat.jar graph mydisk.img filesystem.dot
# Render the graph: dot -Tpng filesystem.dot -o filesystem.png
```

### Library API

## Command-Line Interface Reference

JFAT includes a comprehensive command-line interface that can be used standalone. The JAR file is configured to run the CLI by default.

### Installation

Download the latest JAR file from [GitHub Packages](https://github.com/jseitter/jfat/packages) or build from source:

```bash
./gradlew build
# The JAR will be available in build/libs/jfat-<version>.jar
```

### Usage

```bash
java -jar jfat.jar <command> [options]
```

### Commands

#### `create <image> <type> <size>`
Create a new FAT filesystem image.

- `image`: Path to the output image file
- `type`: FAT type (`fat12`, `fat16`, or `fat32`)
- `size`: Size in MB

```bash
java -jar jfat.jar create disk.img fat32 64
java -jar jfat.jar create floppy.img fat12 1
java -jar jfat.jar create storage.img fat16 32
```

#### `list <image> [path]` / `ls <image> [path]`
List contents of filesystem or directory.

- `image`: FAT image file
- `path`: Directory path to list (optional, defaults to root)

```bash
java -jar jfat.jar list disk.img
java -jar jfat.jar ls disk.img /documents
```

#### `copy <src> <image> <dest>` / `cp <src> <image> <dest>`
Copy file or directory from local filesystem to FAT image.

- `src`: Source file/directory on local filesystem
- `image`: Target FAT image file
- `dest`: Destination path in FAT image

```bash
java -jar jfat.jar copy README.md disk.img /readme.txt
java -jar jfat.jar cp /home/user/documents disk.img /documents
```

#### `mkdir <image> <path>`
Create directory in FAT image.

- `image`: FAT image file
- `path`: Directory path to create

```bash
java -jar jfat.jar mkdir disk.img /documents
java -jar jfat.jar mkdir disk.img /documents/projects
```

#### `extract <image> <src> <dest>`
Extract file or directory from FAT image to local filesystem.

- `image`: FAT image file
- `src`: Source path in FAT image
- `dest`: Destination path on local filesystem

```bash
java -jar jfat.jar extract disk.img /readme.txt extracted_readme.txt
java -jar jfat.jar extract disk.img /documents ./extracted_documents
```

#### `info <image>`
Show detailed filesystem information.

- `image`: FAT image file

```bash
java -jar jfat.jar info disk.img
```

Output includes:
- FAT type and technical parameters
- Cluster and sector information
- Content statistics (files, directories, used space)

#### `graph <image> [output.dot]` / `dot <image> [output.dot]`
Export filesystem structure as DOT graph.

- `image`: FAT image file
- `output.dot`: Output DOT file (optional, defaults to stdout)

```bash
java -jar jfat.jar graph disk.img filesystem.dot
java -jar jfat.jar dot disk.img | dot -Tpng > filesystem.png
```

To render the graph, use Graphviz:
```bash
# Install Graphviz (macOS)
brew install graphviz

# Install Graphviz (Ubuntu/Debian)
sudo apt-get install graphviz

# Render as PNG
dot -Tpng filesystem.dot -o filesystem.png

# Render as SVG
dot -Tsvg filesystem.dot -o filesystem.svg
```

#### `help` / `--help` / `-h`
Show help message and usage information.

#### `version` / `--version` / `-v`
Show version information.

### Advanced Usage

#### Debug Mode
Enable debug output for troubleshooting:

```bash
# Using environment variable
JFAT_DEBUG=1 java -jar jfat.jar command

# Using system property
java -Djfat.debug=true -jar jfat.jar command
```

#### Gradle Tasks
If building from source, you can also use Gradle tasks:

```bash
# Run CLI via Gradle
./gradlew runCLI --args="help"
./gradlew runCLI --args="create test.img fat32 32"

# Run other utilities
./gradlew runExample
./gradlew runFatfsTest
./gradlew createDiskImages
```

### Examples and Use Cases

#### Creating a Bootable FAT32 Image
```bash
# Create a 64MB FAT32 image
java -jar jfat.jar create bootable.img fat32 64

# Create boot directory structure
java -jar jfat.jar mkdir bootable.img /boot
java -jar jfat.jar mkdir bootable.img /boot/grub

# Copy bootloader files
java -jar jfat.jar copy /path/to/bootloader bootable.img /boot/
```

#### Digital Forensics
```bash
# Extract all files from a disk image
java -jar jfat.jar extract evidence.img / ./extracted_evidence/

# Generate filesystem structure for analysis
java -jar jfat.jar graph evidence.img evidence_structure.dot
dot -Tpng evidence_structure.dot -o evidence_structure.png

# Get detailed filesystem information
java -jar jfat.jar info evidence.img
```

#### Legacy System Compatibility
```bash
# Create FAT12 floppy disk image
java -jar jfat.jar create floppy.img fat12 1

# Copy files for legacy systems
java -jar jfat.jar copy legacy_software floppy.img /
```

### Error Handling

The CLI provides clear error messages and uses appropriate exit codes:
- `0`: Success
- `1`: General error (invalid arguments, file not found, etc.)

Enable debug mode for detailed error information when troubleshooting issues.

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
- **Version Management**: Automatically updates version numbers from Git tags using a dedicated `version.txt` file
- **Release Assets**: Attaches all artifacts to GitHub releases
- **Environment Protection**: Uses GitHub environments for deployment safety with optional approval gates

## Version Management

JFAT uses a simple and reliable versioning approach:
- **Version File**: Project version is stored in `version.txt` 
- **Gradle Integration**: `build.gradle` reads version from this file using `file('version.txt').text.trim()`
- **Release Automation**: GitHub Actions simply overwrites `version.txt` during releases (no complex `sed` operations)
- **Single Source of Truth**: One file contains the canonical version number

### GitHub Environments

JFAT uses GitHub environments for deployment safety and audit trails:

- **Staging Environment**: Used during CI builds for verification (automatic)
- **Production Environment**: Used for releases with optional manual approval gates

As of [GitHub's May 15, 2025 update](https://github.blog/changelog/2025-05-15-new-releases-for-github-actions/), Actions environments are now available for all plans in private repositories, allowing for enhanced deployment protection.

See `.github/environments.md` for detailed environment configuration instructions.

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