package optimalarborescence;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import optimalarborescence.graph.*;

public class MainAdjacencyList {
    public static void main(String[] args) {
        // Create a CompleteGraph and add a node
        CompleteGraph graph = new CompleteGraph();
        Node node = new Node("ACTG",1);
        graph.addNode(node);
        System.out.println(graph);

        RandomAccessFile file = null;
        try {
            // Serialize Graph to byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(graph);
            oos.flush();
            byte[] serializedGraphBytes = bos.toByteArray();
            oos.close();
            bos.close();

            // Write to memory-mapped file
            file = new RandomAccessFile("graph.dat", "rw");
            FileChannel channel = file.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, serializedGraphBytes.length);
            buffer.put(serializedGraphBytes);

            // Read from memory-mapped file
            buffer.position(0);
            byte[] data = new byte[serializedGraphBytes.length];
            buffer.get(data);

            // Deserialize Graph from byte array
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bis);
            CompleteGraph deserializedGraph = (CompleteGraph) ois.readObject();
            ois.close();
            bis.close();

            System.out.println("Graph data written and read successfully.");
            // System.out.println("Deserialized graph has " + deserializedGraph.getNumNodes() + " node(s).");
            System.out.println(deserializedGraph);
        } catch (FileNotFoundException e) {
            System.err.println("(Main.java) File not found: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("(Main.java) IO error: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("(Main.java) Class not found: " + e.getMessage());
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.err.println("(Main.java) Error closing file: " + e.getMessage());
                }
            }
        }
    }
}
