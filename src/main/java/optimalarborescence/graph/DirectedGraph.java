package optimalarborescence.graph;

import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;
import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.distance.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

public class DirectedGraph extends Graph {

    /** The nearest neighbour search algorithm used to find the nearest
     * neighbours for each node when adding it to the graph.
     */
    private NearestNeighbourSearchAlgorithm nnSearch;

    /** The maximum number of neighbours each node can have. */
    private int maxNumNeighbours;
    
    /** The file path to serialize the nearest neighbour search algorithm. */
    private String NNSearchSerializationFilePath = "nnsearch.dat"; // TODO - parameterizar

    /**
     * Constructs a directed graph using a nearest neighbour search algorithm to determine edges.
     * @param searchAlgorithm the nearest neighbour search algorithm to use
     * @param maxNumNeighbours the maximum number of neighbours each node can have
     */
    public DirectedGraph(NearestNeighbourSearchAlgorithm searchAlgorithm, int maxNumNeighbours) {
        super();
        this.nnSearch = searchAlgorithm;
        this.maxNumNeighbours = maxNumNeighbours;

        if (maxNumNeighbours <= 0) {
            throw new IllegalArgumentException("maxNumNeighbours must be greater than 0");
        }
    }

    /**
     * Constructs a directed graph from a base graph, using a nearest neighbour search algorithm to determine edges.
     * @param searchAlgorithm the nearest neighbour search algorithm to use
     * @param NNSearchFilePath the file path to serialize the nearest neighbour search algorithm
     * @param maxNumNeighbours the maximum number of neighbours each node can have
     * @param baseGraph the base graph from which to construct the directed graph
     */
    public DirectedGraph(NearestNeighbourSearchAlgorithm searchAlgorithm, String NNSearchFilePath, 
    int maxNumNeighbours, Graph baseGraph) {
    
        this(searchAlgorithm, maxNumNeighbours);
        for (Node node : baseGraph.getNodes()) {
            this.addNode(node);
        }
        this.NNSearchSerializationFilePath = NNSearchFilePath;
    }

    /**
     * Adds a node to the graph and connects it to all existing nodes.
     *
     * @param node the node to be added
     */
    @Override
    public void addNode(Node node) {
        super.addNode(node);

        List<Point> nearestNeighbors = nnSearch.neighbourSearch(node, this.maxNumNeighbours);
        nnSearch.storePoint(node);

        for (Point neighbor : nearestNeighbors) {
            if (neighbor instanceof Node) {
                Node neighborNode = (Node) neighbor;
                // TODO - double ou int para a distância?
                int distance = (int) nnSearch.getDistanceFunction().calculate(node.getBitArray(), neighborNode.getBitArray());
                // node.addNeighbor(neighborNode, distance);
                neighborNode.addNeighbor(node, distance);
                // super.addEdge(new Edge(node, neighborNode, distance));
                super.addEdge(new Edge(neighborNode, node, distance));
            }
        }
    }

    @Override
    public void exportEdgeListAndIndex(String edgeListFile, String indexFile) throws IOException {
        super.exportEdgeListAndIndex(edgeListFile, indexFile);

        try {
            FileOutputStream file = new FileOutputStream(NNSearchSerializationFilePath);
            ObjectOutputStream out = new ObjectOutputStream(file);
            out.writeObject(nnSearch);
            out.close();
        } 
        catch (IOException e) {
            throw new IOException("Failed to serialize Nearest Neighbour Search Algorithm: " + e.getMessage());
        }
    }

    /**
     * Static method to load a directed graph from a binary edge list and index files.
     * A nearest neighbour search algorithm is also loaded from a serialized file.
     * 
     * Reads the index file to reconstruct nodes with their IDs and MLST data,
     * then reads the edge list to reconstruct edges.
     * 
     * @param edgeListFile Path to the binary edge list file
     * @param indexFile Path to the binary index file
     * @param nnSearchFile Path to the serialized nearest neighbour search algorithm
     * @return A new Graph instance populated with nodes and edges
     */
    public static Graph loadFromEdgeListAndIndex(String edgeListFile, String indexFile, String nnSearchFile) throws IOException {

        // Load the nearest neighbour search algorithm from the serialized file
        NearestNeighbourSearchAlgorithm nnSearchAlg;
        try {
            FileInputStream file = new FileInputStream(nnSearchFile);
            ObjectInputStream in = new ObjectInputStream(file);
            nnSearchAlg = (NearestNeighbourSearchAlgorithm) in.readObject();
            in.close();
        } 
        catch (IOException e) {
            throw new IOException("Failed to deserialize Nearest Neighbour Search Algorithm: " + e.getMessage());
        }
        catch (ClassNotFoundException e) {
            throw new IOException("Class not found during deserialization: " + e.getMessage());
        }

        Graph g = Graph.loadFromEdgeListAndIndex(edgeListFile, indexFile);
        int PLACEHOLDER = 5; // maxNumNeighbours TODO - serializar

        DirectedGraph graph = new DirectedGraph(nnSearchAlg, nnSearchFile, PLACEHOLDER , g);
        return graph;
    }

}