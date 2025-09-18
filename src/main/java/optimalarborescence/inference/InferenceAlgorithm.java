package optimalarborescence.inference;

import optimalarborescence.graph.Graph;

public interface InferenceAlgorithm {


    /**
     * Infers a phylogenetic tree from the given graph.
     *
     * @param graph the input graph
     * @return the inferred phylogenetic tree as a graph
     */
    Graph inferPhylogeny(Graph graph);
   
}