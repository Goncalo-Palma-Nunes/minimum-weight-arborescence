package optimalarborescence.graph;


import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class Graph implements Serializable, PhylogeneticData {
    
    private List<Node> nodes;
    private List<Edge> edges;
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

    public void addEdge(Edge edge) {
        if (edge != null) {
            edges.add(edge);
            numEdges++;
        }
    }


    public List<Node> cloneNodeList() {
        List<Node> clonedList = new ArrayList<>();
        for (Node node : nodes) {
            clonedList.add(new Node(node.getId()));
        }
        return clonedList;
    }

    public void removeEdge(Edge edge) {
        if (edges.remove(edge)) {
            numEdges--;
        }
    }

    /**
     * Removes a node from the graph. Also removes any edge incident or outgoing from this node.
     * 
     * @param node The node to remove
     */
    public void removeNode(Node node) {
        if (node == null) {
            return;
        }
        
        // Remove edges incident or outgoing from this node
        edges.removeIf(edge -> edge.getSource().equals(node) || edge.getDestination().equals(node));
        
        // Remove the node and update count
        if (nodes.remove(node)) {
            numNodes--;
        }
    }

    public void addNodes(List<Node> nodesToAdd) {
        for (Node node : nodesToAdd) {
            addNode(node);
        }
    }

    public void removeNodes(List<Node> nodesToRemove) {
        for (Node node : nodesToRemove) {
            removeNode(node);
        }
    }
    
    public List<Edge> getNodeIncomingEdges(Node node) {
        List<Edge> incomingEdges = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.getDestination().equals(node)) {
                incomingEdges.add(edge);
            }
        }
        return incomingEdges;
    }

    public List<Edge> getNodeOutgoingEdges(Node node) {
        return node.getNeighbors().entrySet().stream()
            .map(entry -> new Edge(node, entry.getKey(), entry.getValue()))
            .toList();
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
