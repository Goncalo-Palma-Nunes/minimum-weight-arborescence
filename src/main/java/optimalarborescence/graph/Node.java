package optimalarborescence.graph;

import optimalarborescence.nearestneighbour.Point;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Node extends Point implements Serializable, Comparable<Node> {

    private String MLSTdata;
    private Map<Node, Integer> neighbors = new TreeMap<>();
    // private static final int serialVersionUID = 1;
    private int pointID; // Unique identifier for the node

    @Override
    public int compareTo(Node other) {
        return Integer.compare(this.pointID, other.pointID);
    }

    public Node(String MLSTdata, int pointID) {
        super(pointID, MLSTdata);
        this.MLSTdata = MLSTdata;
        this.pointID = pointID;
    }

    // public long getID() {
    //     return serialVersionUID;
    // }

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

    public int getID() {
        return pointID;
    }

    @Override
    public String toString() {
        String neighborStr = neighbors.keySet().stream()
            .map(n -> n.getMLSTdata())
            .collect(Collectors.joining(", "));
        return "Node {" +
                "Sequence='" + MLSTdata + '\'' +
                ", neighbors=[" + neighborStr + "]" +
                ", pointID=" + pointID +
                " }";
    }
}