package optimalarborescence.inference;

import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.datastructure.heap.MergeableHeapInterface;
import optimalarborescence.datastructure.heap.PairingHeap;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;
import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.distance.DistanceFunction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private boolean onDemand = false;
    private NearestNeighbourSearchAlgorithm<?> nnAlgorithm = null;
    private DistanceFunction distanceFunction = null;
    private int numNeighbors = 0;
    private boolean symmetric = true;
    private boolean useMemoryMappedFiles;
    private Map<Integer, Node> nodeMap;
    private Map<Integer, Boolean> queueInitialized;
    private EdgeListMapper.EdgeLoader edgeLoader;  // Keeps file channel open during algorithm execution
    private Map<Integer, Long> nodeOffsetCache;  // Preloaded offsets to avoid repeated file opens
    /**
     * Maps SCC representative ID to the set of all node IDs that have been merged into this SCC.
     * Updated during contractionPhase when cycles are detected and nodes are unified.
     * Used during queue re-initialization to load edges for all nodes in the SCC.
     */
    private Map<Integer, Set<Integer>> sccComposition;
    
    /**
     * Constructor for in-memory operation (backward compatibility).
     * 
     * @param graph The graph to process
     * @param comparator Edge comparator for determining minimum weight edges
     */
    public SerializableCameriniForest(Graph graph, Comparator<Edge> comparator) {
        super(graph, comparator);
        this.useMemoryMappedFiles = false;
        this.sccComposition = new HashMap<>();
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
        this.sccComposition = new HashMap<>();
        
        // Load node map for edge reconstruction during lazy loading
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        
        // Preload all node offsets to avoid repeated file opens during lazy loading
        this.nodeOffsetCache = preloadNodeOffsets(baseName);
        
        // Mark all queues as uninitialized for lazy loading
        // Note: queues were already created by parent constructor
        for (int i = 0; i < queues.size(); i++) {
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Constructor for memory-mapped file operation with full lazy loading.
     * 
     * This constructor enables true lazy loading by loading only the node structure
     * from memory-mapped files. Edges are loaded on-demand when needed during
     * algorithm execution. This is the most memory-efficient mode.
     * 
     * @param comparator Edge comparator for determining minimum weight edges
     * @param baseName Base name for memory-mapped files containing the graph
     * @param onDemand If true, edge weights are computed on-the-fly (no edges stored)
     * @throws IOException if file operations fail
     */
    public SerializableCameriniForest(Comparator<Edge> comparator, String baseName, boolean onDemand, NearestNeighbourSearchAlgorithm<?> nnAlgorithm, int numNeighbors, DistanceFunction distanceFunction, boolean symmetric) throws IOException {
        super(createMinimalGraph(baseName), comparator);
        
        this.baseName = baseName;
        this.onDemand = onDemand;
        this.nnAlgorithm = nnAlgorithm;
        this.numNeighbors = numNeighbors;
        this.distanceFunction = distanceFunction;
        this.symmetric = symmetric;
        this.useMemoryMappedFiles = true;
        this.queueInitialized = new HashMap<>();
        this.sccComposition = new HashMap<>();
        
        // Load node map for edge reconstruction during lazy loading
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        
        // Preload all node offsets to avoid repeated file opens during lazy loading
        this.nodeOffsetCache = preloadNodeOffsets(baseName);
        
        // Mark all queues as uninitialized for lazy loading
        // Note: queues were already created by parent constructor with empty graph
        for (int i = 0; i < queues.size(); i++) {
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Create a minimal graph with only nodes (no edges) from memory-mapped files.
     * This allows the parent constructor to set up data structures without loading edges.
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

    private void clearQueue(MergeableHeapInterface<HeapNode> q, int nodeId) {
        q.clear();
        queueInitialized.put(nodeId, false);
    }

    /**
     * Override contractionPhase to clear queues after processing a node. A cleared queue is 
     * re-initialized if the node is accessed again (this only happens if it is part of a contraction),
     * allowing for lazy re-loading of edges. This helps manage memory usage when working with large graphs.
     * <p>
     * This method is only used when useMemoryMappedFiles is true.
     * </p>
     * Note: This method essentially duplicates the code from the parent class with the addition of clearing the queue.
     */
    private void contractionPhase() {
        int edgesSelected = 0;
        int cyclesDetected = 0;
        while (!roots.isEmpty()) {
                Node root = roots.remove(0);
                MergeableHeapInterface<HeapNode> q = getQueue(sccFind(root)); // priority queue of edges entering r

                if (emptyQueue(q)) {
                    rset.add(root);
                    continue;
                }
                Edge e = q.extractMin().getEdge();
                while (!emptyQueue(q) && sccFind(e.getSource()) == sccFind(e.getDestination())) {
                    e = q.extractMin().getEdge();
                }

                if (sccFind(e.getSource()) == sccFind(e.getDestination())) {
                    // Both ends of the edge are in the same SCC, skip this edge
                    rset.add(root);
                    // TODO - dar clear da queue aqui também só por consistência?
                    continue;
                }

                Node u = e.getSource();
                Node v = e.getDestination();

                TarjanForestNode minNode = createMinNode(e);
                if (wccFind(u) != wccFind(v)) {
                    // no cycle formed
                    inEdgeNode.set(root.getId(), minNode);
                    edgesSelected++;
                    System.out.println("DEBUG: Edge selected (no cycle): " + e + ", total selected: " + edgesSelected);
                    wccUnion(u, v);
                    clearQueue(q, sccFind(root).getId()); // Clear the queue to free memory
                }
                else {

                    // store nodes in cycle
                    List<Integer> contractionSet = new ArrayList<>();
                    contractionSet.add(sccFind(v).getId());

                    // keep track of the edges in the cycle
                    List<TarjanForestNode> edgeNodesInCycle = new ArrayList<>();
                    edgeNodesInCycle.add(minNode);

                    // map the edge incident in a node
                    Map<Integer, TarjanForestNode> map = new HashMap<>();
                    map.put(sccFind(v).getId(), minNode);

                    // since a cycle as arisen we need to choose a new minimum weight edge incident in node root
                    inEdgeNode.set(root.getId(), null);

                    Set<Integer> visited = new HashSet<>();
                    for (int i = sccFind(u).getId(); inEdgeNode.get(i) != null; i = sccFind(inEdgeNode.get(i).edge.getSource()).getId()) {
                        if (visited.contains(i)) {
                            break;  // Cycle detected - prevent infinite loop
                        }
                        visited.add(i);
                        map.put(i, inEdgeNode.get(i));
                        edgeNodesInCycle.add(inEdgeNode.get(i));
                        contractionSet.add(i);
                    }

                    TarjanForestNode maxWeightTarjanNode = getMaxWeightEdgeInCycle(edgeNodesInCycle);
                    Edge maxWeightEdge = maxWeightTarjanNode.edge;
                    Node dst = sccFind(maxWeightEdge.getDestination());

                    int sigma = getAdjustedWeight(maxWeightEdge).intValue();
                    updateReducedCosts(contractionSet, sigma, map);

                    for (TarjanForestNode n: edgeNodesInCycle) { // Perform union of the nodes in the cycle
                        sccUnion(n.edge.getSource(), n.edge.getDestination());
                    }

                    Node rep = sccFind(maxWeightEdge.getDestination());
                    cyclesDetected++;
                    System.out.println("DEBUG: Cycle detected #" + cyclesDetected + ", cycle size: " + edgeNodesInCycle.size() + ", rep: " + rep.getId());
                    
                    // Track SCC composition for lazy queue re-initialization
                    // Collect all original node IDs being merged into this SCC
                    // Use edgeNodesInCycle instead of contractionSet because contractionSet
                    // contains Union-Find representative IDs, not original graph node IDs
                    Set<Integer> originalNodeIds = new HashSet<>();
                    
                    for (TarjanForestNode n : edgeNodesInCycle) {
                        int srcId = n.edge.getSource().getId();
                        int dstId = n.edge.getDestination().getId();
                        
                        // Add source's composition
                        if (sccComposition.containsKey(srcId)) {
                            originalNodeIds.addAll(sccComposition.get(srcId));
                        } else {
                            originalNodeIds.add(srcId);
                        }
                        
                        // Add destination's composition
                        if (sccComposition.containsKey(dstId)) {
                            originalNodeIds.addAll(sccComposition.get(dstId));
                        } else {
                            originalNodeIds.add(dstId);
                        }
                    }
                    
                    // Store the composition for the new representative
                    int repId = rep.getId();
                    sccComposition.put(repId, originalNodeIds);
                    
                    // Clean up old entries for nodes that are now merged
                    for (TarjanForestNode n : edgeNodesInCycle) {
                        int srcId = n.edge.getSource().getId();
                        int dstId = n.edge.getDestination().getId();
                        if (srcId != repId) {
                            sccComposition.remove(srcId);
                        }
                        if (dstId != repId) {
                            sccComposition.remove(dstId);
                        }
                    }
                    
                    roots.add(0, rep); // Add representative to roots to be processed again
                    for (Integer node : contractionSet) { // Merge queues involved in the cycle
                        if (rep.getId() != node) {
                            MergeableHeapInterface<HeapNode> nodeQueue = getQueue(getNodes().get(node));
                            int queueSizeBefore = nodeQueue.size();
                            getQueue(rep).merge(nodeQueue);
                            System.out.println("DEBUG: Merged queue of node " + node + " (size: " + queueSizeBefore + ") into rep " + rep.getId());
                            // Clear the merged queue to free memory
                            clearQueue(nodeQueue, node);
                        }
                    }
                    updateMax(rep, dst);
                    cycleEdgeNodes.set(rep.getId(), edgeNodesInCycle);
                }
            }
        System.out.println("\nDEBUG: Contraction phase complete:");
        System.out.println("  - Edges selected (no cycle): " + edgesSelected);
        System.out.println("  - Cycles detected: " + cyclesDetected);
        System.out.println("  - Nodes in rset: " + rset.size());
        
        // Count non-null entries in inEdgeNode
        int nonNullEdges = 0;
        for (int i = 0; i < inEdgeNode.size(); i++) {
            if (inEdgeNode.get(i) != null) {
                nonNullEdges++;
            }
        }
        System.out.println("  - Non-null entries in inEdgeNode: " + nonNullEdges);
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
     * Preload all node offsets from the node file into a map.
     * This eliminates the need to open the node file repeatedly during lazy loading.
     * 
     * @param baseName Base name for files
     * @return Map of node ID to incoming edge offset
     * @throws IOException if file operations fail
     */
    private Map<Integer, Long> preloadNodeOffsets(String baseName) throws IOException {
        String nodeFile = baseName + "_nodes.dat";
        
        // Get all node IDs from nodeMap
        Set<Integer> allNodeIds = nodeMap.keySet();
        
        // Load all offsets in one batch operation
        return optimalarborescence.memorymapper.NodeIndexMapper.getIncomingEdgeOffsetsBatch(
            nodeFile, allNodeIds);
    }

    
    /**
     * Initialize the queue for a specific node by loading its incoming edges from disk.
     * If the node is part of an SCC, loads edges for all nodes in the SCC.
     * Uses preloaded offset cache and EdgeLoader for maximum efficiency.
     * 
     * @param v The node whose incoming edges to load
     * @throws IOException if file operations fail
     */
    private void initializeQueueForNode(Node v) throws IOException {
        // Find the SCC representative for this node
        Node rep = sccFind(v);
        int repId = rep.getId();
        System.out.println("DEBUG: Initializing queue for node " + v.getId() + " (rep: " + repId + ")");
        
        // Determine which nodes' edges need to be loaded
        Set<Integer> nodesToLoad;
        if (sccComposition.containsKey(repId)) {
            // This is an SCC representative with merged queues
            // Load edges for ALL nodes in this SCC
            nodesToLoad = sccComposition.get(repId);
        } else {
            // This node is not part of any SCC (or is a singleton SCC)
            // Load only its own edges
            nodesToLoad = new HashSet<>();
            nodesToLoad.add(repId);
        }
        
        // Get the queue for the representative (this is the merged queue)
        MergeableHeapInterface<HeapNode> queue = queues.get(repId);
        
        System.out.println("DEBUG:   - Loading edges for " + nodesToLoad.size() + " node(s): " + nodesToLoad);
        
        // Load and insert edges for all nodes in the SCC
        int totalEdgesLoaded = 0;
        for (Integer nodeId : nodesToLoad) {
            List<Edge> incomingEdges = new ArrayList<>();
            if (onDemand) {
                if (numNeighbors > 0) {
                    // Compute incoming edges on-the-fly using nearest neighbor search
                    Node node = nodeMap.get(nodeId);
                    
                    // NNSearch
                    @SuppressWarnings("unchecked")
                    List<Point<Object>> neighbors = ((NearestNeighbourSearchAlgorithm<Object>) nnAlgorithm)
                        .neighbourSearch((Point<Object>) node.getPoint(), numNeighbors);

                    for (Point<?> neighbor : neighbors) {
                        Node otherNode = new Node(neighbor);
                        Edge edge = buildEdge(otherNode, node, distanceFunction);
                        if (edge.getDestination().getId() == nodeId) {
                            incomingEdges.add(edge);
                        }
                    }
                }
                else {
                    // Complete graph. Compute all distances to other nodes
                    Node node = nodeMap.get(nodeId);
                    for (Node otherNode : nodeMap.values()) {
                        if (otherNode.getId() != nodeId) {
                            Edge edge = buildEdge(otherNode, node, distanceFunction);
                            if (edge.getDestination().getId() == nodeId) {
                                incomingEdges.add(edge);
                            }
                        }
                    }
                }

            }
            else {
                Long offset = nodeOffsetCache.get(nodeId);
                
                if (offset == null) {
                    // This node doesn't exist in the original graph - this shouldn't happen
                    System.err.println("WARNING: Node " + nodeId + " not found in nodeOffsetCache. " +
                                    "This indicates an issue with SCC composition tracking.");
                    System.err.println("Representative: " + repId + ", SCC composition: " + nodesToLoad);
                    continue;
                }
                
                if (offset < 0) {
                    // No incoming edges for this node (but the node exists)
                    System.out.println("DEBUG:     Node " + nodeId + " has no incoming edges (offset < 0)");
                    continue;
                }
                
                // Load incoming edges for this node
                if (edgeLoader != null) {
                    incomingEdges = edgeLoader.loadLinkedList(offset, nodeMap);
                } else {
                    // Fallback - should rarely happen
                    incomingEdges = GraphMapper.getIncomingEdges(baseName, nodeId, nodeMap);
                }
                System.out.println("DEBUG:     Node " + nodeId + " loaded " + incomingEdges.size() + " edges from file (offset: " + offset + ")");
            }
            
            // Insert all edges into the representative's queue
            for (Edge edge : incomingEdges) {
                queue.insert(new HeapNode(edge, null, null));
            }
            totalEdgesLoaded += incomingEdges.size();
        }
        System.out.println("DEBUG:   - Total edges inserted into queue for rep " + repId + ": " + totalEdgesLoaded);
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
        this.sccComposition = new HashMap<>();
        
        // Save graph to memory-mapped files
        GraphMapper.saveGraph(graph, sequenceLength, baseName);
        
        // Load node map from saved files
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        
        // Preload all node offsets to avoid repeated file opens during lazy loading
        this.nodeOffsetCache = preloadNodeOffsets(baseName);
        
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

    private Edge buildEdge(Node u, Node v, DistanceFunction distanceFunction) {
        if (symmetric) {
            int dist = (int) distanceFunction.calculate(u.getPoint().getSequence(), v.getPoint().getSequence());
            return new Edge(u, v, dist);
        }
        else {
            Edge e;
            if (u.getPoint().getSequence().getPositionsWithMissingData().size() <= v.getPoint().getSequence().getPositionsWithMissingData().size()) {
                int dist = (int) distanceFunction.calculate(u.getPoint().getSequence(), v.getPoint().getSequence());
                e = new Edge(u, v, dist);
            }
            else {
                int dist = (int) distanceFunction.calculate(v.getPoint().getSequence(), u.getPoint().getSequence());
                e = new Edge(v, u, dist);
            }
            return e;
        }
    }
    
    /**
     * Get the base name of the memory-mapped files being used.
     * 
     * @return The base name, or null if not using memory-mapped files
     */
    public String getBaseName() {
        return baseName;
    }
    
    /**
     * Override inferPhylogeny to manage EdgeLoader lifecycle.
     * Opens the EdgeLoader before inference and closes it afterwards,
     * maintaining lazy loading while keeping the file channel open.
     * 
     * @param graph Unused parameter (for compatibility with parent interface)
     * @return The inferred phylogenetic tree
     */
    @Override
    public Graph inferPhylogeny(Graph graph) {
        if (!useMemoryMappedFiles) {
            // No EdgeLoader needed for in-memory operation
            return super.inferPhylogeny(graph);
        }
        

        if (onDemand) {
            contractionPhase();
            List<Edge> forest = expansionPhase();
            return new Graph(forest);
        }
        else {
            // Open EdgeLoader for efficient lazy loading during algorithm execution
            String edgeFile = baseName + "_edges.dat";
            try (EdgeListMapper.EdgeLoader loader = new EdgeListMapper.EdgeLoader(edgeFile)) {
                this.edgeLoader = loader;
                
                // Run the algorithm with EdgeLoader active and custom contractionPhase
                contractionPhase();
                List<Edge> forest = expansionPhase();
                return new Graph(forest);
                
            } catch (IOException e) {
                throw new RuntimeException("Failed to open EdgeLoader for inference", e);
            } finally {
                // Clean up reference
                this.edgeLoader = null;
            }
        }
    }
}
