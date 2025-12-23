package optimalarborescence.unittests.inference.staticalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.CameriniForest;

import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

/**
 * Based on the graph used in the examples of section 4.9.2 of 
 * <p>
 * Joaquim Espada's master thesis: "Large scale phylogenetic inference from noisy data based
 * on minimum weight spanning arborescences"
 */
public class CameriniForestDirectedStronglyConnectedGraphTest {
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
            add(new Edge(nodes.get(0), nodes.get(1), 2));
            add(new Edge(nodes.get(0), nodes.get(3), 3));
            add(new Edge(nodes.get(1), nodes.get(0), 2));
            add(new Edge(nodes.get(1), nodes.get(3), 10));
            add(new Edge(nodes.get(2), nodes.get(1), 3));
            add(new Edge(nodes.get(2), nodes.get(3), 2));
            add(new Edge(nodes.get(3), nodes.get(2), 2));
        }
    };

    private Graph originalGraph = new Graph(edges);

    private List<Edge> expectedEdges = List.of(
        new Edge(nodes.get(1), nodes.get(0), 2),
        new Edge(nodes.get(0), nodes.get(3), 3),
        new Edge(nodes.get(3), nodes.get(2), 2)
    );


    @Test
    public void testCameriniForestDirectedStronglyConnectedGraph() {
        CameriniForest camerini = new CameriniForest(originalGraph, EDGE_COMPARATOR);
        Graph result = camerini.inferPhylogeny(originalGraph);

        // System.out.println("################### Result ###################");
        // System.out.println(result);
        // System.out.println("Cost of result: " + result.getEdges().stream().mapToInt(Edge::getWeight).sum());
        // System.out.println("################### Expected ###################");
        // System.out.println(new Graph(expectedEdges));
        // System.out.println("Cost of expected: " + new Graph(expectedEdges).getEdges().stream().mapToInt(Edge::getWeight).sum());

        System.out.println("Result = " + result.getEdges());
        System.out.println("####################################################################");
        System.out.println("Expected = " + expectedEdges);


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
