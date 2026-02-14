package optimalarborescence.inference;

import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.datastructure.heap.MergeableHeapInterface;
import optimalarborescence.datastructure.heap.PairingHeap;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
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
 * A serializable version of CameriniForest that works with memory-mapped files or on-demand edge weight computation.
 * <p>
 * This class enables lazy-loading of edges from memory-mapped files, reducing memory
 * consumption for large graphs during the static Camerini algorithm execution. Alternatively, it can
 * compute edge weights on-demand, avoiding the large amount of disk space required by the memory mapping approach,
 * at the expense of increased computation time.
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
    /**
     * Maps SCC representative ID to the set of all node IDs that have been merged into this SCC.
     * Updated during contractionPhase when cycles are detected and nodes are unified.
     * Used during queue re-initialization to load edges for all nodes in the SCC.
     */
    private Map<Integer, Set<Integer>> sccComposition;

    /**
     * Tracks the number of edges examined for each node when running with lazy loading with on-demand edge computation.
     * If a node's numExaminedEdges reaches the numNeighbors limit, no more nearest neighbor searches will be
     * performed for that node and instead we compute the entire list of incoming edges
     */
    private int[] numExaminedEdges;

    /**
     * Used to track if a SCC has been previously initialized with on-demand edge computation and 
     * with nearest neighbor search. If it has and it failed to find an adequate edge during the
     * contraction phase, prevFailure[root] is set to true and the queue must be re-initialized
     * with the complete list of incoming edges to that SCC
     */
    private boolean[] prevFailure = null;

    
    /**
     * Constructor for in-memory operation (backward compatibility).
     * 
     * @param graph The graph to process
     * @param comparator Edge comparator for determining minimum weight edges
     */
    public SerializableCameriniForest(Graph graph, Comparator<Edge> comparator) {
        super(graph, comparator);
        this.useMemoryMappedFiles = false;
        this.queueInitialized = new HashMap<>();
        this.sccComposition = new HashMap<>();
        this.numExaminedEdges = new int[graph.getNodes().size()];
        this.prevFailure = new boolean[graph.getNodes().size()];
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
        this.numExaminedEdges = new int[graph.getNodes().size()];
        this.prevFailure = new boolean[graph.getNodes().size()];
        
        this.nodeMap = GraphMapper.loadNodeIdsOnly(baseName);
        
        // Mark all queues as uninitialized for lazy loading
        // Note: queues were already created by parent constructor
        for (int i = 0; i < queues.size(); i++) {
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Constructor for memory-mapped file operation with full lazy loading and on-demand edge weight computation.
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
        super(createMinimalGraph(baseName, onDemand), comparator);
        
        this.baseName = baseName;
        this.onDemand = onDemand;
        this.nnAlgorithm = nnAlgorithm;
        this.numNeighbors = numNeighbors;
        this.distanceFunction = distanceFunction;
        this.symmetric = symmetric;
        this.useMemoryMappedFiles = true;
        this.queueInitialized = new HashMap<>();
        this.sccComposition = new HashMap<>();
        this.numExaminedEdges = new int[graph.getNodes().size()];

        if (numNeighbors > 0) {
            this.prevFailure = new boolean[graph.getNodes().size()];
        }
        
        // If onDemand=true: Need full sequences for edge weight computation
        // If onDemand=false: Only need node IDs (edges pre-computed on disk)
        if (onDemand) {
            this.nodeMap = GraphMapper.loadNodeMap(baseName);
        } else {
            this.nodeMap = GraphMapper.loadNodeIdsOnly(baseName);
        }
        
        // Mark all queues as uninitialized for lazy loading
        // Note: queues were already created by parent constructor with empty graph
        for (int i = 0; i < queues.size(); i++) {
            queueInitialized.put(i, false);
        }
    }
    
    /**
     * Create a minimal graph with only nodes (no edges) from memory-mapped files.
     * This allows the parent constructor to set up data structures without loading edges.
     * Nodes are loaded with or without sequences depending on whether onDemand mode is enabled.
     * 
     * @param baseName Base name for memory-mapped files
     * @param onDemand If true, load full sequences; if false, load only IDs
     * @return Graph with nodes but no edges
     * @throws IOException if file operations fail
     */
    private static Graph createMinimalGraph(String baseName, boolean onDemand) throws IOException {
        // Load nodes with or without sequences based on whether we need them
        Map<Integer, Node> nodeMap = onDemand ? 
            GraphMapper.loadNodeMap(baseName) : 
            GraphMapper.loadNodeIdsOnly(baseName);
        Graph graph = new Graph(new ArrayList<>());  // Empty edges list
        
        // Add all nodes to the graph
        for (Node node : nodeMap.values()) {
            graph.addNode(node);
        }
        
        return graph;
    }

    private void clearQueue(MergeableHeapInterface<HeapNode> q, int nodeId) {
           // Diagnostics: heap usage before clear
        Runtime runtime = Runtime.getRuntime();
        long usedBefore = runtime.totalMemory() - runtime.freeMemory();
        System.err.println(String.format("[DIAG] Clearing queue for node %d: heap before=%.2f MB", nodeId, usedBefore / (1024.0 * 1024.0)));

           q.clear();
           q = null;
           queueInitialized.put(nodeId, false);

           // Diagnostics: heap usage after clear (before and after GC)
           long usedAfter = runtime.totalMemory() - runtime.freeMemory();
           System.err.println(String.format("[DIAG] Cleared queue for node %d: heap after clear=%.2f MB", nodeId, usedAfter / (1024.0 * 1024.0)));
           System.gc();
           try { Thread.sleep(50); } catch (InterruptedException e) { }
           long usedAfterGC = runtime.totalMemory() - runtime.freeMemory();
           System.err.println(String.format("[DIAG] Cleared queue for node %d: heap after GC=%.2f MB", nodeId, usedAfterGC / (1024.0 * 1024.0)));
    }


    private Edge extractMinEdge(MergeableHeapInterface<HeapNode> q) {
        if (emptyQueue(q)) {
            return null;
        }
        Edge e = q.extractMin().getEdge();
        while (!emptyQueue(q) && sccFind(e.getSource()) == sccFind(e.getDestination())) {
            e = q.extractMin().getEdge();
            this.numExaminedEdges[e.getDestination().getId()]++; // Increment the count of examined edges for the destination node
        }
        return e;
    }

    private boolean isApproximatedOnDemand() {
        return onDemand && numNeighbors > 0;
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
        while (!roots.isEmpty()) {
                Node root = roots.remove(0);
                MergeableHeapInterface<HeapNode> q = getQueue(sccFind(root)); // priority queue of edges entering r

                if (emptyQueue(q)) {
                    rset.add(root);
                    continue;
                }
                Edge e = extractMinEdge(q);

                if (isApproximatedOnDemand() && emptyQueue(q) && sccFind(e.getSource()) == sccFind(e.getDestination())) {
                    this.prevFailure[root.getId()] = true; // Mark this node as having failed to find an edge in this iteration
                    clearQueue(q, sccFind(root).getId()); // Clear the queue to free memory and mark for re-initialization
                    q = getQueue(sccFind(root)); // Re-initialize the queue for this node with the entire list of incoming edges
                    e = extractMinEdge(q); // Extract the minimum edge again after re-initialization
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
                            getQueue(rep).merge(nodeQueue);
                            // Clear the merged queue to free memory
                            clearQueue(nodeQueue, node);
                        }
                    }
                    updateMax(rep, dst);
                    cycleEdgeNodes.set(rep.getId(), edgeNodesInCycle);
                }
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
     * If the node is part of an SCC, loads edges for all nodes in the SCC.
     * Uses preloaded offset cache and EdgeLoader.
     * 
     * @param v The node whose incoming edges to load
     * @throws IOException if file operations fail
     */
    private void initializeQueueForNode(Node v) throws IOException {
        // Find the SCC representative for this node
        Node rep = sccFind(v);
        int repId = rep.getId();
        
        // Determine which nodes' edges need to be loaded
        Set<Integer> nodesToLoad;
        if (sccComposition.containsKey(repId)) {
            // This is an SCC representative with merged queues
            // Load edges for all nodes in this SCC
            nodesToLoad = sccComposition.get(repId);
        } else {
            // This node is not part of any SCC (or is a singleton SCC)
            // Load only its own edges
            nodesToLoad = new HashSet<>();
            nodesToLoad.add(repId);
        }
        
        // DIAGNOSTIC: Log SCC size and memory estimate
        long estimatedEdges = nodesToLoad.size() * 100000L; // Assume 100k edges per node
        long estimatedMemoryMB = (estimatedEdges * 150) / (1024 * 1024); // ~150 bytes per HeapNode
        if (nodesToLoad.size() > 1 || repId % 10000 == 0) {
            System.err.println(String.format("[MEMORY] Initializing queue for node %d (SCC size: %d nodes, ~%d edges, ~%d MB)",
                repId, nodesToLoad.size(), estimatedEdges, estimatedMemoryMB));
            
            // Log current memory state
            Runtime runtime = Runtime.getRuntime();
            long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
            System.err.println(String.format("[MEMORY] Heap usage: %d MB / %d MB (%.1f%%)",
                usedMemoryMB, maxMemoryMB, (usedMemoryMB * 100.0 / maxMemoryMB)));
        }
        
        // Get the queue for the representative (this is the merged queue)
        MergeableHeapInterface<HeapNode> queue = queues.get(repId);
        
        // Track total edges loaded for diagnostic purposes
        final long[] totalEdgesLoaded = {0};
        
        // Load and insert edges for all nodes in the SCC
        for (Integer nodeId : nodesToLoad) {
            if (onDemand) {
                // List<Edge> incomingEdges = new ArrayList<>();
                Node node = nodeMap.get(nodeId);
                if (numNeighbors > 0 && this.numExaminedEdges[nodeId] < numNeighbors && !prevFailure[sccFind(node).getId()]) {
                    // Compute incoming edges on-the-fly using nearest neighbor search

                    // Slack to add to the number of neighbors, in case the number of examined edges
                    // is already close to the limit, to ensure we get a reasonable number of edges
                    // to examine
                    int slack = numNeighbors - this.numExaminedEdges[nodeId];
                    
                    // NNSearch
                    @SuppressWarnings("unchecked")
                    List<Point<Object>> neighbors = ((NearestNeighbourSearchAlgorithm<Object>) nnAlgorithm)
                        .neighbourSearch((Point<Object>) node.getPoint(), numNeighbors + slack);

                    for (Point<?> neighbor : neighbors) {
                        Node otherNode = new Node(neighbor);
                        Edge edge = buildEdge(otherNode, node, distanceFunction);
                        if (edge.getDestination().getId() == nodeId) {
                            // incomingEdges.add(edge);
                            queue.insert(new HeapNode(edge, null, null)); // Insert directly into queue
                            totalEdgesLoaded[0]++;
                        }
                    }
                }
                else {
                    // Complete graph. Compute all distances to other nodes
                    for (Node otherNode : nodeMap.values()) {
                        if (otherNode.getId() != nodeId) {
                            Edge edge = buildEdge(otherNode, node, distanceFunction);
                            if (edge.getDestination().getId() == nodeId) {
                                // incomingEdges.add(edge);
                                queue.insert(new HeapNode(edge, null, null)); // Insert directly into queue
                                totalEdgesLoaded[0]++;
                            }
                        }
                    }
                }
            }
            else {
                // DIAGNOSTIC: Track edge loading per node
                final long[] edgeCount = {0};
                long startTime = System.currentTimeMillis();
                
                GraphMapper.streamIncidentEdges(baseName, nodeId, edge -> {
                    queue.insert(new HeapNode(edge, null, null));
                    edgeCount[0]++;
                    totalEdgesLoaded[0]++;
                });
                
                long elapsed = System.currentTimeMillis() - startTime;
                if (nodesToLoad.size() > 1 || elapsed > 1000) {
                    System.err.println(String.format("[MEMORY] Node %d: loaded %d edges in %d ms",
                        nodeId, edgeCount[0], elapsed));
                }
            }
        }
        
        // DIAGNOSTIC: Log final edge count loaded into queue
        if (nodesToLoad.size() > 1) {
            System.err.println(String.format("[MEMORY] Queue for SCC %d: loaded %d total edges",
                repId, totalEdgesLoaded[0]));
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
        // TODO - este método não me parece que esteja a fazer um caralho
        
        this.baseName = baseName;
        this.useMemoryMappedFiles = true;
        this.queueInitialized = new HashMap<>();
        this.sccComposition = new HashMap<>();
        
        // Save graph to memory-mapped files
        GraphMapper.saveGraph(graph, sequenceLength, baseName);
        
        // Load node map - only load full sequences if onDemand mode requires them
        if (onDemand) {
            this.nodeMap = GraphMapper.loadNodeMap(baseName);
        } else {
            this.nodeMap = GraphMapper.loadNodeIdsOnly(baseName);
        }
        
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
     * Override expansionPhase to add debug output.
     */
    @Override
    protected List<Edge> expansionPhase() {
        // Call parent's expansion
        return super.expansionPhase();
    }
    
    @Override
    public Graph inferPhylogeny(Graph graph) {
        contractionPhase();
        List<Edge> forest = expansionPhase();
        return new Graph(forest);
    }
}
