package optimalarborescence.memorymapper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class IntArrayMapper {

    /**
     * Save an int array into a memory-mapped file.
     * Each int is stored as 4 bytes (big- or little-endian depends on platform native order).
     */
    public static void saveArrayToMappedFile(int[] array, String fileName) throws IOException {
        // size in bytes
        long size = (long) array.length * Integer.BYTES;
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            // ensure file is the right length
            raf.setLength(size);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            mbb.order(ByteOrder.nativeOrder());
            for (int v : array) {
                mbb.putInt(v);
            }
            // force changes to disk
            mbb.force();
        }
    }

    /**
     * Load an int array from a memory-mapped file.
     */
    public static int[] loadArrayFromMappedFile(String fileName) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            long size = channel.size();
            if (size % Integer.BYTES != 0) {
                throw new IOException("Mapped file size is not a multiple of integer size");
            }
            int count = (int) (size / Integer.BYTES);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            mbb.order(ByteOrder.nativeOrder());
            int[] array = new int[count];
            for (int i = 0; i < count; i++) {
                array[i] = mbb.getInt();
            }
            return array;
        }
    }

    /**
     * Resize an int array.
     * @param original
     * @param newSize
     * @return
     */
    public static int[] resizeArray(int[] original, int newSize) {
        int[] newArray = new int[newSize];
        System.arraycopy(original, 0, newArray, 0, Math.min(original.length, newSize));
        return newArray;
    }


    /**
     * Save a single int element to a specific position in a memory-mapped file.
     * @param fileName
     * @param element
     * @param positionElements
     * @throws IOException
     */
    public static void saveElementToFileAtPosition(String fileName, int element, int positionElements) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            long position = (long) positionElements * Integer.BYTES;
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, Integer.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            mbb.putInt(element);
            mbb.force();
        }
    }

    /**
     * Append multiple int elements to the end of a memory-mapped file.
     * @param fileName
     * @param elements
     * @throws IOException
     */
    public static void appendElementsToFile(String fileName, int[] elements) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            long position = channel.size();
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, Integer.BYTES * elements.length);
            mbb.order(ByteOrder.nativeOrder());
            for (int element : elements) {
                mbb.putInt(element);
            }

            mbb.force();
        }
    }

    /**
     * Append a single int element to the end of a memory-mapped file.
     * @param fileName
     * @param element
     * @throws IOException
     */
    public static void appendElementToFile(String fileName, int element) throws IOException {
        appendElementsToFile(fileName, new int[]{element});
    }
    
}
