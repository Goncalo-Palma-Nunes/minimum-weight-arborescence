package optimalarborescence.inference;

import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.datastructure.heap.MergeableHeapInterface;
import optimalarborescence.datastructure.heap.PairingHeap;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.GraphMapper;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A serializable version of CameriniForest that works with memory-mapped files.
 * 
 * This class enables lazy-loading of edges from memory-mapped files, reducing memory
 * consumption for large graphs during the static Camerini algorithm execution.
 * 
 * Key features:
 * - Edge lazy-loading: Graph edges loaded on-demand per node (via queue initialization)
 * - Memory efficiency: Only loads edges for nodes being processed
 * - File-based operation: Reads from memory-mapped graph files created by GraphMapper
 * 
 * What is persisted:
 * - Graph structure (nodes + edges) via GraphMapper
 * 
 * What is NOT persisted:
 * - Queue state (initialized lazily during algorithm execution)
 * - Union-find structures
 * - Temporary algorithm data structures (leaves, roots, rset, etc.)
 * 
 * Usage patterns:
 * 1. In-memory mode (backward compatibility):
 *    SerializableCameriniForest algo = new SerializableCameriniForest(graph, comparator);
 * 
 * 2. File-based lazy-loading mode:
 *    SerializableCameriniForest algo = new SerializableCameriniForest(graph, comparator, graphFilePath, sequenceLength);
 * 
 * Performance notes:
 * - Lazy-loading reduces memory for large graphs by loading edges on-demand
 * - Trade-off: Slightly slower first access per node due to file I/O
 * - Ideal for graphs where memory is constrained but disk I/O is acceptable
 */
public class SerializableCameriniForest extends CameriniForest {
    
    private String baseName;
    private boolean useMemoryMappedFiles;
    private Map<Integer, Node> nodeMap;
    private Map<Integer, Boolean> queueInitialized;
    
    /**
     * Constructor for in-memory operation (backward compatibility).
     * 
     * @param graph The graph to process
     * @param comparator Edge comparator for determining minimum weight edges
     */
    public SerializableCameriniForest(Graph graph, Comparator<Edge> comparator) {
        super(graph, comparator);
        this.useMemoryMappedFiles = false;
    }
    
    /**
     * Constructor for memory-mapped file operation with lazy loading.
     * 
     * This constructor configures the algorithm to load edges lazily from
     * memory-mapped files. The graph file must already exist at the specified path.
     * 
     * @param graph The graph structure (can be minimal - nodes only, edges loaded lazily)
     * @param comparator Edge comparator for determining minimum weight edges
     * @param baseName Base name for memory-mapped files containing the graph
     * @param sequenceLength Length of sequences for serialization metadata
     * @throws IOException if file operations fail
     */
    public SerializableCameriniForest(Graph graph, Comparator<Edge> comparator, 
                                     String baseName, int sequenceLength) throws IOException {
        super(graph, comparator);
        
        this.baseName = baseName;
        this.useMemoryMappedFiles = true;
        this.queueInitialized = new HashMap<>();
        
        // Load node map for edge reconstruction during lazy loading
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        
        // Mark all queues as uninitialized for lazy loading
        // Note: queues were already created by parent constructor
        for (int i = 0; i < queues.size(); i++) {
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Override getQueue to lazily initialize queues on first access.
     * This loads incoming edges from the memory-mapped file only when needed.
     * 
     * @param v The node whose incoming edge queue to retrieve
     * @return The heap/queue containing incoming edges for node v
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
     * 
     * @param v The node whose incoming edges to load
     * @throws IOException if file operations fail
     */
    private void initializeQueueForNode(Node v) throws IOException {
        // Load incoming edges from memory-mapped files
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(baseName, v.getId(), nodeMap);
        
        MergeableHeapInterface<HeapNode> queue = queues.get(v.getId());
        for (Edge edge : incomingEdges) {
            queue.insert(new HeapNode(edge, null, null));
        }
    }
    
    /**
     * Override to prevent eager initialization when using memory-mapped files.
     * 
     * When using memory-mapped files, queues are initialized lazily on first access
     * rather than eagerly during construction.
     */
    @Override
    protected void initializeDataStructures() {
        if (!useMemoryMappedFiles) {
            // Use parent's initialization for in-memory operation
            super.initializeDataStructures();
        }
        // Otherwise do nothing - we initialize lazily via getQueue()
    }
    
    /**
     * Enable memory-mapped file operation and save the graph to the specified location.
     * This can be called after constructing with the in-memory constructor to transition
     * to file-based operation.
     * 
     * @param baseName Base name for memory-mapped files
     * @param sequenceLength Length of sequences for serialization metadata
     * @throws IOException if file operations fail
     */
    public void setBaseName(String baseName, int sequenceLength) throws IOException {
        this.baseName = baseName;
        this.useMemoryMappedFiles = true;
        this.queueInitialized = new HashMap<>();
        
        // Save graph to memory-mapped files
        GraphMapper.saveGraph(graph, sequenceLength, baseName);
        
        // Load node map from saved files
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        
        // Clear all queues and mark for lazy initialization
        for (int i = 0; i < queues.size(); i++) {
            queues.set(i, new PairingHeap(maxDisjointCmp));
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Check if this instance is using memory-mapped files for lazy loading.
     * 
     * @return true if using memory-mapped files, false if using in-memory operation
     */
    public boolean isUsingMemoryMappedFiles() {
        return useMemoryMappedFiles;
    }
    
    /**
     * Get the base name of the memory-mapped files being used.
     * 
     * @return The base name, or null if not using memory-mapped files
     */
    public String getBaseName() {
        return baseName;
    }
}
