package optimalarborescence.graph;

import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;
import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.distance.*;

import java.util.List;

public class DirectedGraph extends Graph {

    private NearestNeighbourSearchAlgorithm nnSearch;
    private int maxNumNeighbours;

    /**
     * Constructs a directed graph with the specified number of nodes.
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
    
}
