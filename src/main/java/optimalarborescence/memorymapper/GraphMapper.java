package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.DirectedGraph;
import optimalarborescence.graph.Node;
import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/**
 * GraphMapper provides high-level methods to save and load entire graphs
 * using memory-mapped files. This class wraps EdgeListMapper and NodeIndexMapper
 * to store and query graphs.
 * 
 * File Structure:
 * - {baseName}_edges.dat: Edge list sorted by destination
 * - {baseName}_nodes.dat: Node data (header + MLST data and incoming edge offsets)
 */
public class GraphMapper {
    
    /**
     * Save a graph to memory-mapped files.
     * 
     * @param graph Graph to save
     * @param mlstLength Fixed length for MLST data (in bytes)
     * @param baseName Base name for output files
     * @throws IOException if file operations fail
     */
    public static void saveGraph(Graph graph, int mlstLength, String baseName) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        String nodeFile = baseName + "_nodes.dat";
        
        // Save edges (sorted by destination) and get incoming edge offsets
        Map<Integer, Long> incomingEdgeOffsets = EdgeListMapper.saveEdgesToMappedFile(
            graph.getEdges(), edgeFile);
        
        // Save nodes with data and offsets
        NodeIndexMapper.saveGraph(graph, mlstLength, incomingEdgeOffsets, nodeFile);
    }


    /**
     * Save a graph with nodes but no edges to memory-mapped files. Useful for initializing empty graphs or
     * when the edge weights are computed on-demand.
     * 
     * @param nodes List of nodes to save
     * @param mlstLength Fixed length for MLST data (in bytes)
     * @param baseName Base name for output files
     * @throws IOException if file operations fail
     */
    public static void saveGraph(List<Node> nodes, int mlstLength, String baseName) throws IOException {
        String nodeFile = baseName + "_nodes.dat";

        Map<Integer, Long> incomingEdgeOffsets = new HashMap<>(); // No edges
        for (Node node : nodes) {
            incomingEdgeOffsets.put(node.getId(), -1L);
        }
        
        // Save nodes with MLST data and no edges
        NodeIndexMapper.saveGraph(nodes, mlstLength, incomingEdgeOffsets, nodeFile);
    }
    

    /**
     * Load a graph from memory-mapped files.
     * 
     * @param baseName Base name for input files
     * @return Loaded Graph object
     * @throws IOException if file operations fail
     */
    public static Graph loadGraph(String baseName) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        String nodeFile = baseName + "_nodes.dat";
        
        // Load nodes
        Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(nodeFile);
        
        // Load edges
        List<Edge> edges = EdgeListMapper.loadEdgesFromMappedFile(edgeFile, nodeMap);
        
        Graph graph = new Graph(edges);
        
        // Add any isolated nodes (nodes that don't appear in any edges)
        for (Node node : nodeMap.values()) {
            if (!graph.getNodes().contains(node)) {
                graph.addNode(node);
            }
        }
        
        return graph;
    }

    public static Map<Integer, Node> loadNodeMap(String baseName) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        return NodeIndexMapper.loadNodes(nodeFile);
    }
    
    /**
     * Get the incoming edge offset for a specific node without loading the entire graph.
     * 
     * @param baseName Base name for files
     * @param nodeId Node ID to query
     * @return Byte offset to first incoming edge, or -1 if none
     * @throws IOException if file operations fail
     */
    public static long getIncomingEdgeOffset(String baseName, int nodeId) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        return NodeIndexMapper.getIncomingEdgeOffset(nodeFile, nodeId);
    }
    
    /**
     * Read incoming edges for a specific node.
     * 
     * @param baseName Base name for files
     * @param nodeId Node ID to query
     * @param nodeMap Map of all nodes (for edge reconstruction)
     * @return List of incoming edges for the node
     * @throws IOException if file operations fail
     */
    public static List<Edge> getIncomingEdges(String baseName, int nodeId, 
                                               Map<Integer, Node> nodeMap) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        String nodeFile = baseName + "_nodes.dat";
        
        long offset = NodeIndexMapper.getIncomingEdgeOffset(nodeFile, nodeId);
        if (offset < 0) {
            return List.of(); // No incoming edges
        }
        
        return EdgeListMapper.loadLinkedList(edgeFile, offset);
    }

    /**
     * Get all edges entering a specific node using an existing EdgeLoader.
     * This is more efficient when loading edges for multiple nodes as it avoids
     * repeatedly opening/closing the edge file.
     * 
     * @param baseName Base name for files
     * @param nodeId ID of the node whose incoming edges to retrieve
     * @param nodeMap Map of node IDs to Node objects for edge reconstruction
     * @param edgeLoader Pre-opened EdgeLoader for efficient file access
     * @return List of incoming edges for the node
     * @throws IOException if file operations fail
     */
    public static List<Edge> getIncomingEdges(String baseName, int nodeId, 
                                               Map<Integer, Node> nodeMap,
                                               EdgeListMapper.EdgeLoader edgeLoader) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        
        long offset = NodeIndexMapper.getIncomingEdgeOffset(nodeFile, nodeId);
        if (offset < 0) {
            return List.of(); // No incoming edges
        }
        
        return edgeLoader.loadLinkedList(offset, nodeMap);
    }

    /**
     * Add a single node and its incident edges to the graph files.
     *
     * @param node Node to add
     * @param incomingEdges List of edges pointing TO the new node
     * @param outgoingEdges List of edges FROM the new node to other nodes
     * @param baseName Base name for files
     * @param mlstLength Fixed length for MLST data
     * @throws IOException if file operations fail
     */
    public static void addNode(Node node, List<Edge> incomingEdges, List<Edge> outgoingEdges, 
                              String baseName, int mlstLength) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        String edgeFile = baseName + "_edges.dat";

        // Add the node itself to the node index
        NodeIndexMapper.addNode(node, nodeFile, mlstLength);
        
        // Add incoming edges (stored as a linked list for this node)
        EdgeListMapper.addEdges(incomingEdges, node, edgeFile);
        
        // Add outgoing edges (each needs to be added to its destination's linked list)
        for (Edge outgoingEdge : outgoingEdges) {
            EdgeListMapper.addEdge(outgoingEdge, edgeFile);
        }
    }
    
    /**
     * Add a single node and its incoming edges to the graph files.
     *
     * @param node Node to add
     * @param incomingEdges List of edges pointing TO the new node
     * @param baseName Base name for files
     * @param mlstLength Fixed length for MLST data
     * @throws IOException if file operations fail
     */
    public static void addNode(Node node, List<Edge> incomingEdges, String baseName, int mlstLength) throws IOException {
        addNode(node, incomingEdges, List.of(), baseName, mlstLength);
    }
    
    /**
     * Add multiple nodes and their edges in a single batch operation.
     * 
     * @param nodes List of nodes to add
     * @param nodeEdges Map of node to its incoming edges
     * @param existingNodeNewEdges Map of existing nodes to edges that should be added to them (edges from new nodes TO existing nodes)
     * @param baseName Base name for files
     * @param mlstLength Fixed length for MLST data
     * @throws IOException if file operations fail
     */
    public static void addNodesBatch(List<Node> nodes, Map<Node, List<Edge>> nodeEdges, 
                                     Map<Node, List<Edge>> existingNodeNewEdges,
                                     String baseName, int mlstLength) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        String edgeFile = baseName + "_edges.dat";
        
        // Add all nodes at once
        NodeIndexMapper.addNodesBatch(nodes, nodeFile, mlstLength);
        
        // Add edges for new nodes in one batch operation
        EdgeListMapper.addEdgesBatch(nodeEdges, edgeFile);
        
        // Add edges incoming to existing nodes
        if (existingNodeNewEdges != null && !existingNodeNewEdges.isEmpty()) {
            EdgeListMapper.addEdgesToExistingNodes(existingNodeNewEdges, nodeFile, edgeFile);
        }
    }

    public static void removeNode(Node node, String baseName, int mlstLength) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        String edgeFile = baseName + "_edges.dat";

        long incomingEdgeOffset = NodeIndexMapper.getIncomingEdgeOffset(nodeFile, node.getId());
        if (incomingEdgeOffset >= 0) {
            EdgeListMapper.removeLinkedList(edgeFile, incomingEdgeOffset);
        }

        List<Long> outgoingEdgeOffsets = EdgeListMapper.getOutgoingEdgeOffsets(edgeFile, node.getId());

        int iterator = outgoingEdgeOffsets.size() - 1;
        while (iterator >= 0) {
            EdgeListMapper.removeEdgeAtOffset(edgeFile, outgoingEdgeOffsets.get(iterator));
            iterator--;
        }

        NodeIndexMapper.removeNode(node, nodeFile);
    }

    /**
     * Remove multiple nodes and their incident edges in a single batch operation.
     * <p>
     * The operation removes:
     * 1. All edges where the source or destination node is in the nodes list
     * 2. All corresponding node entries from the node index
     * 
     * @param nodes List of nodes to remove
     * @param baseName Base name for files
     * @param mlstLength Fixed length for MLST data
     * @throws IOException if file operations fail
     */
    public static void removeNodesBatch(List<Node> nodes, String baseName, int mlstLength) throws IOException {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        String edgeFile = baseName + "_edges.dat";
        String nodeFile = baseName + "_nodes.dat";
        
        // Create set of node IDs for edge removal
        Set<Integer> nodeIds = new HashSet<>();
        for (Node node : nodes) {
            nodeIds.add(node.getId());
        }
        
        // Remove all edges incident to these nodes in one batch
        EdgeListMapper.removeEdgesBatch(nodeIds, edgeFile);
        
        // Remove all nodes in one batch
        NodeIndexMapper.removeNodesBatch(nodes, nodeFile);
    }

    public static boolean edgeExists(int sourceId, int destId, String baseName) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        String nodeFile = baseName + "_nodes.dat";

        try {
            long incomingEdgeOffset = NodeIndexMapper.getIncomingEdgeOffset(nodeFile, destId);
            if (incomingEdgeOffset < 0) {
                return false;
            }

            return EdgeListMapper.edgeExists(edgeFile, sourceId, destId, incomingEdgeOffset);
        } catch (IOException e) {
            // If node doesn't exist, edge can't exist
            if (e.getMessage() != null && (e.getMessage().contains("out of range") || e.getMessage().contains("not found"))) {
                return false;
            }
            throw e;
        }
    }

    public static void removeEdge(int sourceId, int destId, String baseName) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        String nodeFile = baseName + "_nodes.dat";

        try {
            long incomingEdgeOffset = NodeIndexMapper.getIncomingEdgeOffset(nodeFile, destId);
            if (incomingEdgeOffset < 0) {
                return;
            }

            EdgeListMapper.removeEdge(edgeFile, sourceId, destId, incomingEdgeOffset);
        } catch (IOException e) {
            // If node doesn't exist, nothing to remove
            if (e.getMessage() != null && (e.getMessage().contains("out of range") || e.getMessage().contains("not found"))) {
                return;
            }
            throw e;
        }
    }

    public static void addEdge(Edge edge, String baseName) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        EdgeListMapper.addEdge(edge, edgeFile);
    }

    public static void saveArborescence(List<Edge> phylogeny, String baseName) throws IOException {
        String edgeFile = baseName + "_phylogeny_edges.dat";
        EdgeListMapper.saveEdgesToMappedFile(phylogeny, edgeFile);
    }
}
