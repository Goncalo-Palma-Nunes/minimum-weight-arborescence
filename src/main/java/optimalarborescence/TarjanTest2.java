package optimalarborescence;

import optimalarborescence.graph.*;
import optimalarborescence.inference.InferenceAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.distance.*;

import java.util.List;
import java.util.ArrayList;

public class TarjanTest2 {
    private static final boolean VERBOSE = true;

    // Note: Test taken from Joaquim Espada's thesis

    /* Graph Parameters */
    private static final List<Node> NODES = new ArrayList<>() {{
        add(new Node("A", 0));
        add(new Node("T", 1));
        add(new Node("C", 2));
        add(new Node("G", 3));
    }};
    private static final int NUM_NODES = NODES.size();
    private static final List<Edge> edges = new ArrayList<>() {{
        add(new Edge(NODES.get(0), NODES.get(1), 6));
        add(new Edge(NODES.get(1), NODES.get(2), 10));
        add(new Edge(NODES.get(1), NODES.get(3), 12));
        add(new Edge(NODES.get(3), NODES.get(0), 1));
        add(new Edge(NODES.get(3), NODES.get(2), 8));
        add(new Edge(NODES.get(2), NODES.get(1), 10));
    }};

    public static void main(String[] args) {
        System.out.println("##################### Tarjan Test ############################");
        // Implement Tarjan's algorithm test cases here
        Graph graph = createGraph();
        TarjanArborescence tarjan = new TarjanArborescence(graph);

        // System.out.println(graph);
        // System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        System.out.println(tarjan.inferPhylogeny(graph));

        System.out.println("##################### End of Tarjan Test #####################");
    }

    public static Graph createGraph() {
        return new Graph(edges);
    }
}
