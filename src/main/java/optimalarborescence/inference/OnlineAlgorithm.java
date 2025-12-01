package optimalarborescence.inference;

import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;

import java.util.List;

public abstract class OnlineAlgorithm extends StaticAlgorithm {

    public OnlineAlgorithm(Graph graph) {
        super(graph);
    }

    public OnlineAlgorithm() {
        super();
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        throw new NotImplementedException("The abstract class StaticAlgorithm leaves the implementation of the inferPhylogeny method to its subclasses.");
    }

    public abstract List<Edge> updateEdge(Edge edge);

    public abstract List<Edge> removeEdge(Edge edge);

    public abstract List<Edge> addEdge(Edge edge);
}
