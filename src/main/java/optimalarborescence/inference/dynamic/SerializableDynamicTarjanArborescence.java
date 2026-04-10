package optimalarborescence.inference.dynamic;

import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.GraphMapper;
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

        if (this.useMemoryMappedFiles) {
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
     * Finds the minimum weight edge entering the SCC of node v, reading from disk.
     * Skips edges whose source or destination is a virtually removed node.
     * Skips edges whose source is within the same SCC (intra-SCC edges).
     */
    private Edge getMinSafeEdge(Node v) throws IOException {
        int repId = ufSCC.find(v.getId());
        Set<Integer> nodesInSCC = sccComposition.getOrDefault(repId, Set.of(repId));

        Edge minEdge = null;
        int[] minRaw = null;

        for (Integer nodeId : nodesInSCC) {
            // Skip loading edges for virtually removed destination nodes
            if (removedNodes.getOrDefault(nodeId, false)) continue;

            List<Edge> incomingEdges = GraphMapper.getIncomingEdges(baseName, nodeId);
            for (Edge edge : incomingEdges) {
                int srcId = edge.getSource().getId();
                int dstId = edge.getDestination().getId();

                // Skip intra-SCC edges
                if (ufSCC.find(srcId) == ufSCC.find(dstId)) continue;

                // Skip edges from virtually removed sources
                if (removedNodes.getOrDefault(srcId, false)) continue;

                int[] raw = new int[]{ edge.getWeight(), srcId, dstId };
                if (minRaw == null || maxDisjointCmp.compare(raw, minRaw) < 0) {
                    minRaw = raw;
                    minEdge = edge;
                }
            }
        }

        return minEdge;
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
                rset.add(root);
                continue;
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
