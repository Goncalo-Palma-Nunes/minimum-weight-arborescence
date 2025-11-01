package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;

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
 * - {baseName}_nodes.dat: Node index array
 * - {baseName}_mlst.dat: MLST data and incoming edge offsets
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
        String nodeIndexFile = baseName + "_nodes.dat";
        String mlstDataFile = baseName + "_mlst.dat";
        
        // Save edges (sorted by destination) and get incoming edge offsets
        Map<Integer, Long> incomingEdgeOffsets = EdgeListMapper.saveEdgesToMappedFile(
            graph.getEdges(), edgeFile);
        
        // Save nodes with MLST data and offsets
        NodeIndexMapper.saveGraph(graph, mlstLength, incomingEdgeOffsets, nodeIndexFile, mlstDataFile);
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
        String nodeIndexFile = baseName + "_nodes.dat";
        String mlstDataFile = baseName + "_mlst.dat";
        
        // Load nodes
        Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(nodeIndexFile, mlstDataFile);
        
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
     * Get the incoming edge offset for a specific node without loading the entire graph.
     * 
     * @param baseName Base name for files
     * @param nodeId Node ID to query
     * @param mlstLength Fixed length of MLST data
     * @return Byte offset to first incoming edge, or -1 if none
     * @throws IOException if file operations fail
     */
    public static long getIncomingEdgeOffset(String baseName, int nodeId, int mlstLength) throws IOException {
        String mlstDataFile = baseName + "_mlst.dat";
        return NodeIndexMapper.getIncomingEdgeOffset(mlstDataFile, nodeId, mlstLength);
    }
    
    /**
     * Read incoming edges for a specific node.
     * 
     * @param baseName Base name for files
     * @param nodeId Node ID to query
     * @param mlstLength Fixed length of MLST data
     * @param nodeMap Map of all nodes (for edge reconstruction)
     * @return List of incoming edges for the node
     * @throws IOException if file operations fail
     */
    public static List<Edge> getIncomingEdges(String baseName, int nodeId, int mlstLength, 
                                               Map<Integer, Node> nodeMap) throws IOException {
        String edgeFile = baseName + "_edges.dat";
        String mlstDataFile = baseName + "_mlst.dat";
        
        long offset = NodeIndexMapper.getIncomingEdgeOffset(mlstDataFile, nodeId, mlstLength);
        if (offset < 0) {
            return List.of(); // No incoming edges
        }
        
        List<Edge> incomingEdges = new java.util.ArrayList<>();
        
        // Read edges starting at offset until we find an edge with a different destination
        long currentOffset = offset;
        Edge edge;
        while ((edge = EdgeListMapper.readEdgeAtOffset(edgeFile, currentOffset, nodeMap)) != null) {
            if (edge.getDestination().getID() != nodeId) {
                break; // Different destination, stop reading
            }
            incomingEdges.add(edge);
            currentOffset += 12; // 12 bytes per edge
        }
        
        return incomingEdges;
    }

    public static void addNode(Node node, List<Edge> incomingEdges, String baseName, int mlstLength) throws IOException {
        String nodeIndexFile = baseName + "_nodes.dat";
        String mlstDataFile = baseName + "_mlst.dat";
        String edgeFile = baseName + "_edges.dat";

        NodeIndexMapper.addNode(node, nodeIndexFile, mlstDataFile, mlstLength);
        EdgeListMapper.addEdges(incomingEdges, node, edgeFile);
    }
}
