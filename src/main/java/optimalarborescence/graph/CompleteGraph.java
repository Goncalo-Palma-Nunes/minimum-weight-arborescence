package optimalarborescence.graph;

import java.util.List;
import java.io.Serializable;
import java.util.ArrayList;

public class CompleteGraph implements Serializable {

    private List<Node> nodes;


    /**
     * Constructs a complete graph with the specified number of nodes.
     * Implemented with an adjacency list
     *
     */
    public CompleteGraph() {
        this.nodes = new ArrayList<>();
    }

    /*
     * Returns the list of nodes in the graph.
     */
    public List<Node> getNodes() {
        return nodes;
    }

    /**
     * Connects all nodes in the graph to each other.
     */
    // private void connectAllNodes() {
    //     for (Node node : getNodes()) {
    //         for (Node neighbor : getNodes()) {
    //             if (!node.equals(neighbor)) {
    //                 node.addNeighbor(neighbor);
    //             }
    //         }
    //     }
    // }

    /**
     * Connects one node to all existing nodes in the graph.
     * 
     * Note: Only store one direction of the connection 
     * to save memory.
     */
    public void connectNodeToAll(Node node) {
        for (Node neighbor : getNodes()) {
            if (!node.equals(neighbor)) {
                // TODO - falta calcular o peso
                node.addNeighbor(neighbor);
            }
        }
    }

    /**
     * Adds a node to the graph and connects it to all existing nodes.
     *
     * @param node the node to be added
     */
    public void addNode(Node node) {
        nodes.add(node);
        connectNodeToAll(node);
    }
}
