package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.datastructure.UnionFindStronglyConnected;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.function.Consumer;

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

        // Group edges by their destination node
        Map<Integer, List<Edge>> edgesByDestination = new HashMap<>();
        for (Edge edge : graph.getEdges()) {
            int destId = edge.getDestination().getId();
            edgesByDestination.computeIfAbsent(destId, k -> new ArrayList<>()).add(edge);
        }

        // Save edges to separate per-node files
        // For nodes with edges, save them; for nodes without edges, create empty files
        for (Node node : graph.getNodes()) {
            int nodeId = node.getId();
            List<Edge> nodeEdges = edgesByDestination.getOrDefault(nodeId, new ArrayList<>());
            String nodeEdgeFile = baseName + "_edges_node" + nodeId + ".dat";
            EdgeListMapper.writeEdgeArray(nodeEdgeFile, nodeEdges);
        }
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
            // Check if file exists before trying to load (nodes with no incoming edges won't have a file)
            java.io.File file = new java.io.File(filename);
            if (file.exists()) {
                edges.addAll(EdgeListMapper.loadEdgeArray(filename));
            }
        }

        Graph graph = new Graph(edges);
        
        // Add isolated nodes (nodes not connected by any edge)
        for (Node node : nodeMap.values()) {
            if (!graph.getNodes().stream().anyMatch(n -> n.getId() == node.getId())) {
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
     * Load only node IDs without sequence data from memory-mapped file.
     * Much more memory-efficient when sequences are not needed for computation.
     * Use this when edges are pre-computed and stored on disk.
     * 
     * @param baseName Base name for files
     * @return Map of node ID to lightweight Node object (ID only)
     * @throws IOException if file operations fail
     */
    public static Map<Integer, Node> loadNodeIdsOnly(String baseName) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        return NodeIndexMapper.loadNodeIdsOnly(nodeFile);
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

        // Remove the edge file for this node (incoming edges)
        EdgeListMapper.removeEdges(edgeFile, node.getId());

        // Remove all outgoing edges from this node (edges in other nodes' files)
        EdgeListMapper.removeOutgoingEdges(edgeFile, node.getId());

        // Remove the node from the node index
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
        
        // Remove all outgoing edges from these nodes
        for (Integer nodeId : nodeIds) {
            EdgeListMapper.removeOutgoingEdges(edgeFile, nodeId);
        }
        
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

    public static List<Edge> loadIncidentEdges(String baseName, int nodeId) throws IOException {
        String edgeFile = baseName + "_edges_node" + nodeId + ".dat";
        return EdgeListMapper.loadEdgeArray(edgeFile);
    }
    
    /**
     * Stream edges incident to a specific node directly to a consumer.
     * 
     * @param baseName Base name for the graph files
     * @param nodeId ID of the node whose incident edges to stream
     * @param edgeConsumer Function to process each edge as it's read
     */
    public static void streamIncidentEdges(String baseName, int nodeId, Consumer<Edge> edgeConsumer) {
        String edgeFile = baseName + "_edges_node" + nodeId + ".dat";
        EdgeListMapper.streamEdges(edgeFile, edgeConsumer);
    }

    public static void saveArborescence(List<Edge> phylogeny, String baseName) throws IOException {
        String edgeFile = baseName + "_phylogeny_edges.dat";
        EdgeListMapper.writeEdgeArray(edgeFile, phylogeny);
    }

    public static List<Edge> getOutgoingEdges(String baseName, int sourceId) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        return EdgeListMapper.getOutgoingEdges(edgeFile, sourceId);
    }

    public static void removeOutgoingEdges(String baseName, int sourceId) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        EdgeListMapper.removeOutgoingEdges(edgeFile, sourceId);
    }

    public static Edge findMinSafeEdgeIncomingToSCC(String baseName, UnionFindStronglyConnected uf,
                                                     Set<Integer> sccNodes, Comparator<int[]> cmp) throws IOException {
        String edgeFile = baseName + "_edges.dat";

        Edge currBest = null;
        for (Integer nodeId : sccNodes) {
            Edge e = EdgeListMapper.findMinSafeEdgeInFile(edgeFile, nodeId, uf, cmp);
            if (e != null) {
                if (currBest == null || cmp.compare(
                        new int[]{ e.getWeight(), e.getSource().getId(), e.getDestination().getId() },
                        new int[]{ currBest.getWeight(), currBest.getSource().getId(), currBest.getDestination().getId() }) < 0) {
                    currBest = e;
                }
            }
        }
        return currBest;
    }
}
