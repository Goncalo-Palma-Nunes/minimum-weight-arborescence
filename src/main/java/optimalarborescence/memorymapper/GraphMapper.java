package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * GraphMapper provides high-level methods to save and load entire graphs
 * using memory-mapped files. This class wraps EdgeListMapper and NodeIndexMapper
 * to store and query graphs.
 * <p>
 * The graph is stored in |V| + 1 files: One index for the nodes and their sequence data;
 * |V| files for the edges incident to each node. The files are named based on a provided base name:
 * 
 * - {baseName}_edges_node{nodeId}.dat: Array of edges pointing to node with ID nodeId
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
        
        // Save node index
        NodeIndexMapper.saveGraph(graph.getNodes(), mlstLength, nodeFile);

        // Save edges
        EdgeListMapper.writeEdgeArray(edgeFile, graph.getEdges());
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
        
        // Save nodes with MLST data and no edges
        NodeIndexMapper.saveGraph(nodes, mlstLength, nodeFile);
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
        List<Edge> edges = new ArrayList<>();

        for (Node node : nodeMap.values()) {
            String filename = edgeFile.replace("_edges.dat", "_edges_node" + node.getId() + ".dat");
            edges.addAll(EdgeListMapper.loadEdgeArray(filename));
        }

        return new Graph(edges);
    }

    public static Map<Integer, Node> loadNodeMap(String baseName) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        return NodeIndexMapper.loadNodes(nodeFile);
    }
    
    
    /**
     * Read incoming edges for a specific node.
     * 
     * @param baseName Base name for files
     * @param nodeId Node ID to query
     * @return List of incoming edges for the node
     * @throws IOException if file operations fail
     */
    public static List<Edge> getIncomingEdges(String baseName, int nodeId) throws IOException {
        return EdgeListMapper.loadEdgeArray(baseName + "_edges_node" + nodeId + ".dat");
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

    /**
     * Remove a single node and all its incident and outgoing edges from the graph files.
     * @param node Node to remove
     * @param baseName Base name for files
     * @throws IOException
     */
    public static void removeNode(Node node, String baseName) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        String edgeFile = baseName + "_edges.dat";

        // Remove all incident edges to this node
        EdgeListMapper.removeEdges(edgeFile, node.getId());

        // Remove all outgoing edges from this node
        EdgeListMapper.removeOutgoingEdges(edgeFile, node.getId());

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
        return EdgeListMapper.edgeExists(baseName, sourceId, destId);
    }

    public static void removeEdge(int sourceId, int destId, String baseName) throws IOException {
        EdgeListMapper.removeEdge(baseName, sourceId, destId);
    }

    public static void addEdge(Edge edge, String baseName) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        EdgeListMapper.addEdge(edge, edgeFile);
    }

    public static void saveArborescence(List<Edge> phylogeny, String baseName) throws IOException {
        String edgeFile = baseName + "_phylogeny_edges.dat";
        EdgeListMapper.writeEdgeArray(edgeFile, phylogeny);
    }
}
