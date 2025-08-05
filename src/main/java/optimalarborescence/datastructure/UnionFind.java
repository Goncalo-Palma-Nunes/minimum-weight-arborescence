package optimalarborescence.datastructure;

import java.util.*;
import optimalarborescence.graph.Edge;

public class UnionFind {
    private int[] parent;
    private int[] size;

    // Constructor to initialize UnionFind with n elements
    public UnionFind(int n) {
        parent = new int[n];
        size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i; // Each element is its own parent initially
            size[i] = 1;   // Each set has size 1 initially
        }
    }

    // Find with path compression
    public int recursiveFind(int node) {
        if (parent[node] != node) {
            parent[node] = recursiveFind(parent[node]); // Path compression
        }
        return parent[node];
    }

    public int find(int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]]; // Path compression
            node = parent[node];
        }
        return node;
    }

    // Union by size
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
