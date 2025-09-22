package optimalarborescence.datastructure;

import java.util.*;
import optimalarborescence.graph.Edge;

public class UnionFind {
    protected int[] parent;
    protected int[] rank;
    protected int size;

    /**
     * Constructs a UnionFind instance with the specified number of elements. 
     * Can be used to keep track of weakly connected components in a graph.
     * 
     * @param n The number of elements in the UnionFind structure.
     */
    public UnionFind(int n) {
        this.size = n + 1;
        parent = new int[size];
        rank = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = i; // Each element is its own parent initially
            rank[i] = 1;   // Each set has rank 1 initially
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
     * This method uses union by rank to keep the tree flat.
     * 
     * @param a The first node.
     * @param b The second node.
     */
    public void union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);

        if (rootA != rootB) {
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
                rank[rootB] += rank[rootA];
            } else {
                parent[rootB] = rootA;
                rank[rootA] += rank[rootB];
            }
        }
    }
}