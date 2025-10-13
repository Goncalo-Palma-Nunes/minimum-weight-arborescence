package optimalarborescence;

import optimalarborescence.graph.*;
import optimalarborescence.inference.InferenceAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.distance.*;
import optimalarborescence.exception.NotImplementedException;

import java.util.List;
import java.util.ArrayList;

public class TarjanTestSubloops {
    private static final boolean VERBOSE = true;

    /* Graph Parameters */
    private static final List<Node> NODES = new ArrayList<>() {{
        add(new Node("A", 0));
        add(new Node("T", 1));
        add(new Node("C", 2));
        add(new Node("G", 3));
        add(new Node("G", 4)); // Virtual Node
    }};
    private static final int NUM_NODES = NODES.size();
    private static final List<Edge> edges = new ArrayList<>() {{
        add(new Edge(NODES.get(0), NODES.get(1), 2));
        add(new Edge(NODES.get(0), NODES.get(3), 3));
        add(new Edge(NODES.get(1), NODES.get(0), 2));
        add(new Edge(NODES.get(1), NODES.get(3), 10));
        add(new Edge(NODES.get(2), NODES.get(1), 3));
        add(new Edge(NODES.get(2), NODES.get(3), 2));
        add(new Edge(NODES.get(3), NODES.get(2), 2));
        add(new Edge(NODES.get(4), NODES.get(0), 999));
        add(new Edge(NODES.get(4), NODES.get(1), 999));
        add(new Edge(NODES.get(4), NODES.get(2), 999));
        add(new Edge(NODES.get(4), NODES.get(3), 999));
    }};

    public static void main(String[] args) {
        System.out.println("##################### Tarjan Test ############################");
        // Implement Tarjan's algorithm test cases here
        Graph graph = createGraph();
        TarjanArborescence tarjan = new TarjanArborescence(graph);

        throw new NotImplementedException("Isto ainda é só uma cópia do DynamicInsert test");

        // System.out.println(graph);
        // System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        // System.out.println(tarjan.inferPhylogeny(graph));

        // System.out.println("##################### End of Tarjan Test #####################");
    }

    public static Graph createGraph() {
        return new Graph(edges);
    }
}
