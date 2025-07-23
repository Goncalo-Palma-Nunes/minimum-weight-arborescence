package src.graph;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public class Node {

    private String MLSTdata;
    private Map<Node, Integer> neighbors = new TreeMap<>();
    private static final long serialVersionUID = 1L;

    public Node(String MLSTdata) {
        this.MLSTdata = MLSTdata;
    }

    public long getID() {
        return serialVersionUID;
    }

    public String getMLSTdata() {
        return MLSTdata;
    }

    public Map<Node, Integer> getNeighbors() {
        return neighbors;
    }

    public void addNeighbor(Node neighbor, int weight) {
        if (neighbor != null && !neighbors.containsKey(neighbor)) {
            neighbors.put(neighbor, weight);
        }
    }
    public void addNeighbor(Node neighbor) {
        if (neighbor != null && !neighbors.containsKey(neighbor)) {
            neighbors.put(neighbor, 0);
        }
    }

    @Override
    public String toString() {
        return "Node {" +
                "MLSTdata='" + MLSTdata + '\'' +
                ", neighbors=" + neighbors +
                " }";
    }
}