package src;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

public class Main {
    

    public static void main(String[] args) {

        
        int fileSize = 1024; // Example size, adjust as needed
        byte[] serializedGraphBytes = new byte[fileSize]; // Replace with actual serialized graph        
        
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile("graph.dat", "rw");
            FileChannel channel = file.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);

            // Serialize your graph to bytes and write:
            buffer.put(serializedGraphBytes);

            // To read:
            buffer.position(0);
            byte[] data = new byte[fileSize];
            buffer.get(data);
            // Deserialize data back to your graph structure
        } catch (FileNotFoundException e) {
            System.err.println("(main.java) File not found: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("(main.java) IO error: " + e.getMessage());
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.err.println("(main.java) Error closing file: " + e.getMessage());
                }
            }
        }
    }
}
