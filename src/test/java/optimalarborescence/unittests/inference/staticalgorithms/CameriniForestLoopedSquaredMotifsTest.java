package optimalarborescence.unittests.inference.staticalgorithms;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.CameriniForest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

/**
 * Based on the graph used in the examples of section 2.1.3 of 
 * <p>
 * Espada, J.; Francisco, A.P.; Rocher, T.; Russo, L.M.S.; Vaz, C. On Finding Optimal (Dynamic) Arborescences. 
 * Algorithms 2023, 16, 559. https://doi.org/10.3390/a16120559 
 */
public class CameriniForestLoopedSquaredMotifsTest {

    private static final String ALLELIC_PROFILE = "ACGT";

    private List<Node> nodes = new ArrayList<>() {
        {
            add(new Node(ALLELIC_PROFILE, 0));
            add(new Node(ALLELIC_PROFILE, 1));
            add(new Node(ALLELIC_PROFILE, 2));
            add(new Node(ALLELIC_PROFILE, 3));
            add(new Node(ALLELIC_PROFILE, 4));
            add(new Node(ALLELIC_PROFILE, 5));
            add(new Node(ALLELIC_PROFILE, 6));
            add(new Node(ALLELIC_PROFILE, 7));
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
    public void testCameriniForestLoopedSquaredMotifs() {
        CameriniForest camerini = new CameriniForest(originalGraph);
        Graph result = camerini.inferPhylogeny(originalGraph);

        System.out.println("################### Result ###################");
        System.out.println(result);
        System.out.println("Cost of result: " + result.getEdges().stream().mapToInt(Edge::getWeight).sum());
        System.out.println("################### Expected ###################");
        System.out.println(new Graph(expectedEdges));
        System.out.println("Cost of expected: " + new Graph(expectedEdges).getEdges().stream().mapToInt(Edge::getWeight).sum());

        Assert.assertNotNull(result);
        Assert.assertEquals(expectedEdges.size(), result.getEdges().size());
        Assert.assertTrue(isValidArborescence(originalGraph, result));

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = result.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }


    /*
     * Helper methods
     */
    private boolean isValidArborescence(Graph graph, Graph arborescence) {
        if (arborescence.getNumNodes() != graph.getNumNodes() || arborescence.getNumEdges() != graph.getNumNodes() - 1) {
            return false;
        }

        Map<Integer, Node> incidentNodes = new HashMap<>();
        for (Edge edge : arborescence.getEdges()) {
            if (!graph.getEdges().contains(edge)) {
                return false;
            }
            Node dest = edge.getDestination();
            if (incidentNodes.containsKey(dest.getId())) {
                return false; // More than one incoming edge to the same node
            }
            incidentNodes.put(dest.getId(), dest);
        }

        List<Node> allNodes = graph.getNodes();
        for (Node node : incidentNodes.values()) {
            allNodes.remove(node);
        }
        if (allNodes.size() != 1) {
            return false; // More than one root or missing nodes
        }
        Node root = allNodes.get(0);

        if (!BFS(arborescence, root)) { // It is not a spanning tree
            return false; // Not all nodes are reachable from the root
        }

        return true;
    }

    private boolean BFS(Graph graph, Node start) {
        List<Node> visited = new ArrayList<>();
        List<Node> queue = new ArrayList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.remove(0);
            visited.add(current);

            for (Edge edge : graph.getEdges()) {
                if (edge.getSource().equals(current)) {
                    Node neighbor = edge.getDestination();
                    if (!visited.contains(neighbor) && !queue.contains(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return visited.size() == graph.getNumNodes();
    }
}
