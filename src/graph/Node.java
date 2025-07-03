package src.graph;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class Node {

    private String MLSTdata;
    private List<Node> neighbors = new ArrayList<>();
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

    public List<Node> getNeighbors() {
        return neighbors;
    }

    public void addNeighbor(Node neighbor) {
        if (neighbor != null && !neighbors.contains(neighbor)) {
            neighbors.add(neighbor);
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