package optimalarborescence;

import optimalarborescence.graph.*;
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // Create nodes
        Node nodeA = new Node("A", 0);
        Node nodeB = new Node("T", 1);
        Node nodeC = new Node("C", 2);

        // Create a Graph and add nodes
        Graph graph = new Graph() {};
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        // Add edges
        graph.addEdge(new Edge(nodeA, nodeB, 10));
        graph.addEdge(new Edge(nodeA, nodeC, 20));
        graph.addEdge(new Edge(nodeB, nodeC, 30));

        // Export to binary edge list and index files
        try {
            graph.exportEdgeListAndIndex("edges.bin", "index.bin");
            System.out.println("Graph exported to edges.bin and index.bin");
        } catch (IOException e) {
            System.err.println("Error exporting graph: " + e.getMessage());
        }

        // Read and print the edge list file
        try (DataInputStream edgeIn = new DataInputStream(new FileInputStream("edges.bin"))) {
            System.out.println("\nEdges read from edges.bin:");
            while (edgeIn.available() >= 8) { // 2 ints = 8 bytes
                int src = edgeIn.readInt();
                int dst = edgeIn.readInt();
                System.out.println("Edge: " + src + " -> " + dst);
            }
        } catch (IOException e) {
            System.err.println("Error reading edge list: " + e.getMessage());
        }

        // Read and print the index file
        try (DataInputStream indexIn = new DataInputStream(new FileInputStream("index.bin"))) {
            System.out.println("\nIndex read from index.bin:");
            int nodeId = 0;
            while (indexIn.available() >= 8) { // 1 long = 8 bytes
                long offset = indexIn.readLong();
                System.out.println("Node " + nodeId + " offset: " + offset);
                nodeId++;
            }
        } catch (IOException e) {
            System.err.println("Error reading index file: " + e.getMessage());
        }
    }
}