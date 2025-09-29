package optimalarborescence;

import optimalarborescence.graph.*;
import optimalarborescence.inference.InferenceAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.distance.*;

import java.util.List;
import java.util.ArrayList;

public class TarjanTest {
    private static final boolean VERBOSE = true;


    // private static InferenceAlgorithm tarjan = new TarjanArborescence();

    /* Graph Parameters */
    private static final int MAX_NUM_NEIGHBOURS = 5;
    private static final List<String> ALLELIC_PROFILES = new ArrayList<>() {{
        add("ATGC");
        add("ATGA");
        add("ATTT");
        // add("ATGT");
        // add("ATGG");
        // add("ACTC");
        // add("TGGC");
        // add("TTTT");
        // add("CCGG");
    }};
    private static final int NUM_NODES = ALLELIC_PROFILES.size();

    /* Nearest Neighbour Search parameters */
    private static final int NUM_HASH_TABLES = 4;
    private static final int WIDTH_CONCATENATED_HASHES = 2;
    private static final int MIN_HASH_INDEX = 0;
    private static final int MAX_HASH_INDEX = 3;
    private static final int SEARCH_RADIUS = 3;
    // private static NearestNeighbourSearchAlgorithm nnSearch = new KDTree(new HammingDistance());
    private static NearestNeighbourSearchAlgorithm nnSearch = new LSH(
                        WIDTH_CONCATENATED_HASHES, NUM_HASH_TABLES, 
                        MIN_HASH_INDEX, MAX_HASH_INDEX,
                        new HammingDistance(), SEARCH_RADIUS);

    public static void main(String[] args) {
        System.out.println("##################### Tarjan Test ############################");
        // Implement Tarjan's algorithm test cases here
        Graph graph = createGraph();
        TarjanArborescence tarjan = new TarjanArborescence(graph);

        System.out.println(graph);
        System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        System.out.println(tarjan.inferPhylogeny(graph));

        System.out.println("##################### End of Tarjan Test #####################");
    }

    public static Graph createGraph() {
        Graph graph = new DirectedGraph(nnSearch, MAX_NUM_NEIGHBOURS);

        List<Node> nodes = createNodes();
        for (Node node : nodes) {
            graph.addNode(node);
        }

        return graph;
    }

    private static List<Node> createNodes() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < NUM_NODES; i++) {
            nodes.add(new Node(ALLELIC_PROFILES.get(i), i));
        }
        return nodes;
    }
}
