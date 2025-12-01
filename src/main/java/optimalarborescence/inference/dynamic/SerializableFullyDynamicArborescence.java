package optimalarborescence.inference.dynamic;

import optimalarborescence.graph.Graph;

import java.util.List;

/**
 * A simplified serializable version of FullyDynamicArborescence.
 * 
 * This is a thin wrapper that ensures SerializableDynamicTarjanArborescence
 * is used for lazy edge loading from memory-mapped files.
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
}
