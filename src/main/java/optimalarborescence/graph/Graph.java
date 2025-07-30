package optimalarborescence.graph;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public abstract class Graph implements Serializable{
    
    private List<Node> nodes;
    private int numNodes;
 
    public Graph() {
        this.nodes = new ArrayList<>();
        this.numNodes = 0;
    }

    /* ******************************************
     *
     *            Getters and Setters
     * 
     * ******************************************/

    public List<Node> getNodes() {
        return nodes;
    }

    public int getNumNodes() {
        return numNodes;
    }

    /* ******************************************
     *
     *            Graph Operations
     * 
     * ******************************************/

    public void addNode(Node node) {
        if (node != null && !nodes.contains(node)) {
            nodes.add(node);
            numNodes++;
        }
    }

    /* ******************************************
     *
     *            Overridden Methods
     * 
     * ******************************************/

}
