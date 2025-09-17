package optimalarborescence;

import optimalarborescence.distance.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.graph.*;

import java.util.List;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class DirectedGraphTest {

    static String SEQUENCE1 = "ATGC";
    static String SEQUENCE2 = "ATGA";
    static String SEQUENCE3 = "ATGT";
    static String SEQUENCE4 = "ATTC";
    static String SEQUENCE5 = "ATAC";
    static String SEQUENCE6 = "CTGC";
    static String SEQUENCE7 = "GTGC";
    static String SEQUENCE8 = "CTGG";
    static String SEQUENCE9 = "CGCG";
    static final int numSequences = 9;

    static String A = "A";
    static String T = "T";
    static String C = "C";
    static String G = "G";
    static HammingDistance hd = new HammingDistance();

    public static void main(String[] args) {

        String[] sequences = {SEQUENCE1, SEQUENCE2, SEQUENCE3, SEQUENCE4, SEQUENCE5, SEQUENCE6, SEQUENCE7, SEQUENCE8, SEQUENCE9};
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < sequences.length; i++) {
            String seq = sequences[i];
            nodes.add(new Node(seq, i));
        }

        // print sequences
        for (Node node : nodes) {
            System.out.println("Node ID: " + node.getId() + ", Sequence: " + node.getSequence());
        }

        // Compute distance matrix between the 9 sequences using hd
        int[][] distanceMatrix = new int[numSequences][numSequences];
        for (int i = 0; i < numSequences; i++) {
            for (int j = 0; j < numSequences; j++) {
                if (i != j) {
                    distanceMatrix[i][j] = (int) hd.calculate(nodes.get(i).getBitArray(), nodes.get(j).getBitArray());
                }
            }
        }

        // print the distance matrix
        System.out.println("Distance Matrix:");
        for (int i = 0; i < numSequences; i++) {
            for (int j = 0; j < numSequences; j++) {
                System.out.print(distanceMatrix[i][j] + "\t");
            }
            System.out.println();
        }

        int radius = 2;
        LSH lsh = new LSH(2, 6, 0, 3,
                        new HammingDistance(), radius);
        int maxNumNeighbours = 4;
        Graph g = new DirectedGraph(lsh, maxNumNeighbours);
        System.out.println("Graph created with max " + maxNumNeighbours + " neighbors. Max distance between nodes is " + radius);

        for (Node node : nodes) {
            g.addNode(node);
        }

        System.out.println(g);

        System.out.println("Saving Graph to binary edge list and index files...");

        // Export to binary edge list and index files
        try {
            g.exportEdgeListAndIndex("edges.bin", "index.bin");
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

        // Load the graph from the binary files
        try {
            System.out.println("Loading graph from binary files...");
            Graph loadedGraph = Graph.loadFromEdgeListAndIndex("edges.bin", "index.bin");
            System.out.println("Graph loaded from edges.bin and index.bin");
            System.out.println(loadedGraph);
        } catch (IOException e) {
            System.err.println("Error loading graph: " + e.getMessage());
        }
    }

}


