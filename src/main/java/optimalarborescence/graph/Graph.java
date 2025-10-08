package optimalarborescence.graph;

import optimalarborescence.graph.Edge;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
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
        }
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
            clonedList.add(new Node(node.getMLSTdata(), node.getId()));
        }
        return clonedList;
    }

    public void removeEdge(Edge edge) {
        if (edges.remove(edge)) {
            numEdges--;
        }
    }

    /**
     * Exports the edge list to a binary file (each edge: 2 integers)
     * and the index file (each node: 1 long offset, with padding for missing nodes).
     * Edges for the same source node are stored contiguously.
     */
    public void exportEdgeListAndIndex(String edgeListFile, String indexFile) throws IOException {
        // Group edges by source node ID
        Map<Integer, List<Edge>> edgesBySource = new TreeMap<>();
        int maxNodeId = -1;
        for (Edge edge : edges) {
            int srcId = edge.getSource().getId();
            int dstId = edge.getDestination().getId();
            edgesBySource.computeIfAbsent(srcId, k -> new ArrayList<>()).add(edge);
            maxNodeId = Math.max(maxNodeId, Math.max(srcId, dstId));
        }
        // Write edge list file
        try (DataOutputStream edgeOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(edgeListFile)))) {
            for (List<Edge> edgeList : edgesBySource.values()) {
                for (Edge edge : edgeList) {
                    edgeOut.writeInt(edge.getSource().getId());
                    edgeOut.writeInt(edge.getDestination().getId());
                }
            }
        }
        // Write index file
        try (DataOutputStream indexOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)))) {
            long offset = 0;
            for (int nodeId = 0; nodeId <= maxNodeId; nodeId++) {
                List<Edge> edgeList = edgesBySource.get(nodeId);
                indexOut.writeLong(offset);
                if (edgeList != null) {
                    offset += edgeList.size() * 8L; // 2 ints per edge, 4 bytes each
                }
            }
        }
    }

    /**
     * Static method to load a graph from a binary edge list and index file.
     * Node objects are created with their ID as both id and MLSTdata (as String).
     * Returns a new Graph instance populated with the loaded edges.
     */
    public static Graph loadFromEdgeListAndIndex(String edgeListFile, String indexFile) throws IOException {
        Graph graph = new Graph() {};
        Map<Integer, Node> nodeMap = new HashMap<>();
        try (DataInputStream edgeIn = new DataInputStream(new FileInputStream(edgeListFile))) {
            while (edgeIn.available() >= 8) { // 2 ints = 8 bytes
                int srcId = edgeIn.readInt();
                int dstId = edgeIn.readInt();
                Node src = nodeMap.computeIfAbsent(srcId, id -> new Node(String.valueOf(id), id));
                Node dst = nodeMap.computeIfAbsent(dstId, id -> new Node(String.valueOf(id), id));
                graph.addEdge(new Edge(src, dst, 0)); // Weight unknown from file, set to 0 or add a third int if needed
            }
        }
        // Optionally, you can read the index file here if you want to store offsets or degrees
        return graph;
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
