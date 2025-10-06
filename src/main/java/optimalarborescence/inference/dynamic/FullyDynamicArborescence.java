package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.OnlineAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.graph.Graph;
import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.graph.Edge;

import java.util.List;

public class FullyDynamicArborescence extends OnlineAlgorithm {

    ATreeNode aTreeRoot;
    TarjanArborescence tarjan;
    List<Edge> currentArborescence; // TODO - formar eficiente de indexar as arestas para não percorrer linearmente durante o DELETE

    public FullyDynamicArborescence(Graph graph, ATreeNode aTreeRoot, TarjanArborescence tarjan) {
        super(graph);
        this.aTreeRoot = aTreeRoot;
        this.tarjan = tarjan;
    }

    public ATreeNode getATreeRoot() {
        return aTreeRoot;
    }

    private TarjanArborescence getInferenceAlgorithm() {
        return tarjan;
    }

    public List<Edge> getCurrentArborescence() {
        return currentArborescence;
    }

    protected Graph getGraph() {
        return graph;
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        Graph g = this.getInferenceAlgorithm().inferPhylogeny(graph);
        this.currentArborescence = g.getEdges();
        return g;
    }

    @Override
    public List<Edge> updateEdge(Edge edge) {
        throw new NotImplementedException("The updateEdge method is not implemented.");
    }

    private void decompose(Edge e) {
        ATreeNode N = getATreeRoot().findATreeNodeByEdge(e, getATreeRoot());

        throw new NotImplementedException("The decompose method is not implemented.");
    }

    @Override
    public List<Edge> removeEdge(Edge edge) {
        getGraph().removeEdge(edge);
        if (!this.getCurrentArborescence().contains(edge)) {

            ATreeNode contraction = getATreeRoot().findContractionByEdge(edge, getATreeRoot());
            if (contraction != null) {
                // Handle the contraction
                contraction.getContractedEdges().remove(edge);
            }
        }
        else {
            decompose(edge);
        }
        


        return this.getCurrentArborescence();
    }


    }

    @Override
    public List<Edge> addEdge(Edge edge) {
        throw new NotImplementedException("The addEdge method is not implemented.");
    }
    
}
