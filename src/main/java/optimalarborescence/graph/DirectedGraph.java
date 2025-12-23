package optimalarborescence.graph;

import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;
import optimalarborescence.nearestneighbour.Point;

import java.util.List;
import java.util.ArrayList;

public class DirectedGraph<T> extends Graph {

    /** The nearest neighbour search algorithm used to find the nearest
     * neighbours for each node when adding it to the graph.
     */
    private NearestNeighbourSearchAlgorithm<T> nnSearch;

    /** The maximum number of neighbours each node can have. */
    private int maxNumNeighbours;
    
    /** The file path to serialize the nearest neighbour search algorithm. */
    // private String NNSearchSerializationFilePath = "nnsearch.dat"; // TODO - parameterizar

    /**
     * Constructs a directed graph using a nearest neighbour search algorithm to determine edges.
     * @param searchAlgorithm the nearest neighbour search algorithm to use
     * @param maxNumNeighbours the maximum number of neighbours each node can have
     */
    public DirectedGraph(NearestNeighbourSearchAlgorithm<T> searchAlgorithm, int maxNumNeighbours) {
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
    public DirectedGraph(NearestNeighbourSearchAlgorithm<T> searchAlgorithm, int maxNumNeighbours, Graph baseGraph) {
    
        this(searchAlgorithm, maxNumNeighbours);
        for (Node node : baseGraph.getNodes()) {
            this.addNode(node);
        }
        // this.NNSearchSerializationFilePath = NNSearchFilePath;
    }

    public DirectedGraph(NearestNeighbourSearchAlgorithm<T> searchAlgorithm, int maxNumNeighbours, List<Point<T>> points) {
        this(searchAlgorithm, maxNumNeighbours);
        for (Point<T> point : points) {
            Node node = new Node(point);
            point.setNode(node);
            this.addNode(node);
        }
    }

    public DirectedGraph(int maxNumNeighbours, List<Edge> edges, NearestNeighbourSearchAlgorithm<T> searchAlgorithm) {
        this(searchAlgorithm, maxNumNeighbours);
        for (Edge edge : edges) {
            Node fromNode = edge.getSource();
            Node toNode = edge.getDestination();
            super.addNode(fromNode);
            super.addNode(toNode);
            super.addEdge(edge);
        }
    }

    /**
     * Adds a node to the graph and connects it to all existing nodes.
     *
     * @param node the node to be added
     */
    @Override
    public void addNode(Node node) {
        super.addNode(node);

        @SuppressWarnings("unchecked")
        Point<T> typedPoint = (Point<T>) node.getPoint();
        
        List<Point<T>> nearestNeighbors = nnSearch.neighbourSearch(typedPoint, this.maxNumNeighbours);
        nnSearch.storePoint(typedPoint);

        for (Point<T> neighbor : nearestNeighbors) {
            Node neighborNode = neighbor.getNode();
            if (neighborNode != null) {
                // TODO - double ou int para a distância?
                int distance = (int) nnSearch.getDistanceFunction().calculate(node.getMLSTdata(), neighborNode.getMLSTdata());
                // node.addNeighbor(neighborNode, distance);
                neighborNode.addNeighbor(node, distance);
                // super.addEdge(new Edge(node, neighborNode, distance));
                super.addEdge(new Edge(neighborNode, node, distance));
            }
        }
    }

    /**
     * Gets the nearest neighbour search algorithm used by this directed graph.
     * @return The nearest neighbour search algorithm
     */
    public NearestNeighbourSearchAlgorithm<T> getNnSearch() {
        return nnSearch;
    }

    /**
     * Gets the neighbors of a given point in the directed graph.
     * @param point The point for which to find neighbors
     * @return List of neighboring points
     */
    public List<Point<T>> getNeighbors(Point<T> point) {
        return nnSearch.neighbourSearch(point, this.maxNumNeighbours);
    }

    /**
     * Gets the edges from a node to its nearest neighbours.
     * @param neighboringPoints List of neighboring points
     * @param node The node from which to get edges
     * @return List of edges to nearest neighbours
     */
    public List<Edge> getNNEdges(List<Point<T>> neighboringPoints, Node node) {
        List<Edge> edges = new ArrayList<>();

        for (Point<T> point : neighboringPoints) {
            Node neighborNode = point.getNode();
            if (neighborNode != null) {
                int distance = (int) nnSearch.getDistanceFunction().calculate(node.getMLSTdata(), neighborNode.getMLSTdata());
                edges.add(new Edge(point.getNode(), neighborNode, distance));
            }
        }
        return edges;
    }
}