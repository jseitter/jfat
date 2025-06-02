# JFAT - Java FAT Filesystem Library

[![CI](https://github.com/jseitter/jfat/actions/workflows/ci.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/ci.yml)
[![Security](https://github.com/jseitter/jfat/actions/workflows/security.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/security.yml)
[![Release](https://github.com/jseitter/jfat/actions/workflows/release.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/release.yml)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/jseitter/jfat)](https://github.com/jseitter/jfat/releases/latest)
[![GitHub](https://img.shields.io/github/license/jseitter/jfat)](https://github.com/jseitter/jfat/blob/main/LICENSE)
[![Java](https://img.shields.io/badge/Java-11%2B-orange)](https://www.oracle.com/java/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-blue)](https://gradle.org/)
[![codecov](https://codecov.io/gh/jseitter/jfat/branch/main/graph/badge.svg)](https://codecov.io/gh/jseitter/jfat)

A comprehensive Java library for reading and writing FAT (File Allocation Table) filesystems with full support for FAT12, FAT16, and FAT32 formats.

## ✨ Key Features

- **🔧 Complete FAT Support**: Read/write operations on FAT12, FAT16, and FAT32
- **📁 Long Filename Support**: Full Unicode LFN support with automatic short name generation  
- **⚡ Intelligent Cluster Sizing**: Microsoft-compliant cluster size optimization for peak performance
- **🖥️ Command-Line Interface**: Comprehensive CLI with filesystem operations and analysis
- **🌐 Web Interface**: Modern browser-based interface with real-time visualization
- **🔍 Advanced Analysis**: Expert-mode visualization with cluster chain mapping and FAT table analysis
- **💻 Interactive Shell**: MS-DOS-like commands for intuitive filesystem navigation
- **🏗️ Device Flexibility**: Support for disk images, block devices, and custom storage backends
- **🧪 Production Ready**: Extensive test suite with performance benchmarks

## 🚀 Quick Start

### Installation

**Gradle:**
```gradle
repositories {
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

**Direct Download:**
Download the latest JAR from [GitHub Releases](https://github.com/jseitter/jfat/releases/latest)

### Command-Line Usage

```bash
# Create a FAT32 filesystem with optimal cluster size
java -jar jfat.jar create disk.img fat32 1024    # 1GB with automatic 4KB clusters

# Explore filesystem contents
java -jar jfat.jar list disk.img
java -jar jfat.jar info disk.img                 # Shows cluster size optimization details

# Interactive exploration
java -jar jfat.jar interactive disk.img

# Start web interface
java -jar jfat.jar webserver 8080               # Open http://localhost:8080
```

### Web Interface

Launch the modern browser-based interface for filesystem management:

```bash
# Start web server (production mode)
java -jar jfat.jar webserver 8080

# Start in development mode
java -jar jfat.jar web 3000 --dev
```

The web interface provides:
- **📊 Real-time Dashboard**: Filesystem statistics and health monitoring
- **📁 File Browser**: Drag-and-drop file management with directory navigation
- **📈 Graph Visualization**: Interactive cluster allocation and fragmentation analysis
- **⚙️ Image Manager**: Create, delete, and analyze FAT images
- **🔬 Expert Mode**: Detailed FAT table visualization for digital forensics

### Library Usage

```java
import net.seitter.jfat.core.FatFileSystem;
import net.seitter.jfat.io.DeviceAccess;
import net.seitter.jfat.util.DiskImageCreator;

// Create an optimized FAT32 image
DiskImageCreator.createDiskImage("disk.img", FatType.FAT32, 1024);  // Auto-selects 4KB clusters

// Mount and use the filesystem
try (DeviceAccess device = new DeviceAccess("disk.img");
     FatFileSystem fs = FatFileSystem.mount(device)) {
    
    // Work with long filenames and Unicode
    FatFile file = fs.createFile("/My 🚀 Project Files/документ.txt");
    file.write("Hello, FAT filesystem!".getBytes());
    
    // Analyze cluster configuration
    System.out.println(fs.getBootSector().getClusterSizeInfo());
}
```

## 🔬 Advanced Features

### Intelligent Cluster Size Management

JFAT automatically selects optimal cluster sizes following Microsoft specifications:

- **FAT16**: 1KB (≤16MB) → 2KB (≤128MB) → 4KB (≤256MB) → 8KB (≤512MB)
- **FAT32**: 4KB (≤8GB) → 8KB (≤16GB) → 16KB (≤32GB) → 32KB (>32GB)
- **Custom Configuration**: Support for custom sector sizes (512B-4KB) and cluster configurations

```bash
# View cluster size recommendations
java -jar jfat.jar info disk.img

# Output:
# Cluster Size: 4096 bytes (4 KB)
# Sectors per Cluster: 8
# Cluster size ≤ 32MB: ✓ YES
# Overall validation: ✓ PASSED
```

### Expert-Mode Filesystem Analysis

```bash
# Generate detailed filesystem visualization
java -jar jfat.jar graph disk.img analysis.dot --expert
dot -Tpng analysis.dot -o filesystem.png
```

Expert mode provides:
- **FAT Table Summary**: Cluster usage statistics and health metrics
- **Cluster Chain Visualization**: File fragmentation and allocation patterns  
- **Performance Analysis**: Optimal cluster size recommendations
- **Digital Forensics**: Detailed cluster-level file storage mapping

### Long Filename Support

Full Unicode support with automatic short name generation:

```java
// Create files with long Unicode names
FatFile unicodeFile = root.createFile("Документ с очень длинным именем файла.txt");
FatFile emojiFile = root.createFile("My 🚀 Project File.doc");

// JFAT automatically generates 8.3 short names: ДОКУМЕ~1.TXT, MY~1.DOC
```

## 📚 Documentation

- **[📋 CLI Reference](docs/cli-reference.md)**: Complete command-line interface guide
- **[🌐 Web Interface](docs/web-interface.md)**: Browser-based filesystem management
- **[🔧 API Guide](docs/api-guide.md)**: Programmatic usage and integration examples
- **[🧪 Testing Guide](docs/testing.md)**: Test suite structure and custom testing
- **[🔨 Development Guide](docs/development.md)**: Building from source and contributing
- **[🚀 CI/CD Guide](docs/ci-cd.md)**: Automated build and release processes

## 🛠️ Building from Source

```bash
git clone https://github.com/jseitter/jfat.git
cd jfat
./gradlew build

# Run the CLI
java -jar build/libs/jfat-*.jar help
```

## 🧪 Testing

```bash
# Run comprehensive test suite
./gradlew test

# Performance benchmarks
./gradlew test --tests "FatPerformanceTest"

# Test with your own FAT images
./gradlew runFatfsTest --args="path/to/your/image.img"
```

## 🎯 Use Cases

- **🔬 Digital Forensics**: Analyze FAT filesystems with cluster-level detail
- **⚙️ Embedded Systems**: Create optimized filesystems for resource-constrained devices
- **🗂️ Legacy Support**: Read/write legacy FAT12 floppy disk images
- **📦 System Integration**: Programmatic FAT filesystem manipulation in Java applications
- **🎓 Education**: Learn FAT filesystem internals with visualization tools

## 🏛️ Project Structure

```
src/main/java/net/seitter/jfat/
├── core/           # Core FAT filesystem implementation
├── io/             # Device access and I/O abstraction  
├── util/           # Cluster optimization and disk image creation
├── cli/            # Command-line interface with enhanced analysis
└── examples/       # Usage examples and standalone tools

docs/               # Comprehensive documentation
├── cli-reference.md    # Command-line guide
├── api-guide.md        # Programming interface
├── testing.md          # Test suite documentation
├── development.md      # Building and contributing
└── ci-cd.md           # Automated workflows
```

## 🤝 Contributing

We welcome contributions! See the [Development Guide](docs/development.md) for:

- Building from source
- Code style guidelines  
- Testing requirements
- Pull request process

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🏆 Recognition

JFAT implements the complete Microsoft FAT specification with modern Java best practices, providing both high-level convenience and low-level control for professional filesystem manipulation.

