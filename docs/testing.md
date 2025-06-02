# JFAT Testing Guide

JFAT includes a comprehensive test suite covering functionality, performance, and edge cases across all FAT variants.

## Test Structure

The test suite is organized into several categories:

- **Unit Tests**: Test individual utility functions and low-level operations
- **Integration Tests**: Test complete filesystem operations across FAT12, FAT16, and FAT32
- **Performance Tests**: Benchmark file operations, directory traversal, and large file handling
- **Long Filename Tests**: Verify LFN support with Unicode characters and long names
- **Cluster Size Tests**: Validate cluster size configurations and recommendations

## Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Categories
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

### Verbose Test Output
For detailed test progress and debugging information:
```bash
./gradlew test --info
./gradlew test --debug    # Very detailed output
./gradlew test --stacktrace  # Include stack traces for failures
```

## Test Categories

### 1. FAT Filesystem Tests (`FatFileSystemTest`)
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

**Sample Test Output:**
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

### 2. Long Filename Tests (`LfnSupportTest`)
Tests for long filename (LFN) support including:

- **Unicode Support**: Cyrillic, emoji, and mixed character sets
- **Long Name Handling**: 255-character filenames
- **Short Name Generation**: Windows-style ~1, ~2, etc.
- **LFN Entry Validation**: Checksum and sequence verification

```bash
# Test LFN functionality
./gradlew test --tests "net.seitter.jfat.core.LfnSupportTest"
```

**Key Test Cases:**
- Long filename creation with Unicode characters (Cyrillic "Тест файл", emojis "🚀")
- Mixed 8.3 and LFN usage in the same directory
- Conflict resolution for generated short names
- Checksum validation between LFN and 8.3 entries

### 3. Cluster Size Tests (`ClusterSizeTest`)
Tests for the new cluster size functionality:

- **Validation Tests**: Invalid cluster configurations
- **Recommendation Tests**: Microsoft-compliant cluster sizes
- **Compatibility Tests**: Cross-platform cluster size support

```bash
# Test cluster size functionality
./gradlew test --tests "net.seitter.jfat.core.ClusterSizeTest"
```

**Test Scenarios:**
```java
// Test invalid sectors per cluster (not power of 2)
assertThrows(IOException.class, () -> {
    DiskImageCreator.createDiskImage("invalid.img", FatType.FAT32, 1024, 512, 3);
});

// Test cluster size exceeding 32MB limit
assertThrows(IOException.class, () -> {
    DiskImageCreator.createDiskImage("toolarge.img", FatType.FAT32, 1024, 4096, 128);
});

// Test recommended cluster sizes
assertEquals(8, BootSector.getRecommendedSectorsPerCluster(1024L * 1024 * 1024, FatType.FAT32, 512));
```

### 4. Performance Tests (`FatPerformanceTest`)
Benchmark filesystem performance:

- **File Creation Performance**: Measures time to create multiple files
- **Large File I/O Performance**: Tests read/write speed for large files
- **Directory Traversal Performance**: Benchmarks recursive directory operations

```bash
# Run performance benchmarks
./gradlew test --tests "net.seitter.jfat.core.FatPerformanceTest"
```

**Sample Performance Output:**
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

### 5. Utility Tests (`FatUtilsTest`)
Tests for low-level utility functions:

- Date/time encoding and decoding
- FAT filename handling (8.3 format)
- Binary data operations (little-endian integers)
- UTF-16LE encoding/decoding for LFN support

### 6. Type Detection Tests (`FatTypeDetectionTest`)
Verifies correct FAT type identification:

```bash
# Test FAT type detection
./gradlew test --tests "net.seitter.jfat.core.FatTypeDetectionTest"
```

## Standalone Testing Tool

For interactive testing with existing FAT images, use the standalone test tool:

```bash
# Test with default image
./gradlew runFatfsTest

# Test with your own FAT image
./gradlew runFatfsTest --args="path/to/your/image.img"
```

This tool performs a comprehensive test suite on the specified FAT image and provides detailed output including:
- Filesystem information and cluster details
- File and directory operations
- Performance measurements
- LFN support verification

## Test Configuration

### Test Environment
Tests create temporary disk images in `build/test-images/` which are automatically cleaned up after test completion. The `.gitignore` file ensures these artifacts are not tracked in version control.

### Test Parameters
- **Test Timeouts**: 
  - Basic tests: 60 seconds
  - Extended tests: 120 seconds  
  - Performance tests: 120 seconds

- **Test Data Sizes**:
  - Small files: 1-10KB
  - Large files: 256KB-2MB (varies by FAT type)
  - Disk images: 1MB (FAT12), 16MB (FAT16), 64MB (FAT32)

### Custom Test Images
You can test with custom disk images by placing them in the `src/test/resources/` directory:

```bash
# Create custom test images
java -jar jfat.jar create src/test/resources/custom_fat32.img fat32 128
java -jar jfat.jar create src/test/resources/custom_fat16.img fat16 64

# Tests will automatically discover and use these images
./gradlew test
```

## Writing Custom Tests

### Basic Test Structure
```java
@Test
public void testCustomFunctionality() throws IOException {
    // Create test image
    String imagePath = "build/test-images/custom_test.img";
    DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 64);
    
    try (DeviceAccess device = new DeviceAccess(imagePath);
         FatFileSystem fs = FatFileSystem.mount(device)) {
        
        // Perform test operations
        FatDirectory root = fs.getRootDirectory();
        FatFile testFile = root.createFile("test.txt");
        testFile.write("test content".getBytes());
        
        // Verify results
        assertEquals("test.txt", testFile.getName());
        assertEquals(12, testFile.getSize());
        
    } finally {
        // Cleanup
        new File(imagePath).delete();
    }
}
```

### Cluster Size Testing
```java
@Test
public void testClusterSizeValidation() {
    // Test valid cluster configurations
    assertDoesNotThrow(() -> {
        DiskImageCreator.createDiskImage("valid.img", FatType.FAT32, 1024, 512, 8);
    });
    
    // Test invalid cluster configurations
    IOException exception = assertThrows(IOException.class, () -> {
        DiskImageCreator.createDiskImage("invalid.img", FatType.FAT32, 1024, 512, 3);
    });
    assertTrue(exception.getMessage().contains("Must be a power of 2"));
}
```

### Performance Testing
```java
@Test
public void testFileCreationPerformance() throws IOException {
    String imagePath = "build/test-images/perf_test.img";
    DiskImageCreator.createDiskImage(imagePath, FatType.FAT32, 64);
    
    try (DeviceAccess device = new DeviceAccess(imagePath);
         FatFileSystem fs = FatFileSystem.mount(device)) {
        
        FatDirectory root = fs.getRootDirectory();
        int fileCount = 100;
        byte[] testData = new byte[1024]; // 1KB per file
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < fileCount; i++) {
            FatFile file = root.createFile("file" + i + ".dat");
            file.write(testData);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double avgTimePerFile = (double) duration / fileCount;
        
        System.out.printf("Created %d files in %dms (%.2fms avg)%n", 
                         fileCount, duration, avgTimePerFile);
        
        // Performance assertion (adjust threshold as needed)
        assertTrue("File creation too slow", avgTimePerFile < 100);
    }
}
```

## Troubleshooting Tests

### Common Issues

1. **Test Timeouts**: 
   - Increase timeout values in test classes if needed
   - Check for infinite loops in filesystem operations

2. **Disk Space**: 
   - Ensure sufficient space in `build/` directory for test images
   - Monitor disk usage during large file tests

3. **Permissions**: 
   - Verify write permissions in project directory
   - Check file locks on Windows systems

4. **Memory Issues**:
   - Increase JVM heap size for large file tests: `./gradlew test -Xmx2g`
   - Monitor memory usage during performance tests

### Debug Mode
Run tests with debug output to troubleshoot issues:
```bash
# Enable JFAT debug logging
./gradlew test -Djfat.debug=true --tests "ClassName.methodName"

# Enable Gradle debug output
./gradlew test --tests "ClassName.methodName" --info --stacktrace

# Combine both for maximum debugging
./gradlew test -Djfat.debug=true --tests "ClassName.methodName" --info --stacktrace
```

### Test Data Verification
```bash
# Verify test images manually
java -jar jfat.jar info build/test-images/test_fat32.img
java -jar jfat.jar list build/test-images/test_fat32.img

# Compare with external tools (Linux/macOS)
file build/test-images/test_fat32.img
xxd -l 512 build/test-images/test_fat32.img  # View boot sector
```

## Continuous Integration

### GitHub Actions Integration
Tests run automatically on:
- Every push to main branch
- All pull requests
- Multiple Java versions (11, 17, 21)

### Test Reporting
- **JUnit XML**: Available in `build/test-results/test/`
- **HTML Reports**: Available in `build/reports/tests/test/`
- **Coverage Reports**: Generated by JaCoCo in `build/reports/jacoco/`

### Performance Monitoring
Performance tests track metrics over time to detect regressions:
- File creation throughput
- Large file I/O performance  
- Directory traversal speed
- Memory usage patterns

## Test Best Practices

1. **Always Clean Up**: Use try-finally or try-with-resources to ensure test images are deleted
2. **Use Unique Names**: Avoid conflicts between parallel test runs
3. **Test Edge Cases**: Empty files, maximum filename lengths, cluster boundaries
4. **Verify Cluster Alignment**: Ensure file operations respect cluster boundaries
5. **Test Error Conditions**: Invalid inputs, corrupt filesystems, full disks
6. **Document Performance**: Include timing expectations in performance tests
7. **Use Parameterized Tests**: Test the same functionality across all FAT types

This comprehensive testing guide ensures JFAT maintains high quality and reliability across all supported FAT filesystem variants and configurations. 