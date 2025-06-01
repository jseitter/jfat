package net.seitter.jfat.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Provides low-level access to a device or file.
 */
public class DeviceAccess implements Closeable {
    
    private final RandomAccessFile randomAccessFile;
    private final FileChannel channel;
    private final String devicePath;
    
    /**
     * Creates a new DeviceAccess for the given device path.
     *
     * @param devicePath Path to the device or file (e.g., "/dev/sda" or "disk.img")
     * @throws IOException If the device cannot be opened
     */
    public DeviceAccess(String devicePath) throws IOException {
        this.devicePath = devicePath;
        this.randomAccessFile = new RandomAccessFile(devicePath, "rw");
        this.channel = randomAccessFile.getChannel();
    }
    
    /**
     * Reads data from the device at the specified offset.
     *
     * @param offset The offset to read from
     * @param size   The number of bytes to read
     * @return A byte array containing the read data
     * @throws IOException If an I/O error occurs
     */
    public byte[] read(long offset, int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        channel.position(offset);
        int bytesRead = channel.read(buffer);
        if (bytesRead < size) {
            throw new IOException("Could not read requested number of bytes");
        }
        buffer.flip();
        return buffer.array();
    }
    
    /**
     * Writes data to the device at the specified offset.
     *
     * @param offset The offset to write to
     * @param data   The data to write
     * @throws IOException If an I/O error occurs
     */
    public void write(long offset, byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        channel.position(offset);
        int bytesWritten = channel.write(buffer);
        if (bytesWritten < data.length) {
            throw new IOException("Could not write all data");
        }
        // Ensure data is written to the device
        channel.force(true);
    }
    
    /**
     * Gets the size of the device.
     *
     * @return The size in bytes
     * @throws IOException If an I/O error occurs
     */
    public long getSize() throws IOException {
        return channel.size();
    }
    
    /**
     * Gets the path of the device.
     *
     * @return The device path
     */
    public String getDevicePath() {
        return devicePath;
    }
    
    @Override
    public void close() throws IOException {
        channel.close();
        randomAccessFile.close();
    }
} 