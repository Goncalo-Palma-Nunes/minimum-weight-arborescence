package optimalarborescence.unittests.inference.staticalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.TarjanArborescence;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Comparator;

import org.junit.Assert;
import org.junit.Test;

/**
 * Based on the graph used in the examples of section 2.2.4 of 
 * <p>
 * Espada, J.; Francisco, A.P.; Rocher, T.; Russo, L.M.S.; Vaz, C. On Finding Optimal (Dynamic) Arborescences. 
 * Algorithms 2023, 16, 559. https://doi.org/10.3390/a16120559 
 */
public class TarjanArborescenceSimpleGraphTest {
    // Default comparator for edges - min heap based on weight
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());



    private List<Node> nodes = new ArrayList<>() {
        {
            add(new Node(0));
            add(new Node(1));
            add(new Node(2));
            add(new Node(3));
        }
    };

    private List<Edge> edges = new ArrayList<>() {
        {
            add(new Edge(nodes.get(0), nodes.get(1), 6));
            add(new Edge(nodes.get(1), nodes.get(2), 10));
            add(new Edge(nodes.get(1), nodes.get(3), 12));
            add(new Edge(nodes.get(2), nodes.get(1), 10));
            add(new Edge(nodes.get(3), nodes.get(0), 1));
            add(new Edge(nodes.get(3), nodes.get(2), 8));
        }
    };

    private Graph originalGraph = new Graph(edges);

    @Test
    public void testTarjanArborescenceSimpleGraph() {
        TarjanArborescence tarjan = new TarjanArborescence(originalGraph, EDGE_COMPARATOR);
        Graph result = tarjan.inferPhylogeny(originalGraph);

        Assert.assertNotNull(result);
        Assert.assertEquals(originalGraph.getNumNodes(), result.getNumNodes());
        Assert.assertEquals(originalGraph.getNumNodes() - 1, result.getNumEdges());

        for (Edge edge : result.getEdges()) {
            if (!originalGraph.getEdges().contains(edge)) {
                Assert.fail("Result contains an edge not in the original graph: " + edge);
            }
        }

        List<Edge> expectedEdges = List.of(
            new Edge(nodes.get(3), nodes.get(0), 1),
            new Edge(nodes.get(0), nodes.get(1), 6),
            new Edge(nodes.get(3), nodes.get(2), 8)
        );

        for (Edge expectedEdge : expectedEdges) {
            Assert.assertTrue("Expected edge not found in result: " + expectedEdge,
                result.getEdges().stream().anyMatch(e ->
                    e.equals(expectedEdge)
                )
            );
        }

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = result.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }
}