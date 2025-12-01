package optimalarborescence.graph;


import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
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
            clonedList.add(new Node(node.getMLSTdata(), node.getId()));
        }
        return clonedList;
    }

    public void removeEdge(Edge edge) {
        if (edges.remove(edge)) {
            numEdges--;
        }
    }

    // /**
    //  * Exports the edge list to a binary file (edges sorted by source)
    //  * and the index file (node ID, MLST data, and file offset for outgoing edges).
    //  * 
    //  * Edge List File Format:
    //  *   For each edge: [source_id (4 bytes), destination_id (4 bytes), weight (4 bytes)]
    //  *   Edges are sorted by source_id so outgoing edges are contiguous.
    //  * 
    //  * Index File Format:
    //  *   For each node (with padding for missing nodes):
    //  *   [node_id (4 bytes), MLST_data_length (4 bytes), MLST_data (variable), offset (8 bytes)]
    //  *   Offset points to the first outgoing edge in the edge list file.
    //  */
    // public void exportEdgeListAndIndex(String edgeListFile, String indexFile) throws IOException {
    //     // Sort edges by source node ID (to keep outgoing edges together)
    //     List<Edge> sortedEdges = new ArrayList<>(edges);
    //     sortedEdges.sort(Comparator.comparingInt(edge -> edge.getSource().getId()));
        
    //     // Group edges by source to find where each node's outgoing edges start
    //     Map<Integer, Long> outgoingEdgeOffsets = new TreeMap<>();
    //     long currentOffset = 0;
    //     int maxNodeId = -1;
        
    //     for (Edge edge : sortedEdges) {
    //         int srcId = edge.getSource().getId();
    //         if (!outgoingEdgeOffsets.containsKey(srcId)) {
    //             outgoingEdgeOffsets.put(srcId, currentOffset);
    //         }
    //         maxNodeId = Math.max(maxNodeId, Math.max(edge.getSource().getId(), edge.getDestination().getId()));
    //         currentOffset += 12; // 3 ints per edge (source, dest, weight), 4 bytes each
    //     }
        
    //     // Create a map of all nodes for easy lookup
    //     Map<Integer, Node> nodeMap = new HashMap<>();
    //     for (Node node : nodes) {
    //         nodeMap.put(node.getId(), node);
    //         maxNodeId = Math.max(maxNodeId, node.getId());
    //     }
        
    //     // Write edge list file (sorted by source)
    //     try (DataOutputStream edgeOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(edgeListFile)))) {
    //         for (Edge edge : sortedEdges) {
    //             edgeOut.writeInt(edge.getSource().getId());
    //             edgeOut.writeInt(edge.getDestination().getId());
    //             edgeOut.writeInt(edge.getWeight());
    //         }
    //     }
        
    //     // Write index file (one entry per node ID, with padding for missing nodes)
    //     try (DataOutputStream indexOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)))) {
    //         for (int nodeId = 0; nodeId <= maxNodeId; nodeId++) {
    //             // Write node ID
    //             indexOut.writeInt(nodeId);
                
    //             // Write MLST data
    //             Node node = nodeMap.get(nodeId);
    //             if (node != null && node.getMLSTdata() != null) {
    //                 String mlstData = node.getMLSTdata();
    //                 byte[] mlstBytes = mlstData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    //                 indexOut.writeInt(mlstBytes.length);
    //                 indexOut.write(mlstBytes);
    //             } else {
    //                 indexOut.writeInt(0); // No MLST data
    //             }
                
    //             // Write offset to outgoing edges
    //             Long offset = outgoingEdgeOffsets.get(nodeId);
    //             indexOut.writeLong(offset != null ? offset : -1L); // -1 indicates no outgoing edges
    //         }
    //     }
    // }

    // /**
    //  * Static method to load a graph from binary edge list and index files.
    //  * 
    //  * Reads the index file to reconstruct nodes with their IDs and MLST data,
    //  * then reads the edge list to reconstruct edges.
    //  * 
    //  * @param edgeListFile Path to the binary edge list file
    //  * @param indexFile Path to the binary index file
    //  * @return A new Graph instance populated with nodes and edges
    //  */
    // public static Graph loadFromEdgeListAndIndex(String edgeListFile, String indexFile) throws IOException {
    //     Map<Integer, Node> nodeMap = new HashMap<>();
        
    //     // Read index file to reconstruct all nodes
    //     try (DataInputStream indexIn = new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile)))) {
    //         while (indexIn.available() > 0) {
    //             // Read node ID
    //             int nodeId = indexIn.readInt();
                
    //             // Read MLST data
    //             int mlstLength = indexIn.readInt();
    //             String mlstData;
    //             if (mlstLength > 0) {
    //                 byte[] mlstBytes = new byte[mlstLength];
    //                 indexIn.readFully(mlstBytes);
    //                 mlstData = new String(mlstBytes, java.nio.charset.StandardCharsets.UTF_8);
    //             } else {
    //                 mlstData = String.valueOf(nodeId); // Default to node ID as string
    //             }
                
    //             // Read offset (we don't use it during loading, but need to consume it)
    //             indexIn.readLong();
                
    //             // Create node
    //             nodeMap.put(nodeId, new Node(mlstData, nodeId));
    //         }
    //     }
        
    //     // Read edge list file to reconstruct edges
    //     List<Edge> edgeList = new ArrayList<>();
    //     try (DataInputStream edgeIn = new DataInputStream(new BufferedInputStream(new FileInputStream(edgeListFile)))) {
    //         while (edgeIn.available() >= 12) { // 3 ints = 12 bytes
    //             int srcId = edgeIn.readInt();
    //             int dstId = edgeIn.readInt();
    //             int weight = edgeIn.readInt();
                
    //             Node src = nodeMap.get(srcId);
    //             Node dst = nodeMap.get(dstId);
                
    //             if (src != null && dst != null) {
    //                 edgeList.add(new Edge(src, dst, weight));
    //             }
    //         }
    //     }
        
    //     return new Graph(edgeList);
    // }

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
