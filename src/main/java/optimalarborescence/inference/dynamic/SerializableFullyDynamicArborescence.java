package optimalarborescence.inference.dynamic;

import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.memorymapper.ATreeMapper;
import optimalarborescence.nearestneighbour.NearestNeighbourSearchAlgorithm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A serializable version of FullyDynamicArborescence that works with memory-mapped files.
 *
 * This wrapper overrides the DynamicTarjanArborescence factory method to create
 * disk-based {@link SerializableDynamicTarjanArborescence} instances when a baseName
 * is configured, enabling memory-efficient inference on large graphs.
 *
 * <p><strong>Disk I/O contract:</strong> The caller (e.g., Main.java) is responsible for
 * keeping memory-mapped files in sync with the graph state. Before calling
 * {@code addEdge}/{@code removeEdge}, the caller must update the on-disk edge files
 * via {@link GraphMapper} methods. This is consistent with the convention used by
 * {@link optimalarborescence.inference.SerializableCameriniForest}.</p>
 */
public class SerializableFullyDynamicArborescence extends FullyDynamicArborescence {

    private String baseName;
    private boolean onDemand = false;
    private NearestNeighbourSearchAlgorithm<?> nnAlgorithm = null;
    private int numNeighbors = 0;
    private DistanceFunction distanceFunction = null;
    private boolean symmetric = true;

    /**
     * Constructor with SerializableDynamicTarjanArborescence for file-based operation.
     *
     * @param graph The original graph
     * @param roots The list of ATree root nodes
     * @param camerini An instance of SerializableDynamicTarjanArborescence configured for files
     */
    public SerializableFullyDynamicArborescence(Graph graph,
                                               List<ATreeNode> roots,
                                               SerializableDynamicTarjanArborescence camerini) {
        super(graph, roots, camerini);
    }

    /**
     * Constructor with baseName for file-based operation.
     *
     * @param graph The original graph
     * @param roots The list of ATree root nodes
     * @param camerini An instance of SerializableDynamicTarjanArborescence configured for files
     * @param baseName Base name for memory-mapped files
     */
    public SerializableFullyDynamicArborescence(Graph graph,
                                               List<ATreeNode> roots,
                                               SerializableDynamicTarjanArborescence camerini,
                                               String baseName) {
        super(graph, roots, camerini);
        this.baseName = baseName;
    }

    /**
     * Default constructor for empty initialization.
     */
    public SerializableFullyDynamicArborescence() {
        super();
        this.camerini = new SerializableDynamicTarjanArborescence();
    }

    /**
     * Constructor for loading from memory-mapped files without requiring a Graph instance.
     *
     * This constructor loads the algorithm state entirely from memory-mapped files,
     * including the graph structure, ATree forest, and algorithm state.
     * Edges are loaded on-demand during algorithm execution.
     *
     * @param baseName Base name for memory-mapped files containing the saved state
     * @throws IOException if file operations fail
     */
    public SerializableFullyDynamicArborescence(String baseName) throws IOException {
        this(createMinimalGraph(baseName),
             loadATreeRoots(baseName),
             new SerializableDynamicTarjanArborescence(baseName));
        this.baseName = baseName;
    }

    /**
     * Constructor for loading from memory-mapped files with on-demand edge computation.
     *
     * This constructor enables edges to be computed on-the-fly using a distance function
     * instead of reading pre-stored per-node edge files. When numNeighbors > 0, an
     * approximate nearest-neighbor search is used first, with automatic fallback to full
     * neighbor enumeration if no valid safe edge is found.
     *
     * @param baseName Base name for memory-mapped files containing the saved state
     * @param onDemand If true, edge weights are computed on-the-fly
     * @param nnAlgorithm Nearest-neighbour search algorithm (null disables approximation)
     * @param numNeighbors NN search budget per node (0 means full enumeration)
     * @param distanceFunction Distance function for edge weight computation
     * @param symmetric Whether the distance function is symmetric
     * @throws IOException if file operations fail
     */
    public SerializableFullyDynamicArborescence(String baseName, boolean onDemand,
            NearestNeighbourSearchAlgorithm<?> nnAlgorithm, int numNeighbors,
            DistanceFunction distanceFunction, boolean symmetric) throws IOException {
        this(createMinimalGraph(baseName),
             loadATreeRoots(baseName),
             new SerializableDynamicTarjanArborescence(baseName));
        this.baseName = baseName;
        this.onDemand = onDemand;
        this.nnAlgorithm = nnAlgorithm;
        this.numNeighbors = numNeighbors;
        this.distanceFunction = distanceFunction;
        this.symmetric = symmetric;
    }

    /**
     * Factory override: creates a disk-based SerializableDynamicTarjanArborescence
     * when baseName is set, otherwise falls back to in-memory DynamicTarjanArborescence.
     */
    @Override
    protected DynamicTarjanArborescence createDynamicTarjan(
            List<ATreeNode> aTreeRoots,
            List<Edge> contractedEdges,
            Map<Integer, Integer> reducedCosts,
            Graph originalGraph,
            Comparator<Edge> edgeComparator,
            UnionFind wccUf,
            UnionFindStronglyConnected sccUf,
            List<TarjanForestNode> precomputedInEdgeNode,
            List<TarjanForestNode> precomputedLeaves) {
        if (baseName != null) {
            if (onDemand) {
                try {
                    return new SerializableDynamicTarjanArborescence(
                        aTreeRoots, contractedEdges, reducedCosts, originalGraph,
                        edgeComparator, wccUf, sccUf, precomputedInEdgeNode,
                        precomputedLeaves, baseName, onDemand, nnAlgorithm,
                        numNeighbors, distanceFunction, symmetric);
                } catch (IOException e) {
                    throw new RuntimeException(
                        "Error loading node map for on-demand dynamic arborescence", e);
                }
            }
            return new SerializableDynamicTarjanArborescence(
                aTreeRoots, contractedEdges, reducedCosts, originalGraph,
                edgeComparator, wccUf, sccUf, precomputedInEdgeNode,
                precomputedLeaves, baseName);
        }
        return super.createDynamicTarjan(aTreeRoots, contractedEdges, reducedCosts,
            originalGraph, edgeComparator, wccUf, sccUf,
            precomputedInEdgeNode, precomputedLeaves);
    }

    /**
     * Create a minimal graph with only nodes (no edges) from memory-mapped files.
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
     * Load ATree roots from memory-mapped files.
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
}
