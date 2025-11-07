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
     * Add a single node and its incoming edges to the graph files.
     *
     * @param node
     * @param incomingEdges
     * @param baseName
     * @param mlstLength
     * @throws IOException
     */
    public static void addNode(Node node, List<Edge> incomingEdges, String baseName, int mlstLength) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        String edgeFile = baseName + "_edges.dat";

        NodeIndexMapper.addNode(node, nodeFile, mlstLength);
        EdgeListMapper.addEdges(incomingEdges, node, edgeFile);
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
}
