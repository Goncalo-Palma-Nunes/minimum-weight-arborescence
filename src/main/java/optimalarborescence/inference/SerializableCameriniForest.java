package optimalarborescence.inference;

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
        this.sccComposition = new HashMap<>();
        this.numExaminedEdges = new int[graph.getNodes().size()];
        this.prevFailure = new boolean[graph.getNodes().size()];
        
        this.nodeMap = GraphMapper.loadNodeIdsOnly(baseName);
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
                Edge e = null;
                try { e = getMinSafeEdge(root); }
                catch (IOException ex) { throw new RuntimeException("Error accessing memory-mapped files for node " + root.getId(), ex); }

                if (e == null) { // (Equivalent to an "empty queue")

                    if (isApproximatedOnDemand() && !this.prevFailure[root.getId()]) {

                        this.prevFailure[root.getId()] = true; // Mark this node as having failed to find an edge in this iteration
                        try { e = getMinSafeEdge(root); } // Extract the minimum edge considering all neighbors
                        catch (IOException ex) { throw new RuntimeException("Error accessing memory-mapped files for node " + root.getId(), ex); }
                    }
                    if (e == null) { // if it still found nothing or was not running the approximated algorithm
                        rset.add(root);
                        continue;
                    }

                    // All edges have been examined and no valid edge was found, re-add root to rset
                    rset.add(root);
                    continue;
                }

                Node u = e.getSource();
                Node v = e.getDestination();

                TarjanForestNode minNode = createMinNode(e);
                if (wccFind(u) != wccFind(v)) {
                    // no cycle formed
                    inEdgeNode.set(root.getId(), minNode);
                    wccUnion(u, v);
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
                    updateMax(rep, dst);
                    cycleEdgeNodes.set(rep.getId(), edgeNodesInCycle);
                }
        }
        
    }

    private Edge getMinSafeEdgeOnDemand(Set<Integer> nodesInSCC) {
        Edge minEdge = null;
        for (Integer nodeId : nodesInSCC) {

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
                    if (sccFind(otherNode) == sccFind(node)) {
                        continue; // Skip edges within the same SCC
                    }

                    Edge edge = buildEdge(otherNode, node, distanceFunction);
                    
                    // If the new edge's adjusted weight is smaller than the current minimum, update the minimum edge
                    if (minEdge == null || maxDisjointCmp.compare(new int[]{ edge.getWeight(), edge.getSource().getId(), edge.getDestination().getId() },
                                            new int[]{ minEdge.getWeight(), minEdge.getSource().getId(), minEdge.getDestination().getId() }) < 0) {
                        minEdge = edge;
                    }
                }
            }
            else {
                // Complete graph. Compute all distances to other nodes
                for (Node otherNode : nodeMap.values()) {
                    if (otherNode.getId() != nodeId && sccFind(otherNode) != sccFind(node)) {
                        Edge edge = buildEdge(otherNode, node, distanceFunction);
                        if (minEdge == null || maxDisjointCmp.compare(new int[]{ edge.getWeight(), edge.getSource().getId(), edge.getDestination().getId() },
                                            new int[]{ minEdge.getWeight(), minEdge.getSource().getId(), minEdge.getDestination().getId() }) < 0) {
                            minEdge = edge;
                        }
                    }
                }
            }
        }
        return minEdge;
    }


    private Edge getMinSafeEdge(Node v) throws IOException {
        Set<Integer> nodesInSCC = sccComposition.getOrDefault(sccFind(v).getId(), Set.of(sccFind(v).getId()));
        if (onDemand) {
            return getMinSafeEdgeOnDemand(nodesInSCC);
        }
        
        return GraphMapper.findMinSafeEdgeIncomingToSCC(baseName, ufSCC, nodesInSCC, maxDisjointCmp);    
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
        this.sccComposition = new HashMap<>();
        
        // Save graph to memory-mapped files
        GraphMapper.saveGraph(graph, sequenceLength, baseName);
        
        // Load node map - only load full sequences if onDemand mode requires them
        if (onDemand) {
            this.nodeMap = GraphMapper.loadNodeMap(baseName);
        } else {
            this.nodeMap = GraphMapper.loadNodeIdsOnly(baseName);
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
        if (!useMemoryMappedFiles) {
            return super.inferPhylogeny(graph);
        }
        contractionPhase();
        List<Edge> forest = expansionPhase();
        return new Graph(forest);
    }
}
