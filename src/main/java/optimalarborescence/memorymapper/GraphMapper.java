package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.DirectedGraph;
import optimalarborescence.graph.Node;
import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * GraphMapper provides high-level methods to save and load entire graphs
 * using memory-mapped files. This class coordinates EdgeListMapper and NodeIndexMapper
 * to store graphs efficiently.
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
        
        // Save nodes with MLST data and offsets
        NodeIndexMapper.saveGraph(graph, mlstLength, incomingEdgeOffsets, nodeFile);
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

    /**
     * Load a directed graph from memory-mapped files.
     * @param <T> Type of data in the graph nodes
     * @param baseName Base name for input files
     * @param nnAlgorithm Nearest neighbour search algorithm to use
     * @param numNeighbors Maximum number of neighbours per node
     * @return Loaded DirectedGraph object
     * @throws IOException
     */
    public static <T> DirectedGraph<T> loadDirectedGraph(String baseName, NearestNeighbourSearchAlgorithm<T> nnAlgorithm, int numNeighbors) throws IOException {
        Graph baseGraph = loadGraph(baseName); // TODO - refatorizar para não estar a criar 2 grafos
        DirectedGraph<T> directedGraph = new DirectedGraph<>(nnAlgorithm, numNeighbors, baseGraph);
        return directedGraph;
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
     * Add a single node and its incident edges to the graph files.
     * This method handles both incoming edges (edges to the new node) and outgoing edges
     * (edges from the new node to existing nodes).
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
     * Use this overload when there are no outgoing edges from the new node.
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
     * This is much more efficient than calling addNode() multiple times.
     * 
     * @param nodes List of nodes to add
     * @param nodeEdges Map of node to its incoming edges
     * @param baseName Base name for files
     * @param mlstLength Fixed length for MLST data
     * @throws IOException if file operations fail
     */
    public static void addNodesBatch(List<Node> nodes, Map<Node, List<Edge>> nodeEdges, 
                                     String baseName, int mlstLength) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        String edgeFile = baseName + "_edges.dat";
        
        // Add all nodes at once
        NodeIndexMapper.addNodesBatch(nodes, nodeFile, mlstLength);
        
        // Add all edges in one batch operation (much faster!)
        EdgeListMapper.addEdgesBatch(nodeEdges, edgeFile);
    }

    public static void removeNode(Node node, String baseName, int mlstLength) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        String edgeFile = baseName + "_edges.dat";

        long incomingEdgeOffset = NodeIndexMapper.getIncomingEdgeOffset(nodeFile, node.getId());
        if (incomingEdgeOffset >= 0) {
            EdgeListMapper.removeLinkedList(edgeFile, incomingEdgeOffset);
        }

        List<Long> outgoingEdgeOffsets = EdgeListMapper.getOutgoingEdgeOffsets(edgeFile, node.getId());

        // TODO - já que tenho de percorrer o ficheiro para obter as outgoing edges
        // talvez seja melhor não usar removeLinkedList, mas simplesmente 
        // criar um array das arestas sem incidências/fonte no nó e guardar isso

        int iterator = outgoingEdgeOffsets.size() - 1;
        while (iterator >= 0) {
            EdgeListMapper.removeEdgeAtOffset(edgeFile, outgoingEdgeOffsets.get(iterator));
            iterator--;
        }

        NodeIndexMapper.removeNode(node, nodeFile);
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
