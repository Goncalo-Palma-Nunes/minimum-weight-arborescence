package optimalarborescence.inference;

import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.graph.Graph;

public class TarjanArborescence extends StaticAlgorithm {

    public TarjanArborescence(Graph graph) {
        super(graph);
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        throw new NotImplementedException("Tarjan's algorithm is not yet implemented.");
    }
}
