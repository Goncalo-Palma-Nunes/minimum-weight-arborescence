package optimalarborescence.graph;

import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.sequences.*;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Node implements Serializable, Comparable<Node> {

    private Map<Node, Integer> neighbors = new TreeMap<>();
    // private static final int serialVersionUID = 1;
    private int pointID; // Unique identifier for the node
    private Point<?> point; // Underlying point

    @Override
    public int compareTo(Node other) {
        return Integer.compare(this.pointID, other.pointID);
    }

    public Node(Sequence<?> MLSTdata, int pointID) {
        this.point = new Point<>(pointID, MLSTdata);
        this.point.setNode(this);
        this.pointID = pointID;
    }

    public Node(int pointID) { // For mock testing
        this.point = null;
        this.pointID = pointID;
    }

    public Sequence<?> getMLSTdata() {
        return point.getSequence();
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
        return pointID;
    }

    public Point<?> getPoint() {
        return point;
    }

    @Override
    public String toString() {
        String neighborStr = neighbors.keySet().stream()
            .map(n -> n.getMLSTdata().toString())
            .collect(Collectors.joining(", "));
        return "Node {" +
                "Sequence='" + point.getSequence().toString() + '\'' +
                ", neighbors=[" + neighborStr + "]" +
                ", pointID=" + pointID +
                " }";
    }
}