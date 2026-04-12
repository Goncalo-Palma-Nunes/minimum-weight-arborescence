package optimalarborescence.inference.dynamic;

import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;
import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.memorymapper.ATreeMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A serializable version of DynamicTarjanArborescence that works with memory-mapped files.
 *
 * When using memory-mapped files, this class replaces the queue-based contraction phase
 * with a disk-based approach: edges are read directly from per-node files on disk for each
 * SCC, eliminating the need to hold all edges in memory. This mirrors the approach used
 * by {@link optimalarborescence.inference.SerializableCameriniForest}.
 */
public class SerializableDynamicTarjanArborescence extends DynamicTarjanArborescence {

    private String baseName;
    private boolean useMemoryMappedFiles;
    private boolean onDemand = false;
    private NearestNeighbourSearchAlgorithm<?> nnAlgorithm = null;
    private DistanceFunction distanceFunction = null;
    private int numNeighbors = 0;
    private boolean symmetric = true;
    private int[] numExaminedEdges;
    private boolean[] prevFailure = null;
    private Map<Integer, Node> nodeMap;

    /**
     * Maps SCC representative ID to the set of all node IDs that have been merged into this SCC.
     * Updated during contractionPhase when cycles are detected and nodes are unified.
     * Used during getMinSafeEdge to load edges for all nodes in the SCC from disk.
     */
    private Map<Integer, Set<Integer>> sccComposition;

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
        this.sccComposition = new HashMap<>();
        initializeArrayFields(originalGraph);
    }

    /**
     * Constructor for memory-mapped file operation.
     *
     * This constructor loads from existing memory-mapped files. Edges are read from disk
     * during the contraction phase rather than being held in memory.
     *
     * @param aTreeRoots The ATree forest roots
     * @param contractedEdges Edges from decomposed contractions
     * @param reducedCosts Map of reduced costs for edges
     * @param originalGraph The original graph structure (can be minimal - just node list)
     * @param baseName Base name for memory-mapped files
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
        this.nodeMap = GraphMapper.loadNodeMap(baseName);
        initializeArrayFields(originalGraph);

        initializeSCCCompositionFromATree(aTreeRoots);
    }

    /**
     * Constructor for disk-based operation via the factory method in FullyDynamicArborescence.
     *
     * @param aTreeRoots The ATree forest roots
     * @param contractedEdges Edges from decomposed contractions (passed through to DTA)
     * @param reducedCosts Reduction quantities for each vertex
     * @param originalGraph The original graph structure (nodes used, edges read from disk)
     * @param edgeComparator Edge comparator
     * @param wccUf Pre-computed WCC union-find (may be null)
     * @param sccUf Pre-computed SCC union-find (may be null)
     * @param precomputedInEdgeNode Pre-computed inEdgeNode list
     * @param precomputedLeaves Pre-computed leaves list
     * @param baseName Base name for memory-mapped files (null for in-memory operation)
     */
    public SerializableDynamicTarjanArborescence(List<ATreeNode> aTreeRoots,
                                     List<Edge> contractedEdges,
                                     Map<Integer, Integer> reducedCosts,
                                     Graph originalGraph,
                                     Comparator<Edge> edgeComparator,
                                     UnionFind wccUf,
                                     UnionFindStronglyConnected sccUf,
                                     List<TarjanForestNode> precomputedInEdgeNode,
                                     List<TarjanForestNode> precomputedLeaves,
                                     String baseName) {
        super(aTreeRoots, contractedEdges, reducedCosts, originalGraph,
              edgeComparator, wccUf, sccUf, precomputedInEdgeNode, precomputedLeaves);

        this.baseName = baseName;
        this.useMemoryMappedFiles = (baseName != null);
        initializeArrayFields(originalGraph);

        if (this.useMemoryMappedFiles) {
            initializeSCCCompositionFromATree(aTreeRoots);
        } else {
            this.sccComposition = new HashMap<>();
        }
    }

    /**
     * Constructor for on-demand edge computation with optional nearest-neighbor approximation.
     *
     * Mirrors the on-demand constructor in SerializableCameriniForest. When onDemand is true,
     * edge weights are computed on-the-fly instead of being read from pre-stored disk files.
     * When numNeighbors > 0, approximate NN search is used first, with automatic fallback
     * to full neighbor enumeration if no valid safe edge is found.
     *
     * @param aTreeRoots The ATree forest roots
     * @param contractedEdges Edges from decomposed contractions
     * @param reducedCosts Reduction quantities for each vertex
     * @param originalGraph The original graph (nodes must carry full sequences when onDemand=true)
     * @param edgeComparator Edge comparator
     * @param wccUf Pre-computed WCC union-find (may be null)
     * @param sccUf Pre-computed SCC union-find (may be null)
     * @param precomputedInEdgeNode Pre-computed inEdgeNode list
     * @param precomputedLeaves Pre-computed leaves list
     * @param baseName Base name for memory-mapped node data (must not be null when onDemand=true)
     * @param onDemand If true, edge weights are computed on-the-fly
     * @param nnAlgorithm Nearest-neighbour search algorithm (null disables approximation)
     * @param numNeighbors NN search budget per node (0 means full enumeration)
     * @param distanceFunction Distance function for edge weight computation
     * @param symmetric Whether the distance function is symmetric
     * @throws IOException if memory-mapped node file loading fails
     */
    public SerializableDynamicTarjanArborescence(List<ATreeNode> aTreeRoots,
                                     List<Edge> contractedEdges,
                                     Map<Integer, Integer> reducedCosts,
                                     Graph originalGraph,
                                     Comparator<Edge> edgeComparator,
                                     UnionFind wccUf,
                                     UnionFindStronglyConnected sccUf,
                                     List<TarjanForestNode> precomputedInEdgeNode,
                                     List<TarjanForestNode> precomputedLeaves,
                                     String baseName,
                                     boolean onDemand,
                                     NearestNeighbourSearchAlgorithm<?> nnAlgorithm,
                                     int numNeighbors,
                                     DistanceFunction distanceFunction,
                                     boolean symmetric) throws IOException {
        super(aTreeRoots, contractedEdges, reducedCosts, originalGraph,
              edgeComparator, wccUf, sccUf, precomputedInEdgeNode, precomputedLeaves);

        this.baseName = baseName;
        this.useMemoryMappedFiles = (baseName != null);
        this.onDemand = onDemand;
        this.nnAlgorithm = nnAlgorithm;
        this.numNeighbors = numNeighbors;
        this.distanceFunction = distanceFunction;
        this.symmetric = symmetric;

        int maxNodeId = originalGraph.getNodes().stream().mapToInt(Node::getId).max().orElse(0);
        this.numExaminedEdges = new int[maxNodeId + 1];
        if (numNeighbors > 0) {
            this.prevFailure = new boolean[maxNodeId + 1];
        }

        if (this.useMemoryMappedFiles) {
            this.nodeMap = onDemand
                ? GraphMapper.loadNodeMap(baseName)
                : GraphMapper.loadNodeIdsOnly(baseName);
            initializeSCCCompositionFromATree(aTreeRoots);
        } else {
            this.sccComposition = new HashMap<>();
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
        this.sccComposition = new HashMap<>();
        this.numExaminedEdges = new int[0];
    }

    /**
     * Constructor for ATree loading with edge lazy-loading.
     *
     * This constructor loads the ATree forest from disk and configures
     * disk-based edge access from memory-mapped files.
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
     * then loads the ATree forest if it exists. Edges are read from disk during contraction.
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
     */
    private static Graph createMinimalGraph(String baseName) throws IOException {
        Map<Integer, Node> nodeMap = GraphMapper.loadNodeMap(baseName);
        Graph graph = new Graph(new ArrayList<>());
        for (Node node : nodeMap.values()) {
            graph.addNode(node);
        }
        return graph;
    }

    /**
     * Helper method to load ATree roots eagerly.
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
     * Initializes sccComposition from the ATree's pre-existing c-node contractions.
     * For each c-node root, records all contracted vertex IDs under the SCC representative.
     */
    private void initializeSCCCompositionFromATree(List<ATreeNode> aTreeRoots) {
        this.sccComposition = new HashMap<>();
        if (aTreeRoots == null) return;

        for (ATreeNode root : aTreeRoots) {
            if (root.isSimpleNode() || root.getEdge() == null) continue;
            if (root.getContractedVertices() == null) continue;

            int rep = ufSCC.find(root.getEdge().getDestination().getId());
            Set<Integer> members = new HashSet<>(root.getContractedVertices().keySet());
            sccComposition.put(rep, members);
        }
    }

    /**
     * Returns true if running in approximate on-demand mode with nearest-neighbor search.
     * Mirrors SerializableCameriniForest.isApproximatedOnDemand().
     */
    private boolean isApproximatedOnDemand() {
        return onDemand && numNeighbors > 0;
    }

    /**
     * Initializes numExaminedEdges and prevFailure arrays sized by max node ID.
     * Must be called after on-demand mode fields are set (numNeighbors in particular).
     * Uses max node ID rather than node count to handle sparse or non-contiguous IDs safely.
     */
    private void initializeArrayFields(Graph graph) {
        int maxNodeId = graph.getNodes().stream().mapToInt(Node::getId).max().orElse(0);
        this.numExaminedEdges = new int[maxNodeId + 1];
        if (this.numNeighbors > 0) {
            this.prevFailure = new boolean[maxNodeId + 1];
        }
    }

    private static Edge buildEdge(Node u, Node v, DistanceFunction distanceFunction) {
        int dist = (int) distanceFunction.calculate(u.getPoint().getSequence(), v.getPoint().getSequence());
        return new Edge(u, v, dist);
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
     * Contraction phase using disk-based edge access.
     * Mirrors SerializableCameriniForest.contractionPhase() but adapted for the
     * dynamic algorithm's adjusted weight semantics (reductions map + virtual deletions).
     */
    private void contractionPhase() {
        while (!roots.isEmpty()) {
            Node root = roots.remove(0);
            Edge e = null;
            try {
                e = getMinSafeEdge(root);
            } catch (IOException ex) {
                throw new RuntimeException(
                    "Error accessing memory-mapped files for node " + root.getId(), ex);
            }

            if (e == null) {
                if (isApproximatedOnDemand() && prevFailure != null && !prevFailure[root.getId()]) {
                    prevFailure[root.getId()] = true;
                    try {
                        e = getMinSafeEdge(root);
                    } catch (IOException retryEx) {
                        throw new RuntimeException(
                            "Error accessing memory-mapped files for node " + root.getId(), retryEx);
                    }
                }
                if (e == null) {
                    rset.add(root);
                    continue;
                }
            }

            Node u = e.getSource();
            Node v = e.getDestination();

            TarjanForestNode minNode = createMinNode(e);
            if (wccFind(u) != wccFind(v)) {
                // No cycle formed
                inEdgeNode.set(root.getId(), minNode);
                wccUnion(u, v);
            } else {
                // Cycle detected — same logic as CameriniForest but with sccComposition tracking

                List<Integer> contractionSet = new ArrayList<>();
                contractionSet.add(sccFind(v).getId());

                List<TarjanForestNode> edgeNodesInCycle = new ArrayList<>();
                edgeNodesInCycle.add(minNode);

                Map<Integer, TarjanForestNode> map = new HashMap<>();
                map.put(sccFind(v).getId(), minNode);

                inEdgeNode.set(root.getId(), null);

                Set<Integer> visited = new HashSet<>();
                for (int i = sccFind(u).getId(); inEdgeNode.get(i) != null;
                     i = sccFind(inEdgeNode.get(i).getEdge().getSource()).getId()) {
                    if (visited.contains(i)) break;
                    visited.add(i);
                    map.put(i, inEdgeNode.get(i));
                    edgeNodesInCycle.add(inEdgeNode.get(i));
                    contractionSet.add(i);
                }

                TarjanForestNode maxWeightTarjanNode = getMaxWeightEdgeInCycle(edgeNodesInCycle);
                Edge maxWeightEdge = maxWeightTarjanNode.getEdge();
                Node dst = sccFind(maxWeightEdge.getDestination());

                int sigma = getAdjustedWeight(maxWeightEdge).intValue();
                updateReducedCosts(contractionSet, sigma, map);

                for (TarjanForestNode n : edgeNodesInCycle) {
                    sccUnion(n.getEdge().getSource(), n.getEdge().getDestination());
                }

                Node rep = sccFind(maxWeightEdge.getDestination());

                // Track SCC composition for disk-based edge loading
                Set<Integer> originalNodeIds = new HashSet<>();
                for (TarjanForestNode n : edgeNodesInCycle) {
                    int srcId = n.getEdge().getSource().getId();
                    int dstId = n.getEdge().getDestination().getId();
                    if (sccComposition.containsKey(srcId)) {
                        originalNodeIds.addAll(sccComposition.get(srcId));
                    } else {
                        originalNodeIds.add(srcId);
                    }
                    if (sccComposition.containsKey(dstId)) {
                        originalNodeIds.addAll(sccComposition.get(dstId));
                    } else {
                        originalNodeIds.add(dstId);
                    }
                }
                int repId = rep.getId();
                sccComposition.put(repId, originalNodeIds);
                for (TarjanForestNode n : edgeNodesInCycle) {
                    int srcId = n.getEdge().getSource().getId();
                    int dstId = n.getEdge().getDestination().getId();
                    if (srcId != repId) sccComposition.remove(srcId);
                    if (dstId != repId) sccComposition.remove(dstId);
                }

                roots.add(0, rep);
                // No queue merging needed — getMinSafeEdge scans all SCC members from disk
                updateMax(rep, dst);
                cycleEdgeNodes.set(rep.getId(), edgeNodesInCycle);
            }
        }
    }

    /**
     * Override to prevent eager initialization when using memory-mapped files.
     */
    @Override
    protected void initializeDataStructures() {
        if (!useMemoryMappedFiles) {
            super.initializeDataStructures();
        }
        // Otherwise do nothing - edges are read from disk during contraction
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        if (!useMemoryMappedFiles) {
            return super.inferPhylogeny(graph);
        }

        // Disk-based: use own contraction phase, parent's expansion phase
        contractionPhase();
        List<Edge> forest = expansionPhase();

        this.augmentTarjanForestToATree();

        return new Graph(forest);
    }

    /**
     * Set the base name for memory-mapped files and enable file-based operation.
     * Saves the graph and ATree forest to the specified location.
     *
     * @param baseName Base name for memory-mapped files
     * @param mlstLength Fixed length for MLST data in bytes
     * @throws IOException if file operations fail
     */
    public void setBaseName(String baseName, int mlstLength) throws IOException {
        this.baseName = baseName;
        this.useMemoryMappedFiles = true;

        // Save state to disk
        Graph modifiedGraph = getModifiedGraph();
        GraphMapper.saveGraph(modifiedGraph, mlstLength, baseName);
        saveATreeForest(baseName);

        // Load node map based on current mode
        this.nodeMap = onDemand ? GraphMapper.loadNodeMap(baseName) : GraphMapper.loadNodeIdsOnly(baseName);

        // Initialize sccComposition from ATree state
        initializeSCCCompositionFromATree(getATreeRoots());
    }

    /**
     * Save the ATree forest to memory-mapped files.
     */
    private void saveATreeForest(String baseName) throws IOException {
        Map<Integer, Node> graphNodes = new HashMap<>();
        for (Node node : getModifiedGraph().getNodes()) {
            graphNodes.put(node.getId(), node);
        }
        ATreeMapper.saveATreeForest(getATreeRoots(), graphNodes, baseName);
    }

    /**
     * Get the base name of the memory-mapped files being used.
     */
    public String getBaseName() {
        return baseName;
    }

    /**
     * Check if this instance is using memory-mapped files.
     */
    public boolean isUsingMemoryMappedFiles() {
        return useMemoryMappedFiles;
    }
}
