package optimalarborescence.datastructure;

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

    /** Gets the size of the UnionFind structure. */
    public int getSize() {
        return size;
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


    /**
     * Unites the sets containing rep and other, forcing rep to be the root
     * of the resulting set regardless of rank.
     *
     * @param rep The element that should become the representative of the merged set.
     * @param other The element whose set is merged into rep's set.
     */
    public void unionForceRep(int rep, int other) {
        int rootRep = find(rep);
        int rootOther = find(other);
        if (rootRep != rootOther) {
            parent[rootOther] = rootRep;
            rank[rootRep] += rank[rootOther];
        }
    }

    /**
     * Clears the UnionFind structure, resetting all elements to be their own parents and ranks to 1.
     */
    public void clear() {
        for (int i = 0; i < size; i++) {
            parent[i] = i; // Reset each element to be its own parent
            rank[i] = 1;   // Reset rank to 1
        }
    }

    public void resize(int newSize) {
        if (newSize >= size) {
            int[] newParent = new int[newSize + 1];
            int[] newRank = new int[newSize + 1];
            System.arraycopy(parent, 0, newParent, 0, size);
            System.arraycopy(rank, 0, newRank, 0, size);
            for (int i = size; i <= newSize; i++) {
                newParent[i] = i; // Each new element is its own parent
                newRank[i] = 1;   // Each new set has rank 1
            }
            parent = newParent;
            rank = newRank;
            size = newSize + 1;
        }
    }
}