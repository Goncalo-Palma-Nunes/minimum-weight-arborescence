package optimalarborescence.graph;

import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;
import optimalarborescence.nearestneighbour.Point;

import java.util.List;

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
}