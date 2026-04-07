package optimalarborescence.unittests.inference.dynamicalgorithms;


import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.inference.dynamic.DynamicTarjanArborescence;
import optimalarborescence.unittests.inference.HelperMethods;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;


/**
 * Based on the graph used in the examples of section 2.1.3 of 
 * <p>
 * Espada, J.; Francisco, A.P.; Rocher, T.; Russo, L.M.S.; Vaz, C. On Finding Optimal (Dynamic) Arborescences. 
 * Algorithms 2023, 16, 559. https://doi.org/10.3390/a16120559 
 */
public class FullyDynamicArborescenceLoopedSquaredMotifsTest {
    // Default comparator for edges - min heap based on weight
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());


    private FullyDynamicArborescence algorithm;
    private DynamicTarjanArborescence dynamicTarjan;
    private List<ATreeNode> roots = new ArrayList<>();


    private List<Node> nodes = new ArrayList<>() {
        {
            add(new Node(0));
            add(new Node(1));
            add(new Node(2));
            add(new Node(3));
            add(new Node(4));
            add(new Node(5));
            add(new Node(6));
            add(new Node(7));
        }
    };

    private List<Edge> edges = new ArrayList<>() {
        {
            add(new Edge(nodes.get(0), nodes.get(1), 11));
            add(new Edge(nodes.get(0), nodes.get(2), 5));
            add(new Edge(nodes.get(1), nodes.get(2), 6));
            add(new Edge(nodes.get(2), nodes.get(0), 15));
            add(new Edge(nodes.get(2), nodes.get(4), 3));
            add(new Edge(nodes.get(2), nodes.get(5), 13));
            add(new Edge(nodes.get(3), nodes.get(1), 10));
            add(new Edge(nodes.get(3), nodes.get(2), 2));
            add(new Edge(nodes.get(4), nodes.get(5), 9));
            add(new Edge(nodes.get(4), nodes.get(6), 12));
            add(new Edge(nodes.get(5), nodes.get(3), 7));
            add(new Edge(nodes.get(5), nodes.get(7), 8));
            add(new Edge(nodes.get(6), nodes.get(7), 1));
            add(new Edge(nodes.get(7), nodes.get(4), 4));
        }
    };

    private Graph originalGraph = new Graph(edges);

    private List<Edge> expectedEdges = List.of(
        new Edge(nodes.get(0), nodes.get(2), 5),
        new Edge(nodes.get(2), nodes.get(4), 3),
        new Edge(nodes.get(4), nodes.get(6), 12),
        new Edge(nodes.get(6), nodes.get(7), 1),
        new Edge(nodes.get(4), nodes.get(5), 9),
        new Edge(nodes.get(5), nodes.get(3), 7),
        new Edge(nodes.get(3), nodes.get(1), 10)
    );

    @Test
    public void testFullyDynamicArborescenceLoopedSquaredMotifs() {

        dynamicTarjan = new DynamicTarjanArborescence(roots,
            new ArrayList<>(), // No contracted edges initially
            new java.util.HashMap<>(), // No reduced costs initially
            originalGraph,
            EDGE_COMPARATOR
        );

        // Initialize the fully dynamic arborescence algorithm with the original graph
        algorithm = new FullyDynamicArborescence(originalGraph, roots, dynamicTarjan);

        // Compute the optimal arborescence
        Graph result = algorithm.inferPhylogeny(originalGraph);

        // Extract edges from the resulting arborescence
        List<Edge> resultEdges = result.getEdges();

        // Assert that the resulting edges match the expected edges
        Assert.assertEquals("The number of edges in the optimal arborescence is incorrect.",
                expectedEdges.size(), resultEdges.size());

        // Assert that the resulting arborescence is valid
        Assert.assertTrue("The resulting arborescence is invalid.",
                HelperMethods.isValidArborescence(originalGraph, result));
            
        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = resultEdges.stream().mapToInt(Edge::getWeight).sum();

        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }
}
