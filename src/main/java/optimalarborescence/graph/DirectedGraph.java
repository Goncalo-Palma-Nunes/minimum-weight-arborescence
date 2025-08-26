package optimalarborescence.graph;

import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;
import optimalarborescence.nearestneighbour.Point;

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
        // TODO - maybe é preferível fazer store do point aqui, em vez de dentro do neighbourSearch

        throw new NotImplementedException("DirectedGraph.addNode(Node node) is not implemented yet.");
    }
    
}
