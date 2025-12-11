package optimalarborescence.graph;


import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class Graph implements Serializable{
    
    private List<Node> nodes;
    private List<Edge> edges; // TODO - forma eficiente de representar para dar delete de arestas e nós facilmente?
    private int numNodes;
    private int numEdges;

    public Graph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.numNodes = 0;
        this.numEdges = 0;
    }

    public Graph(List<Edge> edges) {
        this();
        for (Edge edge : edges) {
            addEdge(edge);
        }

        HashMap<Integer, Node> nodeMap = new HashMap<>();
        for (Edge edge : edges) {
            if (!nodeMap.containsKey(edge.getSource().getId())) {
                addNode(edge.getSource());
                nodeMap.put(edge.getSource().getId(), edge.getSource());
            }
            if (!nodeMap.containsKey(edge.getDestination().getId())) {
                addNode(edge.getDestination());
                nodeMap.put(edge.getDestination().getId(), edge.getDestination());
            }
            edge.getSource().addNeighbor(edge.getDestination(), edge.getWeight());
        }
        // Sort nodes by ID to ensure consistent ordering
        // this.nodes.sort(Comparator.comparingInt(Node::getId));
    }

    /* ******************************************
     *
     *            Getters and Setters
     * 
     * ******************************************/

    public List<Edge> getEdges() {
        return edges;
    }

    public int getNumEdges() {
        return numEdges;
    }

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

    // public void addNode(Node node) {
    //     if (node != null) {
    //         numNodes++;
    //     }
    // }

    public void addEdge(Edge edge) {
        if (edge != null) {
            edges.add(edge);
            numEdges++;
        }
    }


    public List<Node> cloneNodeList() {
        List<Node> clonedList = new ArrayList<>();
        for (Node node : nodes) {
            // clonedList.add(new Node(node.getMLSTdata(), node.getId()));
            clonedList.add(new Node(node.getId()));
        }
        return clonedList;
    }

    public void removeEdge(Edge edge) {
        if (edges.remove(edge)) {
            numEdges--;
        }
    }


    /* ******************************************
     *
     *            Overridden Methods
     * 
     * ******************************************/


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph with ").append(numNodes).append(" nodes:\n");
        for (Node node : nodes) {
            sb.append(node.toString()).append("\n");
        }

        sb.append("\n Edges:\n");
        for (Edge edge : edges) {
            sb.append(edge.toString()).append("\n");
        }
        return sb.toString();
    }
}
