package optimalarborescence.graph;

import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.nearestneighbour.Point;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A symmetric distance matrix optimized for space efficiency.
 * Only stores the upper triangle of the matrix since relationships are symmetric.
 */
public class DistanceMatrix implements Serializable, PhylogeneticData {
    
    private double[] distances; // Stores upper triangle only
    private List<Node> nodes;
    private Map<Integer, Integer> nodeIdToIndex; // Maps node ID to matrix index
    private int size;
    private DistanceFunction distanceFunction;

    /**
     * Creates a distance matrix for the given nodes using the provided distance function.
     * 
     * @param nodes The nodes to include in the matrix
     * @param distFunction The distance function to compute distances
     */
    public DistanceMatrix(List<Node> nodes, DistanceFunction distFunction) {
        this.nodes = new ArrayList<>(nodes);
        this.size = nodes.size();
        this.nodeIdToIndex = new HashMap<>();
        this.distanceFunction = distFunction;
        
        // Calculate size for upper triangle: n*(n-1)/2
        int upperTriangleSize = size * (size - 1) / 2;
        this.distances = new double[upperTriangleSize];
        
        // Build node ID to index mapping
        for (int i = 0; i < size; i++) {
            nodeIdToIndex.put(nodes.get(i).getId(), i);
        }
        
        // Calculate distances for all pairs using the distance function
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double distance = distFunction.calculate(nodes.get(i).getMLSTdata(), nodes.get(j).getMLSTdata());
                distances[getIndex(i, j)] = distance;
            }
        }
    }

    public DistanceMatrix(DistanceFunction distFunction, List<Point<?>> points) {
        this.nodes = new ArrayList<>();
        for (Point<?> p : points) {
            this.nodes.add(new Node(p));
        }
        this.size = nodes.size();
        this.nodeIdToIndex = new HashMap<>();
        
        // Calculate size for upper triangle: n*(n-1)/2
        int upperTriangleSize = size * (size - 1) / 2;
        this.distances = new double[upperTriangleSize];
        
        // Build node ID to index mapping
        for (int i = 0; i < size; i++) {
            nodeIdToIndex.put(nodes.get(i).getId(), i);
        }
        
        // Calculate distances for all pairs using the distance function
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double distance = distFunction.calculate(nodes.get(i).getMLSTdata(), nodes.get(j).getMLSTdata());
                distances[getIndex(i, j)] = distance;
            }
        }
    }

    /**
     * Creates a distance matrix from a complete graph's edges.
     * 
     * @param graph The graph containing all pairwise edges
     */
    public DistanceMatrix(Graph graph) {
        this.nodes = new ArrayList<>(graph.getNodes());
        this.size = nodes.size();
        this.nodeIdToIndex = new HashMap<>();
        
        // Calculate size for upper triangle: n*(n-1)/2
        int upperTriangleSize = size * (size - 1) / 2;
        this.distances = new double[upperTriangleSize];
        
        // Build node ID to index mapping
        for (int i = 0; i < size; i++) {
            nodeIdToIndex.put(nodes.get(i).getId(), i);
        }
        
        // Populate distances from graph edges
        for (Edge edge : graph.getEdges()) {
            setDistance(edge.getSource(), edge.getDestination(), edge.getWeight());
        }
    }

    /**
     * Converts upper triangle index (i,j where i < j) to flat array index.
     * Formula: index = i*n - i*(i+1)/2 + j - i - 1
     */
    private int getIndex(int i, int j) {
        if (i == j) {
            throw new IllegalArgumentException("Cannot get distance from node to itself");
        }
        
        // Ensure i < j for upper triangle
        if (i > j) {
            int temp = i;
            i = j;
            j = temp;
        }
        
        // Calculate flat array index for upper triangle storage
        return i * size - i * (i + 1) / 2 + j - i - 1;
    }

    /**
     * Gets the distance between two nodes.
     * 
     * @param a First node
     * @param b Second node
     * @return The distance between the nodes, or 0 if they are the same node
     */
    public double getDistance(Node a, Node b) {
        Integer indexA = nodeIdToIndex.get(a.getId());
        Integer indexB = nodeIdToIndex.get(b.getId());
        
        if (indexA == null || indexB == null) {
            throw new IllegalArgumentException("Node not in distance matrix");
        }
        
        if (indexA.equals(indexB)) {
            return 0.0; // Distance to self is 0
        }
        
        return distances[getIndex(indexA, indexB)];
    }

    /**
     * Sets the distance between two nodes (symmetric).
     * 
     * @param a First node
     * @param b Second node
     * @param distance The distance to set
     */
    public void setDistance(Node a, Node b, double distance) {
        Integer indexA = nodeIdToIndex.get(a.getId());
        Integer indexB = nodeIdToIndex.get(b.getId());
        
        if (indexA == null || indexB == null) {
            throw new IllegalArgumentException("Node not in distance matrix");
        }
        
        if (indexA.equals(indexB)) {
            return; // Cannot set distance to self
        }
        
        distances[getIndex(indexA, indexB)] = distance;
    }

    /**
     * Gets all nodes in the matrix.
     * 
     * @return List of all nodes
     */
    public List<Node> getNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * Gets the number of nodes in the matrix.
     * 
     * @return Number of nodes
     */
    public int getSize() {
        return size;
    }

    /**
     * Gets the distance function used to create this matrix.
     * 
     * @return The distance function
     */
    public DistanceFunction getDistanceFunction() {
        return distanceFunction;
    }

    /**
     * Converts this distance matrix to a complete Graph object.
     * Creates edges for all node pairs with their distances.
     * 
     * @return A Graph containing all pairwise edges
     */
    public Graph toGraph() {
        Graph graph = new Graph();
        
        // Add all nodes
        for (Node node : nodes) {
            graph.addNode(node);
        }
        
        // Add all edges from upper triangle
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                Node source = nodes.get(i);
                Node destination = nodes.get(j);
                double distance = distances[getIndex(i, j)];
                
                // Add both directions for directed graph
                graph.addEdge(new Edge(source, destination, (int) distance));
                graph.addEdge(new Edge(destination, source, (int) distance));
            }
        }
        
        return graph;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DistanceMatrix with ").append(size).append(" nodes:\n");
        
        // Print header
        sb.append("      ");
        for (int j = 0; j < size; j++) {
            sb.append(String.format("%6d ", nodes.get(j).getId()));
        }
        sb.append("\n");
        
        // Print matrix
        for (int i = 0; i < size; i++) {
            sb.append(String.format("%6d ", nodes.get(i).getId()));
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    sb.append("   0.0 ");
                } else {
                    double distance = distances[getIndex(i, j)];
                    sb.append(String.format("%6.2f ", distance));
                }
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
