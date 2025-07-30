package optimalarborescence.graph;

import optimalarborescence.exception.NotImplementedException;

public class DirectedGraph extends Graph {

    /**
     * Constructs a directed graph with the specified number of nodes.
     */
    public DirectedGraph() {
        super();
    }

    /**
     * Adds a node to the graph and connects it to all existing nodes.
     *
     * @param node the node to be added
     */
    @Override
    public void addNode(Node node) {
        super.addNode(node);
        
        // TODO - calcular quem são os vizinhos 

        throw new NotImplementedException("DirectedGraph.addNode(Node node) is not implemented yet.");
    }
    
}
