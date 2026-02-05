package optimalarborescence.inference.dynamic;

import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.memorymapper.ATreeMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A simplified serializable version of FullyDynamicArborescence.
 * 
 * This wrapper overrides edge add/remove operations to work with memory-mapped files.
 * When using persisted files, the caller (Main.java) manages the file operations,
 * and this class only updates the internal algorithm state without modifying the graph object.
 */
public class SerializableFullyDynamicArborescence extends FullyDynamicArborescence {
    
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
     * Default constructor for empty initialization.
     */
    public SerializableFullyDynamicArborescence() {
        super();
        this.camerini = new SerializableDynamicTarjanArborescence();
    }
    
    /**
     * Constructor for full lazy loading from memory-mapped files without requiring a Graph instance.
     * 
     * This constructor loads the algorithm state entirely from memory-mapped files,
     * including the graph structure, ATree forest, and algorithm state. This mirrors
     * the behavior of SerializableCameriniForest for the dynamic algorithm case.
     * 
     * Edges and ATree children are loaded on-demand during algorithm execution.
     * 
     * @param baseName Base name for memory-mapped files containing the saved state
     * @throws IOException if file operations fail
     */
    public SerializableFullyDynamicArborescence(String baseName) throws IOException {
        // Create the serializable Camerini instance with lazy loading
        this(createMinimalGraph(baseName), 
             loadATreeRootsLazy(baseName),
             new SerializableDynamicTarjanArborescence(baseName));
    }
    
    /**
     * Create a minimal graph with only nodes (no edges) from memory-mapped files.
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
     * Load ATree roots lazily from memory-mapped files.
     * If ATree files don't exist, returns an empty list (useful for fresh graphs).
     * 
     * @param baseName Base name for memory-mapped files
     * @return List of ATree root nodes
     * @throws IOException if file operations fail
     */
    private static List<ATreeNode> loadATreeRootsLazy(String baseName) throws IOException {
        Map<Integer, Node> graphNodes = GraphMapper.loadNodeMap(baseName);
        
        // Try to load ATrees; if they don't exist, return empty list
        try {
            return ATreeMapper.loadATreeRootsLazy(baseName, graphNodes);
        } catch (java.io.FileNotFoundException e) {
            // ATree files don't exist yet - return empty list for fresh graph
            return new ArrayList<>();
        }
    }
}
