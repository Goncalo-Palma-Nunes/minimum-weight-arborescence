package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.OnlineAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.graph.Graph;
import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.graph.Edge;

import java.util.LinkedList;
import java.util.List;

public class FullyDynamicArborescence extends OnlineAlgorithm {

    /* Note: A Digraph is another term for a directed graph */

    List<ATreeNode> roots;
    TarjanArborescence tarjan;
    List<Edge> currentArborescence; // TODO - formar eficiente de indexar as arestas para não percorrer linearmente durante o DELETE

    public FullyDynamicArborescence(Graph graph, List<ATreeNode> roots, TarjanArborescence tarjan) {
        super(graph);
        this.roots = roots;
        this.tarjan = tarjan;
    }

    public List<ATreeNode> getRoots() {
        return roots;
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

    private List<ATreeNode> decompose(Edge e) {
        ATreeNode N = null;
        for (ATreeNode root : this.getRoots()) {
            N = root.findATreeNodeByEdge(e, root);
            if (N != null) break;
        }

        List<ATreeNode> removedContractions = new LinkedList<>();
        while (N != null && !N.isRoot()) {

            if (!N.isSimpleNode()) {
                List<ATreeNode> children = N.getChildren();
                for (ATreeNode child : children) {
                    child.setParent(null); // Child becomes a root of its own ATree
                    this.roots.add(child);
                }
                children.clear();
                removedContractions.add(N);
            }
            N.getParent().getChildren().remove(N);
            ATreeNode parentN = N.getParent();
            N.setParent(null);
            N = parentN;
        }
        return removedContractions;
    }

    public void rebuildContractedDigraph() {
        throw new NotImplementedException("The rebuildContractedDigraph method is not implemented.");
    }

    @Override
    public List<Edge> removeEdge(Edge edge) {
        getGraph().removeEdge(edge);
        if (!this.getCurrentArborescence().contains(edge)) {

            ATreeNode contraction = null;
            for (ATreeNode root : this.getRoots()) {
                contraction = root.findContractionByEdge(edge, root);
                if (contraction != null) break;
            }

            if (contraction != null) {
                // Handle the contraction
                contraction.getContractedEdges().remove(edge);
            }
        }
        else {
            List<ATreeNode> V = new LinkedList<>(this.getRoots()); // V' in Joaquim's thesis
            List<ATreeNode> removedContractions = decompose(edge); // set R in Joaquim's thesis
            List<Edge> edges = removedContractions.stream()
                    .flatMap(c -> c.getContractedEdges().stream())
                    .toList(); // Union E' of all contracted edges from decomposed non-simple nodes

            

            // TODO - initializing Edmonds’ algorithm w.r.t. the remainders of the ATree and execute i
            // ver artigo Pollatos et al. e tese Joaquim
        }
        


        return this.getCurrentArborescence();
    }

    @Override
    public List<Edge> addEdge(Edge edge) {
        throw new NotImplementedException("The addEdge method is not implemented.");
    }
    
}
