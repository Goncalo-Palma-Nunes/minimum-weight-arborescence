package optimalarborescence.datastructure;


public class UnionFindStronglyConnected extends UnionFind {
    private int[] weight;

    /**
     * Constructs a UnionFindStronglyConnected instance with the specified number of elements. 
     * Can be used to keep track of strongly connected components in a graph.
     * 
     * @param n The number of elements in the UnionFind structure.
     */
    public UnionFindStronglyConnected(int n) {
        super(n);
        this.weight = new int[this.size];
        for (int i = 0; i < size; i++) {
            this.weight[i] = 0;
        }
    }


    /**
     * Adds a constant w to the weight of all the elements of the set containing n.
     * 
     * @param n The element whose set's weights are to be increased.
     * @param w The weight to be added to each element in the set containing n.
     */
    public void addWeight(int n, int w) {
        int root = find(n);
        weight[root] += w;
    }

    /**
     * Subtracts a constant w from the weight of all the elements of the set containing n.
     * 
     * @param n The element whose set's weights are to be decreased.
     * @param w The weight to be subtracted from each element in the set containing n.
     */
    public void subtractWeight(int n, int w) {
        addWeight(n, -w);
    }

    /**
     * Subtracts the weight of the set containing n2 from the weight of the set containing n.
     * 
     * @param n The element whose set's weights are to be decreased.
     * @param n2 The element whose set's weights are to be subtracted from the set containing n.
     */
    public void minusWeight(int n, int n2) {
        weight[find(n)] -= weight[find(n2)];
    }

    /**
     * @param n The element whose set's weights are to be queried.
     * @return returns the accumulated weight for the set containing n.
     */
    public int findWeight(int n) {
        int w = this.weight[n];
        while (parent[n] != n) {
            n = parent[n];
            w += this.weight[n];
        }
        return w;
    }


    /**
     * Unites the sets containing the two specified nodes.
     * This method uses union by rank to keep the tree flat.
     * 
     * @param a The first node.
     * @param b The second node.
     */
    @Override
    public void union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);

        if (rootA != rootB) {
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
                rank[rootB] += rank[rootA];
                minusWeight(rootA, rootB);
            } else {
                parent[rootB] = rootA;
                rank[rootA] += rank[rootB];
                minusWeight(rootB, rootA);
            }
        }
    }

    /**
     * Finds the root of the set containing the specified node using path compression.
     * This method is more efficient than recursiveFind as it flattens the structure of the tree.
     * 
     * @param node The node to find the root for.
     * @return The root of the set containing the specified node.
     */
    @Override
    public int find(int node) {
        while (parent[node] != node) {
            weight[node] += weight[parent[node]];
            parent[node] = parent[parent[node]]; // Path compression
            node = parent[node];
        }
        return node;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UnionFindStronglyConnected {\n");
        sb.append(" Size: ").append(size).append("\n");
        sb.append(" Parent: ");
        for (int i = 0; i < size; i++) {
            sb.append(parent[i]).append(" ");
        }
        sb.append("\n Rank: ");
        for (int i = 0; i < size; i++) {
            sb.append(rank[i]).append(" ");
        }
        sb.append("\n Weight: ");
        for (int i = 0; i < size; i++) {
            sb.append(weight[i]).append(" ");
        }
        sb.append("\n}");
        return sb.toString();
    }

    public UnionFindStronglyConnected clone() {
        UnionFindStronglyConnected clone = new UnionFindStronglyConnected(this.size);
        System.arraycopy(this.parent, 0, clone.parent, 0, this.size);
        System.arraycopy(this.rank, 0, clone.rank, 0, this.size);
        System.arraycopy(this.weight, 0, clone.weight, 0, this.size);
        return clone;
    }

    @Override
    public void clear() {
        for (int i = 0; i < size; i++) {
            weight[i] = 0;
            parent[i] = i; // Reset each element to be its own parent
            rank[i] = 1;   // Reset rank to 1
        }
    }
}
