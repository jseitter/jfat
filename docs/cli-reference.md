# JFAT Command-Line Interface Reference

JFAT includes a comprehensive command-line interface that can be used standalone. The JAR file is configured to run the CLI by default.

## Installation

Download the latest JAR file from [GitHub Packages](https://github.com/jseitter/jfat/packages) or build from source:

```bash
./gradlew build
# The JAR will be available in build/libs/jfat-<version>.jar
```

## Usage

```bash
java -jar jfat.jar <command> [options]
```

## Commands

### `create <image> <type> <size>`
Create a new FAT filesystem image with Microsoft-recommended cluster sizes.

- `image`: Path to the output image file
- `type`: FAT type (`fat12`, `fat16`, or `fat32`)
- `size`: Size in MB

```bash
java -jar jfat.jar create disk.img fat32 1024    # 1GB FAT32 with optimal 4KB clusters
java -jar jfat.jar create floppy.img fat12 1     # 1MB FAT12 with 512-byte clusters
java -jar jfat.jar create storage.img fat16 128  # 128MB FAT16 with optimal 2KB clusters
```

**Cluster Size Optimization**: JFAT automatically selects optimal cluster sizes following Microsoft specifications:
- **FAT16**: 1KB (≤16MB), 2KB (≤128MB), 4KB (≤256MB), 8KB (≤512MB), etc.
- **FAT32**: 4KB (≤8GB), 8KB (≤16GB), 16KB (≤32GB), 32KB (>32GB)
- **FAT12**: 512-byte clusters (optimal for small volumes)

### `list <image> [path]` / `ls <image> [path]`
List contents of filesystem or directory.

- `image`: FAT image file
- `path`: Directory path to list (optional, defaults to root)

```bash
java -jar jfat.jar list disk.img
java -jar jfat.jar ls disk.img /documents
```

### `copy <src> <image> <dest>` / `cp <src> <image> <dest>`
Copy file or directory from local filesystem to FAT image.

- `src`: Source file/directory on local filesystem
- `image`: Target FAT image file
- `dest`: Destination path in FAT image

```bash
java -jar jfat.jar copy README.md disk.img /readme.txt
java -jar jfat.jar cp /home/user/documents disk.img /documents
```

### `mkdir <image> <path>`
Create directory in FAT image.

- `image`: FAT image file
- `path`: Directory path to create

```bash
java -jar jfat.jar mkdir disk.img /documents
java -jar jfat.jar mkdir disk.img /documents/projects
```

### `extract <image> <src> <dest>`
Extract file or directory from FAT image to local filesystem.

- `image`: FAT image file
- `src`: Source path in FAT image
- `dest`: Destination path on local filesystem

```bash
java -jar jfat.jar extract disk.img /readme.txt extracted_readme.txt
java -jar jfat.jar extract disk.img /documents ./extracted_documents
```

### `info <image>`
Show detailed filesystem information including cluster size analysis.

- `image`: FAT image file

```bash
java -jar jfat.jar info disk.img
```

**Enhanced Output** includes:
- **Cluster Size Information**: Shows actual cluster size and whether it's optimal
- **Validation Status**: Reports if cluster configuration follows FAT specifications
- **Filesystem Details**: FAT type, total size, sectors per cluster, etc.
- **Content Statistics**: Files, directories, used space

Example output:
```
FAT Filesystem Information
==================================================
Image file: disk.img

Filesystem Type: FAT32
Bytes per Sector: 512
Sectors per Cluster: 8
Cluster Size: 4096 bytes (4 KB)
Total Sectors: 2097152
Total Size: 1 GB

Cluster Size Validation:
------------------------------
Sectors per cluster valid: ✓ YES
Bytes per sector valid: ✓ YES
Cluster size ≤ 32MB: ✓ YES
Overall validation: ✓ PASSED
```

### `graph <image> [output.dot] [--expert]` / `dot <image> [output.dot] [--expert]`
Export filesystem structure as DOT graph.

- `image`: FAT image file
- `output.dot`: Output DOT file (optional, defaults to stdout)
- `--expert`: Include detailed FAT table and cluster chain information (optional)

```bash
java -jar jfat.jar graph disk.img filesystem.dot
java -jar jfat.jar dot disk.img | dot -Tpng > filesystem.png

# Expert mode with detailed FAT table and cluster information
java -jar jfat.jar graph disk.img expert_view.dot --expert
java -jar jfat.jar graph disk.img --expert | dot -Tpng > detailed_filesystem.png
```

**Expert Mode Features:**
- **FAT Table Summary**: Shows total clusters, used/free clusters, bad clusters, and EOF markers
- **Detailed Filesystem Information**: Displays cluster size, sectors per cluster, reserved sectors, and FAT table counts
- **Cluster Chain Visualization**: Shows the exact cluster numbers and chains for files and directories
- **Multi-Cluster File Analysis**: Visualizes how large files span multiple clusters with cluster-to-cluster connections

Expert mode is particularly useful for:
- **Filesystem Analysis**: Understanding internal structure and cluster allocation patterns
- **Digital Forensics**: Detailed visualization of file storage and potential fragmentation
- **Educational Purposes**: Learning how FAT filesystems organize data at the cluster level
- **Performance Debugging**: Identifying fragmentation and cluster utilization issues

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

### `interactive <image>` / `shell <image>`
Start an interactive shell for navigating and managing a FAT image with MS-DOS-like commands.

- `image`: FAT image file to open in interactive mode

```bash
java -jar jfat.jar interactive disk.img
```

**Supported commands:**
- `DIR` / `LS` — List directory contents
- `CD <path>` — Change directory
- `COPY <src> <dest>` / `CP` — Copy file (from local or within FAT)
- `DEL <file>` / `DELETE` — Delete file
- `MD <dir>` / `MKDIR` — Create directory
- `RD <dir>` / `RMDIR` — Remove directory
- `TYPE <file>` / `CAT` — Display file contents
- `PWD` — Show current directory
- `CLS` / `CLEAR` — Clear screen
- `HELP` — Show help
- `EXIT` / `QUIT` — Exit interactive shell

**Example session:**
```
$ java -jar jfat.jar interactive disk.img
JFAT Interactive Shell - FAT Filesystem Navigator
Type 'HELP' for available commands or 'EXIT' to quit
Current filesystem: FAT32

/> DIR
(empty directory)
/> MD docs
Directory created: docs
/> COPY /local/path/readme.txt /docs/readme.txt
Copied /local/path/readme.txt to /docs/readme.txt (1.2 KB)
/> DIR docs
readme.txt           
/> TYPE /docs/readme.txt
Hello, FAT filesystem!
/> EXIT
Goodbye!
```

### `help` / `--help` / `-h`
Show help message and usage information.

### `version` / `--version` / `-v`
Show version information.

## Advanced Usage

### Debug Mode
Enable debug output for troubleshooting:

```bash
# Using environment variable
JFAT_DEBUG=1 java -jar jfat.jar command

# Using system property
java -Djfat.debug=true -jar jfat.jar command
```

### Gradle Tasks
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

## Use Case Examples

### Creating a Bootable FAT32 Image
```bash
# Create a 1GB FAT32 image with optimal 4KB clusters
java -jar jfat.jar create bootable.img fat32 1024

# Create boot directory structure
java -jar jfat.jar mkdir bootable.img /boot
java -jar jfat.jar mkdir bootable.img /boot/grub

# Copy bootloader files
java -jar jfat.jar copy /path/to/bootloader bootable.img /boot/
```

### Digital Forensics
```bash
# Extract all files from a disk image
java -jar jfat.jar extract evidence.img / ./extracted_evidence/

# Generate filesystem structure for analysis
java -jar jfat.jar graph evidence.img evidence_structure.dot
dot -Tpng evidence_structure.dot -o evidence_structure.png

# Generate detailed forensic analysis with cluster chains and FAT table information
java -jar jfat.jar graph evidence.img evidence_detailed.dot --expert
dot -Tpng evidence_detailed.dot -o evidence_detailed.png

# Get detailed filesystem information including cluster analysis
java -jar jfat.jar info evidence.img
```

### Legacy System Compatibility
```bash
# Create FAT12 floppy disk image with 512-byte clusters
java -jar jfat.jar create floppy.img fat12 1

# Copy files for legacy systems
java -jar jfat.jar copy legacy_software floppy.img /
```

### Performance Analysis
```bash
# Create images with different cluster sizes for testing
java -jar jfat.jar create small_clusters.img fat32 1024    # Automatic 4KB clusters
java -jar jfat.jar create large_clusters.img fat32 16384   # Automatic 16KB clusters

# Analyze cluster utilization
java -jar jfat.jar info small_clusters.img
java -jar jfat.jar info large_clusters.img

# Visualize cluster chains for performance analysis
java -jar jfat.jar graph small_clusters.img analysis.dot --expert
```

## Error Handling

The CLI provides clear error messages and uses appropriate exit codes:
- `0`: Success
- `1`: General error (invalid arguments, file not found, etc.)

**Cluster Size Validation Errors:**
```bash
# Invalid cluster configuration will be rejected with descriptive messages
java -jar jfat.jar create invalid.img fat32 1024 --sectors-per-cluster=3
# Error: Invalid sectors per cluster: 3. Must be a power of 2 between 1 and 128 inclusive
```

Enable debug mode for detailed error information when troubleshooting issues.

## Cluster Size Optimization

JFAT now includes intelligent cluster size selection that follows Microsoft's FAT specifications for optimal performance:

### Supported Cluster Sizes

**All Sector Sizes (512, 1024, 2048, 4096 bytes):**
- Sectors per cluster: 1, 2, 4, 8, 16, 32, 64, 128 (powers of 2)
- Maximum cluster size: 32MB (FAT specification limit)

**Common Configurations:**
- **512-byte sectors**: 512B, 1KB, 2KB, 4KB, 8KB, 16KB, 32KB, 64KB clusters
- **4096-byte sectors**: 4KB, 8KB, 16KB, 32KB, 64KB, 128KB, 256KB, 512KB clusters

### Microsoft-Compliant Defaults

JFAT automatically selects optimal cluster sizes based on volume size and FAT type:

**FAT16 Recommendations:**
- ≤16MB: 1KB clusters (2 sectors)
- ≤128MB: 2KB clusters (4 sectors)  
- ≤256MB: 4KB clusters (8 sectors)
- ≤512MB: 8KB clusters (16 sectors)

**FAT32 Recommendations:**
- ≤260MB: 512-byte clusters (1 sector)
- ≤8GB: 4KB clusters (8 sectors)
- ≤16GB: 8KB clusters (16 sectors)
- ≤32GB: 16KB clusters (32 sectors)
- >32GB: 32KB clusters (64 sectors)

This ensures optimal performance while maintaining compatibility with all FAT-compliant systems. 