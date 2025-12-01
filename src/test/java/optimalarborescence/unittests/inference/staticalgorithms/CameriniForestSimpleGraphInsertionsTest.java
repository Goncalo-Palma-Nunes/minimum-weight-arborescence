package optimalarborescence.unittests.inference.staticalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.CameriniForest;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for CameriniForest algorithm testing edge insertion scenarios.
 */
public class CameriniForestSimpleGraphInsertionsTest {

    // Default comparator for edges - min heap based on weight
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());

    private static final String ALLELIC_PROFILE = "ACGT";

    private List<Node> nodes = new ArrayList<>() {
        {
            add(new Node(ALLELIC_PROFILE, 0));
            add(new Node(ALLELIC_PROFILE, 1));
            add(new Node(ALLELIC_PROFILE, 2));
            add(new Node(ALLELIC_PROFILE, 3));
        }
    };

    private List<Edge> edges = new ArrayList<>() {
        {
            add(new Edge(nodes.get(0), nodes.get(1), 2)); // 0
            add(new Edge(nodes.get(0), nodes.get(3), 3)); // 1
            add(new Edge(nodes.get(1), nodes.get(0), 2)); // 2
            add(new Edge(nodes.get(1), nodes.get(3), 10)); // 3
            add(new Edge(nodes.get(2), nodes.get(1), 3)); // 4
            add(new Edge(nodes.get(2), nodes.get(3), 2)); // 5
            add(new Edge(nodes.get(3), nodes.get(2), 2)); // 6
        }
    };

    private Graph originalGraph = new Graph(edges);

    private List<Edge> initialExpectedEdges = List.of(
        new Edge(nodes.get(2), nodes.get(1), 3),
        new Edge(nodes.get(2), nodes.get(3), 2),
        new Edge(nodes.get(1), nodes.get(0), 2)
    );

    @Test
    public void testInsertOneSuboptimalEdge() {

        Graph firstPhylogeny = new CameriniForest(originalGraph, EDGE_COMPARATOR).inferPhylogeny(originalGraph);

        System.out.println("First phylogeny edges:");
        for (Edge e : firstPhylogeny.getEdges()) {
            System.out.println("  " + e);
        }
        System.out.println("Nodes: " + firstPhylogeny.getNumNodes() + ", Edges: " + firstPhylogeny.getNumEdges());
        Assert.assertTrue(isValidArborescence(originalGraph, firstPhylogeny));
        int firstCost = firstPhylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        int expectedFirstCost = initialExpectedEdges.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedFirstCost, firstCost);

        // Insert suboptimal edge
        Edge newEdge = new Edge(nodes.get(1), nodes.get(2), 10);
        Graph modifiedGraph = new Graph(originalGraph.getEdges());
        modifiedGraph.addEdge(newEdge);

        Graph secondPhylogeny = new CameriniForest(modifiedGraph, EDGE_COMPARATOR).inferPhylogeny(modifiedGraph);

        Assert.assertTrue(isValidArborescence(modifiedGraph, secondPhylogeny));
        int secondCost = secondPhylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence should remain unchanged after inserting a suboptimal edge.",
            firstCost, secondCost);
    }

    @Test
    public void testInsertOneSuboptimalOneOptimalEdge() {

        Graph firstPhylogeny = new CameriniForest(originalGraph, EDGE_COMPARATOR).inferPhylogeny(originalGraph);

        Assert.assertTrue(isValidArborescence(originalGraph, firstPhylogeny));

        // Insert suboptimal edge
        Edge suboptimalEdge = new Edge(nodes.get(1), nodes.get(2), 10);
        // Insert optimal edge
        Edge optimalEdge = new Edge(nodes.get(2), nodes.get(1), 1);
        Graph modifiedGraph = new Graph(originalGraph.getEdges());
        modifiedGraph.addEdge(suboptimalEdge);
        modifiedGraph.addEdge(optimalEdge);

        Graph secondPhylogeny = new CameriniForest(modifiedGraph, EDGE_COMPARATOR).inferPhylogeny(modifiedGraph);

        List<Edge> expectedEdgesAfterOptimalInsertion = List.of(
            new Edge(nodes.get(3), nodes.get(2), 2),
            new Edge(nodes.get(2), nodes.get(1), 1),
            new Edge(nodes.get(1), nodes.get(0), 2)
        );

        Assert.assertTrue(isValidArborescence(modifiedGraph, secondPhylogeny));
        int expectedCost = expectedEdgesAfterOptimalInsertion.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = secondPhylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence does not match expected value after optimal edge insertion.",
            expectedCost, resultCost);
    }

    @Test
    public void testInsertOneSuboptimalTwoOptimalEdge() {
        // Insert suboptimal edge
        Edge suboptimalEdge = new Edge(nodes.get(1), nodes.get(2), 10);
        // Insert optimal edge
        Edge optimalEdge = new Edge(nodes.get(2), nodes.get(1), 1);
        Graph modifiedGraph = new Graph(originalGraph.getEdges());
        modifiedGraph.addEdge(suboptimalEdge);
        modifiedGraph.addEdge(optimalEdge);

        optimalEdge = new Edge(nodes.get(2), nodes.get(3), 1);
        modifiedGraph.addEdge(optimalEdge);

        List<Edge> expectedEdgesAfterOptimalInsertion = List.of(
            new Edge(nodes.get(2), nodes.get(3), 1),
            new Edge(nodes.get(2), nodes.get(1), 1),
            new Edge(nodes.get(1), nodes.get(0), 2)
        );

        Graph finalPhylogeny = new CameriniForest(modifiedGraph, EDGE_COMPARATOR).inferPhylogeny(modifiedGraph);
        Assert.assertTrue(isValidArborescence(modifiedGraph, finalPhylogeny));
        int expectedCost = expectedEdgesAfterOptimalInsertion.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = finalPhylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence does not match expected value after optimal edge insertion.",
            expectedCost, resultCost); 
    }

    @Test
    public void testInsertionChangingRoot() {
        Edge lightEdge = new Edge(nodes.get(0), nodes.get(2), 1);

        // filter out edges irrelevant to the test
        List<Edge> filteredEdgeList = originalGraph.getEdges().stream()
            .filter(e -> !e.equals(edges.get(3)) && !e.equals(edges.get(6)))
            .toList();

        Graph modifiedGraph = new Graph(filteredEdgeList);
        modifiedGraph.addEdge(lightEdge);

        List<Edge> expectedEdgesAfterOptimalInsertion = List.of(
            new Edge(nodes.get(0), nodes.get(1), 2),
            new Edge(nodes.get(0), nodes.get(2), 1),
            new Edge(nodes.get(2), nodes.get(3), 2)
        );

        Graph finalPhylogeny = new CameriniForest(modifiedGraph, EDGE_COMPARATOR).inferPhylogeny(modifiedGraph);
        Assert.assertTrue(isValidArborescence(modifiedGraph, finalPhylogeny));
        int expectedCost = expectedEdgesAfterOptimalInsertion.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = finalPhylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence does not match expected value after optimal edge insertion.",
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
