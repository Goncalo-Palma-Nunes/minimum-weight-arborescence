package optimalarborescence.inference;

import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.graph.Graph;

public abstract class StaticAlgorithm implements InferenceAlgorithm {

    protected Graph graph;

    public StaticAlgorithm(Graph graph) {
        this.graph = graph;
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        throw new NotImplementedException("The abstract class StaticAlgorithm leaves the implementation of the inferPhylogeny method to its subclasses.");
    }
}
