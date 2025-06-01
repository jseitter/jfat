# Long Filename (LFN) Support Architecture

## Overview

JFAT now supports Long Filenames (LFN), which extend FAT filesystem capabilities beyond the traditional 8.3 filename format. This implementation allows filenames up to 255 characters with Unicode support while maintaining backward compatibility.

## Architecture Components

### 1. Core Classes

#### `LfnEntry`
- **Purpose**: Represents a single Long Filename directory entry
- **Features**:
  - Stores up to 13 UTF-16 characters per entry
  - Manages sequence numbers and checksum validation
  - Handles UTF-16LE encoding/decoding
  - Supports proper null termination and 0xFFFF padding

#### `LfnProcessor`
- **Purpose**: Central processor for LFN operations
- **Features**:
  - Parses directory data with LFN awareness
  - Reconstructs long filenames from LFN entry sequences
  - Generates LFN entries for storage
  - Creates unique 8.3 short names with numeric tails (~1, ~2, etc.)
  - Validates LFN integrity with checksum verification

#### `DirectoryEntryResult`
- **Purpose**: Wrapper for parsed directory entries with LFN information
- **Features**:
  - Contains both long and short filenames
  - Tracks entry offsets and space usage
  - Provides display name selection logic

### 2. Enhanced Core Classes

#### `FatDirectory` (Enhanced)
- **New Features**:
  - LFN-aware directory parsing using `LfnProcessor`
  - Support for creating files/directories with long names
  - Consecutive entry allocation for LFN sequences
  - Short name conflict resolution
  - Mixed 8.3 and LFN entry handling

#### `FatEntry` (Enhanced)
- **New Constants**:
  ```java
  public static final int ATTR_LONG_NAME = 0x0F;
  public static final int LFN_CHARS_PER_ENTRY = 13;
  public static final int LFN_MAX_FILENAME_LENGTH = 255;
  ```

#### `FatUtils` (Enhanced)
- **New Methods**:
  - `requiresLongFilename()`: Determines if LFN is needed
  - `toValidShortName()`: Converts to valid 8.3 format
  - `readUtf16Le()/writeUtf16Le()`: Unicode string handling

## Technical Implementation

### LFN Entry Structure

Each LFN entry is 32 bytes with this layout:

```
Offset  Size  Description
------  ----  -----------
0       1     Sequence number (1-based, 0x40 = last entry)
1       10    Characters 1-5 (UTF-16LE)
11      1     Attributes (always 0x0F)
12      1     Type (always 0)
13      1     Checksum of 8.3 name
14      12    Characters 6-11 (UTF-16LE)
26      2     First cluster (always 0)
28      4     Characters 12-13 (UTF-16LE)
```

### Directory Layout

Long filenames are stored as sequences in the directory:

```
[LFN Entry N] (last entry, sequence N)
[LFN Entry N-1]
...
[LFN Entry 1]
[8.3 Directory Entry]
```

### Checksum Algorithm

The LFN checksum ensures integrity between LFN entries and their corresponding 8.3 entry:

```java
int checksum = 0;
for (byte b : shortName) {
    checksum = (((checksum & 1) << 7) + (checksum >> 1) + (b & 0xFF)) & 0xFF;
}
```

### Short Name Generation

For long filenames, JFAT generates unique 8.3 names using Windows-style conventions:

1. Remove invalid characters
2. Convert to uppercase
3. Truncate to 8.3 format
4. Add numeric tail (~1, ~2, etc.) if conflicts exist

Example: `"My Document.txt"` → `"MYDOCU~1.TXT"`

## Usage Examples

### Creating Files with Long Names

```java
try (FatFileSystem fs = FatFileSystem.mount(device)) {
    FatDirectory root = fs.getRootDirectory();
    
    // Create file with long name
    FatFile file = root.createFile("My Very Long Document Name.txt");
    file.write("Content".getBytes());
    
    // Access by long or short name
    FatFile same = root.getEntry("My Very Long Document Name.txt");
    FatFile byShort = root.getEntry("MYVERY~1.TXT");  // Auto-generated short name
}
```

### Unicode Support

```java
// Create files with Unicode characters
FatFile unicode = root.createFile("Документ файл.txt");  // Cyrillic
FatFile emoji = root.createFile("Test 🚀 file.txt");     // Emoji
FatFile mixed = root.createFile("café résumé.doc");      // Accented chars
```

### Mixed 8.3 and LFN

```java
// Both formats work seamlessly
root.createFile("SHORT.TXT");                    // 8.3 format (no LFN)
root.createFile("medium_filename.doc");          // Requires LFN
root.createFile("Very Long Filename.pdf");       // Requires LFN

// All files accessible through directory listing
List<FatEntry> entries = root.list();
```

## Backward Compatibility

### Legacy Systems

- 8.3 short names remain fully functional
- Legacy systems see auto-generated short names
- No breaking changes to existing 8.3 operations

### Standards Compliance

- Follows Microsoft LFN specification
- Compatible with Windows 95+ implementations
- Proper Unicode (UTF-16LE) encoding
- Correct checksum and sequence validation

## Performance Considerations

### Space Usage

- Each LFN entry consumes 32 bytes (same as regular entry)
- Long filenames require multiple entries:
  - 1-13 chars: 1 LFN entry + 1 regular entry = 64 bytes
  - 14-26 chars: 2 LFN entries + 1 regular entry = 96 bytes
  - 27-39 chars: 3 LFN entries + 1 regular entry = 128 bytes

### Directory Operations

- Directory parsing includes LFN reconstruction
- Entry creation allocates consecutive slots
- Directory expansion handled automatically

## Testing

The LFN implementation includes comprehensive tests:

```bash
# Run LFN-specific tests
./gradlew test --tests "LfnSupportTest"

# Test Unicode filenames
./gradlew test --tests "LfnSupportTest.testUnicodeFilenames"

# Test mixed 8.3 and LFN usage
./gradlew test --tests "LfnSupportTest.testMixedFilenames"
```

## CLI Integration

The command-line interface automatically supports long filenames:

```bash
# Create files with long names
java -jar jfat.jar copy "My Long Document.txt" disk.img "/documents/"

# Interactive shell supports LFN
java -jar jfat.jar interactive disk.img
/> COPY "local file with spaces.txt" "/long filename.txt"
/> DIR
```

## Migration Path

### For Existing Code

1. **No Changes Required**: Existing 8.3 operations continue working
2. **Opt-in LFN**: Use long filenames where needed
3. **Transparent Handling**: Directory operations automatically handle both formats

### For New Development

1. **Use Long Filenames**: Take advantage of full filename support
2. **Unicode Support**: Handle international characters properly
3. **Modern Interface**: Use intuitive filename operations

## Error Handling

### Common Scenarios

- **Invalid Characters**: Automatically cleaned in short name generation
- **Length Limits**: 255 character maximum enforced
- **Directory Full**: Automatic directory expansion
- **Checksum Mismatch**: Invalid LFN sequences ignored gracefully

### Debugging

Enable debug mode to see LFN processing:

```bash
JFAT_DEBUG=1 java -jar jfat.jar command
```

Debug output includes:
- LFN entry parsing details
- Short name generation process
- Checksum validation results
- Directory space allocation

## Future Enhancements

### Planned Features

1. **LFN Defragmentation**: Optimize LFN entry placement
2. **Performance Optimization**: Cache LFN parsing results
3. **Extended Attributes**: Support for additional metadata
4. **Case Preservation**: Maintain original filename casing

### Compatibility

- FAT12/16/32 support across all LFN operations
- Windows/Linux/macOS interoperability
- DOS compatibility mode for legacy systems 