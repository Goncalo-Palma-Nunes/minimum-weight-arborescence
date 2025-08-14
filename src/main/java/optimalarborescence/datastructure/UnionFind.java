package optimalarborescence.datastructure;

import java.util.*;
import optimalarborescence.graph.Edge;

public class UnionFind {
    private int[] parent;
    private int[] size;

    /**
     * Constructs a UnionFind instance with the specified number of elements.
     * 
     * @param n The number of elements in the UnionFind structure.
     */
    public UnionFind(int n) {
        parent = new int[n];
        size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i; // Each element is its own parent initially
            size[i] = 1;   // Each set has size 1 initially
        }
    }

    /**
     * Recursively finds the root of the set containing the specified node.
     * 
     * @param node The node to find the root for.
     * @return The root of the set containing the specified node.
     */
    public int recursiveFind(int node) {
        if (parent[node] != node) {
            parent[node] = recursiveFind(parent[node]); // Path compression
        }
        return parent[node];
    }


    /**
     * Finds the root of the set containing the specified node using path compression.
     * This method is more efficient than recursiveFind as it flattens the structure of the tree.
     * 
     * @param node The node to find the root for.
     * @return The root of the set containing the specified node.
     */
    public int find(int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]]; // Path compression
            node = parent[node];
        }
        return node;
    }

    /**
     * Unites the sets containing the two specified nodes.
     * This method uses union by size to keep the tree flat.
     * 
     * @param a The first node.
     * @param b The second node.
     */
    public void union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);

        if (rootA != rootB) {
            if (size[rootA] < size[rootB]) {
                parent[rootA] = rootB;
                size[rootB] += size[rootA];
            } else {
                parent[rootB] = rootA;
                size[rootA] += size[rootB];
            }
        }
    }

    // // // // Example usage of Edge's compareTo method
    // // // public void processEdges(List<Edge> edges) {
    // // //     Collections.sort(edges); // Sort edges using Edge's compareTo
    // // //     for (Edge edge : edges) {
    // // //         int u = edge.getSource().getId(); // Assuming Node has a getId() method
    // // //         int v = edge.getDestination().getId(); // Assuming Node has a getId() method

    // // //         // Union the sets containing u and v
    // // //         if (find(u) != find(v)) {
    // // //             union(u, v);
    // // //             System.out.println("Edge added: " + edge);
    // // //         }
    // // //     }
    // // // }

}