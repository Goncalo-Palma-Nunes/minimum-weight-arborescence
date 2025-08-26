package optimalarborescence.graph;

import optimalarborescence.nearestneighbour.Point;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public class Node extends Point implements Serializable {

    private String MLSTdata;
    private Map<Node, Integer> neighbors = new TreeMap<>();
    private static final long serialVersionUID = 1L;
    private int pointID; // Unique identifier for the node

    public Node(String MLSTdata, int pointID) {
        super(pointID, MLSTdata);
        this.MLSTdata = MLSTdata;
        this.pointID = pointID;
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

    public int getpointID() {
        return pointID;
    }

    @Override
    public String toString() {
        return "Node {" +
                "MLSTdata='" + MLSTdata + '\'' +
                ", neighbors=" + neighbors +
                ", pointID=" + pointID +
                " }";
    }
}