package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.graph.Graph;
import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.ArrayList;
import java.util.List;

public class DynamicTarjanArborescence extends TarjanArborescence {

    private List<ATreeNode> contractedEdges;

    public DynamicTarjanArborescence(List<ATreeNode> ATreeRoots, List<Edge> removedContractionEdges, Graph graph) {
        super(graph);
        this.contractedEdges = new ArrayList<>();



        // List<Edge> edges = this.graph.getEdges();
        // for (Edge e: edges) {
        //     getQueue(e.getDestination()).insert(new HeapNode(e, null, null));
        // }
    }


    private void assignReduceCosts(List<ATreeNode> ATreeRoots, List<Edge> removedContractionEdges) {
        for (ATreeNode N : ATreeRoots) {
            int id = N.getId();
            Node v_i = this.graph.getNodes().get(id);

            // obtain incident edges from removedContractionEdges to N
            List<Edge> incidentEdges = new ArrayList<>();
            for (Edge e : removedContractionEdges) {
                if (e.getDestination().equals(v_i)) {
                    incidentEdges.add(e);
                }
            }
        }

    }
    
}
