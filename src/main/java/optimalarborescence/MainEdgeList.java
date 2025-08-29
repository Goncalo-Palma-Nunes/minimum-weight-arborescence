package optimalarborescence;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import optimalarborescence.graph.*;

public class MainEdgeList {
    public static void main(String[] args) {
        // Create nodes
        // Node node1 = new Node("ACTG", 1);
        // Node node2 = new Node("TGCA", 2);
        // Node node3 = new Node("GATC", 3);

        // // Create a CompleteGraph and add nodes
        // Graph graph = new Graph();
        // graph.addNode(node1);
        // graph.addNode(node2);
        // graph.addNode(node3);

        // // Add edges between nodes (example weights)
        // graph.addEdge(new Edge(node1, node2, 5));
        // graph.addEdge(new Edge(node2, node3, 3));
        // graph.addEdge(new Edge(node3, node1, 2));

        // // Print all edges
        // System.out.println("Graph edges:");
        // for (Edge edge : graph.getEdges()) {
        //     System.out.println(edge.getSource().getMLSTdata() + " -> " + edge.getDestination().getMLSTdata() + " (weight: " + edge.getWeight() + ")");
        // }

        // // Serialization and deserialization as before
        // RandomAccessFile file = null;
        // try {
        //     ByteArrayOutputStream bos = new ByteArrayOutputStream();
        //     ObjectOutputStream oos = new ObjectOutputStream(bos);
        //     oos.writeObject(graph);
        //     oos.flush();
        //     byte[] serializedGraphBytes = bos.toByteArray();
        //     oos.close();
        //     bos.close();

        //     file = new RandomAccessFile("graph.dat", "rw");
        //     FileChannel channel = file.getChannel();
        //     MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, serializedGraphBytes.length);
        //     buffer.put(serializedGraphBytes);

        //     buffer.position(0);
        //     byte[] data = new byte[serializedGraphBytes.length];
        //     buffer.get(data);

        //     ByteArrayInputStream bis = new ByteArrayInputStream(data);
        //     ObjectInputStream ois = new ObjectInputStream(bis);
        //     CompleteGraph deserializedGraph = (CompleteGraph) ois.readObject();
        //     ois.close();
        //     bis.close();

        //     System.out.println("\nDeserialized graph edges:");
        //     for (Edge edge : deserializedGraph.getEdges()) {
        //         System.out.println(edge.getSource().getMLSTdata() + " -> " + edge.getDestination().getMLSTdata() + " (weight: " + edge.getWeight() + ")");
        //     }
        // } catch (FileNotFoundException e) {
        //     System.err.println("(MainEdgeList.java) File not found: " + e.getMessage());
        // } catch (IOException e) {
        //     System.err.println("(MainEdgeList.java) IO error: " + e.getMessage());
        // } catch (ClassNotFoundException e) {
        //     System.err.println("(MainEdgeList.java) Class not found: " + e.getMessage());
        // } finally {
        //     if (file != null) {
        //         try {
        //             file.close();
        //         } catch (IOException e) {
        //             System.err.println("(MainEdgeList.java) Error closing file: " + e.getMessage());
        //         }
        //     }
        // }
    }
}
