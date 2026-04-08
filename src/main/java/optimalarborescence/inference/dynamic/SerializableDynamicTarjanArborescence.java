package optimalarborescence.inference.dynamic;

import optimalarborescence.datastructure.heap.LinearSearchArray;
import optimalarborescence.datastructure.heap.MergeableHeapInterface;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.memorymapper.ATreeMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A serializable version of DynamicTarjanArborescence that works with memory-mapped files.
 * 
 * This class enables lazy-loading of edges from memory-mapped files.
 */
public class SerializableDynamicTarjanArborescence extends DynamicTarjanArborescence {

    private String baseName;
    private boolean useMemoryMappedFiles;
    private Map<Integer, Node> nodeMap;
    private Map<Integer, Boolean> queueInitialized;
    
    /**
     * Constructor for in-memory operation (backward compatibility).
     */
    public SerializableDynamicTarjanArborescence(List<ATreeNode> aTreeRoots, 
                                     List<Edge> contractedEdges, 
                                     Map<Integer, Integer> reducedCosts,
                                     Graph originalGraph) {
        super(aTreeRoots, contractedEdges, reducedCosts, originalGraph,
              (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight()));
        this.useMemoryMappedFiles = false;
    }
    
    /**
     * Constructor for memory-mapped file operation.
     * 
     * This constructor loads from existing memory-mapped files for lazy loading.
     * The files must already exist (created via setBaseName() after running the algorithm).
     * 
     * @param aTreeRoots The ATree forest roots
     * @param contractedEdges Edges from decomposed contractions
     * @param reducedCosts Map of reduced costs for edges
     * @param originalGraph The original graph structure (can be minimal - just node list)
     * @param baseName Base name for memory-mapped files to load the MODIFIED graph
     * @param mlstLength Fixed length for MLST data in bytes (unused, kept for compatibility)
     * @throws IOException if file operations fail
     */
    public SerializableDynamicTarjanArborescence(List<ATreeNode> aTreeRoots,
                                     List<Edge> contractedEdges,
                                     Map<Integer, Integer> reducedCosts,
                                     Graph originalGraph,
                                     String baseName,
                                     int mlstLength) throws IOException {
        super(aTreeRoots, contractedEdges, reducedCosts, originalGraph,
              (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight()));

        this.baseName = baseName;
        this.useMemoryMappedFiles = true;
        this.queueInitialized = new HashMap<>();
        
        // Load node map for edge reconstruction during lazy loading
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        
        // Clear queues that were initialized by parent constructor - we'll do it lazily
        // Note: queues size is based on maxNodeId + 1, not node count
        for (int i = 0; i < queues.size(); i++) {
            queues.set(i, new LinearSearchArray(0, maxDisjointCmp));
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Default constructor for empty initialization.
     */
    public SerializableDynamicTarjanArborescence() {
        super(new ArrayList<>(), new ArrayList<>(), Map.of(), 
              new Graph(new ArrayList<>()), 
              (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight()));
        this.useMemoryMappedFiles = false;
    }
    
    /**
     * Constructor for ATree loading with edge lazy-loading.
     *
     * This constructor loads the ATree forest from disk and configures
     * edge lazy-loading from memory-mapped files.
     *
     * @param baseName Base name for memory-mapped files
     * @param mlstLength Fixed length for MLST data in bytes (unused, kept for compatibility)
     * @param originalGraph The original graph structure (for node list)
     * @throws IOException if file operations fail
     */
    public SerializableDynamicTarjanArborescence(String baseName, int mlstLength, Graph originalGraph)
            throws IOException {
        this(loadATreeRoots(baseName),
             new ArrayList<>(),
             new HashMap<>(),
             originalGraph,
             baseName,
             mlstLength);
    }

    /**
     * Constructor for loading from memory-mapped files without requiring a Graph instance.
     *
     * This constructor loads the graph structure (nodes only) from memory-mapped files,
     * then loads the ATree forest if it exists. If ATree files don't exist, creates empty ATrees.
     * Edges are loaded on-demand.
     *
     * @param baseName Base name for memory-mapped files containing the saved state
     * @throws IOException if file operations fail
     */
    public SerializableDynamicTarjanArborescence(String baseName) throws IOException {
        this(loadATreeRoots(baseName),
             new ArrayList<>(),
             new HashMap<>(),
             createMinimalGraph(baseName),
             baseName,
             0);
    }
    
    /**
     * Create a minimal graph with only nodes (no edges) from memory-mapped files.
     * This allows the algorithm to operate without loading all edges into memory.
     * 
     * @param baseName Base name for memory-mapped files
     * @return Graph with nodes but no edges
     * @throws IOException if file operations fail
     */
    private static Graph createMinimalGraph(String baseName) throws IOException {
        Map<Integer, Node> nodeMap = GraphMapper.loadNodeMap(baseName);
        Graph graph = new Graph(new ArrayList<>());  // Empty edges list
        
        // Add all nodes to the graph
        for (Node node : nodeMap.values()) {
            graph.addNode(node);
        }
        
        return graph;
    }
    
    /**
     * Helper method to load ATree roots eagerly.
     *
     * Note: This loads the modified graph's node map since ATrees reference
     * nodes from the modified (partially contracted) graph, not the original graph.
     * If ATree files don't exist, returns an empty list (useful for fresh graphs).
     */
    private static List<ATreeNode> loadATreeRoots(String baseName) throws IOException {
        Map<Integer, Node> graphNodes = GraphMapper.loadNodeMap(baseName);
        try {
            return ATreeMapper.loadATreeForest(baseName, graphNodes);
        } catch (java.io.FileNotFoundException e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * Override getQueue to lazily initialize queues on first access.
     * This loads incoming edges from the memory-mapped file only when needed.
     * 
     * IMPORTANT: Edges are loaded from the MODIFIED graph files (with reduced costs),
     * not the original graph. This ensures we only load the edges that the algorithm
     * actually needs to consider.
     */
    @Override
    protected MergeableHeapInterface<int[]> getQueue(Node v) {
        if (!useMemoryMappedFiles) {
            // Use parent's implementation for in-memory operation
            return super.getQueue(v);
        }
        
        int nodeId = v.getId();
        
        // Initialize queue on first access
        if (!queueInitialized.getOrDefault(nodeId, false)) {
            try {
                initializeQueueForNode(v);
                queueInitialized.put(nodeId, true);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize queue for node " + nodeId, e);
            }
        }
        
        return queues.get(nodeId);
    }
    
    /**
     * Initialize the queue for a specific node by loading its incoming edges from disk.
     * Loads from the MODIFIED graph with reduced costs, not the original graph.
     */
    private void initializeQueueForNode(Node v) throws IOException {
        // Load edges from the modified graph files (with reduced costs)
        List<Edge> incomingEdges = GraphMapper.loadIncidentEdges(baseName, v.getId());
        
        MergeableHeapInterface<int[]> queue = queues.get(v.getId());
        for (Edge edge : incomingEdges) {
            queue.insert(new int[]{ edge.getWeight(), edge.getSource().getId(), edge.getDestination().getId() });
        }
    }
    
    /**
     * Save the modified graph (with reduced costs) to memory-mapped files.
     * This allows edges to be loaded lazily during algorithm execution.
     * 
     * @param mlstLength Fixed length for MLST data in bytes
     * @throws IOException if file operations fail
     */
    private void saveModifiedGraphToFiles(int mlstLength) throws IOException {
        Graph modifiedGraph = getModifiedGraph();
        GraphMapper.saveGraph(modifiedGraph, mlstLength, baseName);
    }
    
    /**
     * Override to prevent eager initialization when using memory-mapped files.
     */
    @Override
    protected void initializeDataStructures() {
        if (!useMemoryMappedFiles) {
            // Use parent's initialization for in-memory operation
            super.initializeDataStructures();
        }
        // Otherwise do nothing - we initialize lazily
    }
    
    /**
     * Set the base name for memory-mapped files and enable file-based operation.
     * Saves the modified graph and ATree forest to the specified location.
     * 
     * @param baseName Base name for memory-mapped files
     * @param mlstLength Fixed length for MLST data in bytes
     * @throws IOException if file operations fail
     */
    public void setBaseName(String baseName, int mlstLength) throws IOException {
        this.baseName = baseName;
        this.useMemoryMappedFiles = true;
        this.queueInitialized = new HashMap<>();
        
        // Save modified graph to files
        saveModifiedGraphToFiles(mlstLength);
        
        // Save ATree forest to files
        saveATreeForest(baseName);
        
        // Load node map from saved files
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        
        // Clear queues for lazy initialization
        // Note: queues size is based on maxNodeId + 1, not node count
        for (int i = 0; i < queues.size(); i++) {
            queues.set(i, new LinearSearchArray(0, maxDisjointCmp));
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Save the ATree forest to memory-mapped files.
     * 
     * @param baseName Base name for the files
     * @throws IOException if file operations fail
     */
    private void saveATreeForest(String baseName) throws IOException {
        Map<Integer, Node> graphNodes = new HashMap<>();
        for (Node node : getModifiedGraph().getNodes()) {
            graphNodes.put(node.getId(), node);
        }
        ATreeMapper.saveATreeForest(getATreeRoots(), graphNodes, baseName);
    }
}
