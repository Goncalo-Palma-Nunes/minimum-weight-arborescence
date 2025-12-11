package optimalarborescence.inference.dynamic;

import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.datastructure.heap.MergeableHeapInterface;
import optimalarborescence.datastructure.heap.PairingHeap;
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
 * This class enables lazy-loading of edges and ATrees from memory-mapped files.
 * 
 * Two lazy-loading modes:
 * 1. Edge lazy-loading: Modified graph edges loaded on-demand (via queue initialization)
 * 2. ATree lazy-loading: ATree nodes and children loaded on-demand (via getATreeChildren())
 * 
 * What is persisted:
 * - Original graph (nodes + edges) via GraphMapper - for reference
 * - Modified graph (nodes + edges with reduced costs) via GraphMapper - for algorithm execution
 * - ATree forest structure via ATreeMapper
 * - Current arborescence (the solution) - to be implemented
 * 
 * What is NOT persisted:
 * - Queue state (initialized lazily during algorithm execution)
 * - Temporary algorithm data structures
 * 
 * Performance notes:
 * - Edge lazy-loading: Reduces memory for large graphs during algorithm execution
 * - ATree lazy-loading: Reduces memory for deep/wide ATree forests during dynamic operations
 * - Future enhancement: Could add LRU cache eviction for loaded nodes/edges
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
                                     Map<Edge, Integer> reducedCosts,
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
                                     Map<Edge, Integer> reducedCosts,
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
        for (int i = 0; i < originalGraph.getNumNodes(); i++) {
            queues.set(i, new PairingHeap(maxDisjointCmp));
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
     * Constructor for lazy ATree loading with edge lazy-loading.
     * 
     * This constructor loads ATree roots without their children, and configures
     * edge lazy-loading. Children are loaded on-demand when accessed.
     * 
     * @param baseName Base name for memory-mapped files
     * @param mlstLength Fixed length for MLST data in bytes (unused, kept for compatibility)
     * @param originalGraph The original graph structure (for node list)
     * @throws IOException if file operations fail
     */
    public SerializableDynamicTarjanArborescence(String baseName, int mlstLength, Graph originalGraph) 
            throws IOException {
        // Load ATrees lazily (only roots)
        this(loadATreeRootsLazy(baseName, originalGraph), 
             new ArrayList<>(), 
             new HashMap<>(), 
             originalGraph,
             baseName,
             mlstLength);
    }
    
    /**
     * Helper method to load ATree roots lazily.
     * 
     * Note: This loads the modified graph's node map since ATrees reference
     * nodes from the modified (partially contracted) graph, not the original graph.
     */
    private static List<ATreeNode> loadATreeRootsLazy(String baseName, Graph graph) throws IOException {
        // Load node map from the modified graph (saved during setBaseName)
        Map<Integer, Node> graphNodes = GraphMapper.loadNodeMap(baseName);
        return ATreeMapper.loadATreeRootsLazy(baseName, graphNodes);
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
    protected MergeableHeapInterface<HeapNode> getQueue(Node v) {
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
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(baseName, v.getId(), nodeMap);
        
        MergeableHeapInterface<HeapNode> queue = queues.get(v.getId());
        for (Edge edge : incomingEdges) {
            queue.insert(new HeapNode(edge, null, null));
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
        for (int i = 0; i < getModifiedGraph().getNumNodes(); i++) {
            queues.set(i, new PairingHeap(maxDisjointCmp));
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
