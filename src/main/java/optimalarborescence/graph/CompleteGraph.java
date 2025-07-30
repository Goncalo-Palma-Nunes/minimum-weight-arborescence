package optimalarborescence.graph;

public class CompleteGraph extends Graph {

    /**
     * Constructs a complete graph with the specified number of nodes.
     *
     */
    public CompleteGraph() {
        super();
    }

    /**
     * Connects all nodes in the graph to each other.
     */
    private void connectAllNodes() {
        for (Node node : getNodes()) {
            for (Node neighbor : getNodes()) {
                if (!node.equals(neighbor)) {
                    node.addNeighbor(neighbor);
                }
            }
        }
    }

    /**
     * Connects one node to all existing nodes in the graph.
     * 
     * Note: Only store one direction of the connection 
     * to save memory.
     */
    public void connectNodeToAll(Node node) {
        for (Node neighbor : getNodes()) {
            if (!node.equals(neighbor)) {
                node.addNeighbor(neighbor);
            }
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
        connectNodeToAll(node);
    }
}
