package optimalarborescence.graph;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public class Node implements Serializable {

    private String MLSTdata;
    private Map<Node, Integer> neighbors = new TreeMap<>();
    private static final long serialVersionUID = 1L;
    private int id; // Unique identifier for the node

    public Node(String MLSTdata, int id) {
        this.MLSTdata = MLSTdata;
        this.id = id;
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

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Node {" +
                "MLSTdata='" + MLSTdata + '\'' +
                ", neighbors=" + neighbors +
                ", id=" + id +
                " }";
    }
}