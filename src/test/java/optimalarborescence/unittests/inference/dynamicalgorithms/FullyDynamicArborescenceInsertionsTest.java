package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Dynamic version of TarjanArborescenceSimpleGraphInsertionsTest.
 * Tests the FullyDynamicArborescence class with multiple edge insertion scenarios.
 */
public class FullyDynamicArborescenceInsertionsTest {

    private static final String ALLELIC_PROFILE = "ACGT";

    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;
    private FullyDynamicArborescence dynamicAlgorithm;

    private List<Edge> initialExpectedEdges;

    @Before
    public void setUp() {
        nodes = new ArrayList<>() {
            {
                add(new Node(ALLELIC_PROFILE, 0));
                add(new Node(ALLELIC_PROFILE, 1));
                add(new Node(ALLELIC_PROFILE, 2));
                add(new Node(ALLELIC_PROFILE, 3));
            }
        };

        edges = new ArrayList<>() {
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

        originalGraph = new Graph(edges);
        
        List<ATreeNode> roots = new ArrayList<>();
        TarjanArborescence tarjan = new TarjanArborescence(originalGraph);
        dynamicAlgorithm = new FullyDynamicArborescence(originalGraph, roots, tarjan);

        initialExpectedEdges = List.of(
            new Edge(nodes.get(2), nodes.get(1), 3),
            new Edge(nodes.get(2), nodes.get(3), 2),
            new Edge(nodes.get(1), nodes.get(0), 2)
        );
    }

    @Test
    public void testInsertOneSuboptimalEdge() {
        Graph firstPhylogeny = dynamicAlgorithm.inferPhylogeny(originalGraph);

        Assert.assertTrue(isValidArborescence(originalGraph, firstPhylogeny));
        int firstCost = firstPhylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        int expectedFirstCost = initialExpectedEdges.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the initial arborescence does not match expected value.",
            expectedFirstCost, firstCost);

        // Insert suboptimal edge using dynamic algorithm
        Edge newEdge = new Edge(nodes.get(1), nodes.get(2), 10);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(newEdge);

        Assert.assertTrue(isValidArborescence(dynamicAlgorithm.getGraph(), 
            new Graph(updatedArborescence)));
        int secondCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost should remain unchanged after inserting a suboptimal edge.",
            firstCost, secondCost);
    }

    @Test
    public void testInsertOneSuboptimalOneOptimalEdge() {
        Graph firstPhylogeny = dynamicAlgorithm.inferPhylogeny(originalGraph);

        Assert.assertTrue(isValidArborescence(originalGraph, firstPhylogeny));

        // Insert suboptimal edge
        Edge suboptimalEdge = new Edge(nodes.get(1), nodes.get(2), 10);
        dynamicAlgorithm.addEdge(suboptimalEdge);
        
        // Insert optimal edge
        Edge optimalEdge = new Edge(nodes.get(2), nodes.get(1), 1);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(optimalEdge);

        List<Edge> expectedEdgesAfterOptimalInsertion = List.of(
            new Edge(nodes.get(3), nodes.get(2), 2),
            new Edge(nodes.get(2), nodes.get(1), 1),
            new Edge(nodes.get(1), nodes.get(0), 2)
        );

        Assert.assertTrue(isValidArborescence(dynamicAlgorithm.getGraph(), 
            new Graph(updatedArborescence)));
        int expectedCost = expectedEdgesAfterOptimalInsertion.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost does not match expected value after optimal edge insertion.",
            expectedCost, resultCost);
    }

    @Test
    public void testInsertOneSuboptimalTwoOptimalEdge() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Insert suboptimal edge
        Edge suboptimalEdge = new Edge(nodes.get(1), nodes.get(2), 10);
        dynamicAlgorithm.addEdge(suboptimalEdge);
        
        // Insert first optimal edge
        Edge optimalEdge1 = new Edge(nodes.get(2), nodes.get(1), 1);
        dynamicAlgorithm.addEdge(optimalEdge1);

        // Insert second optimal edge
        Edge optimalEdge2 = new Edge(nodes.get(2), nodes.get(3), 1);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(optimalEdge2);

        List<Edge> expectedEdgesAfterOptimalInsertion = List.of(
            new Edge(nodes.get(2), nodes.get(3), 1),
            new Edge(nodes.get(2), nodes.get(1), 1),
            new Edge(nodes.get(1), nodes.get(0), 2)
        );

        Assert.assertTrue(isValidArborescence(dynamicAlgorithm.getGraph(), 
            new Graph(updatedArborescence)));
        int expectedCost = expectedEdgesAfterOptimalInsertion.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost does not match expected value after optimal edge insertion.",
            expectedCost, resultCost); 
    }

    @Test
    public void testMultipleSuboptimalInsertions() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        int initialCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();

        // Insert multiple suboptimal edges
        Edge sub1 = new Edge(nodes.get(0), nodes.get(2), 20);
        dynamicAlgorithm.addEdge(sub1);
        
        int costAfter1 = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should remain unchanged after first suboptimal insertion",
            initialCost, costAfter1);

        Edge sub2 = new Edge(nodes.get(1), nodes.get(2), 15);
        dynamicAlgorithm.addEdge(sub2);
        
        int costAfter2 = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should remain unchanged after second suboptimal insertion",
            initialCost, costAfter2);

        Edge sub3 = new Edge(nodes.get(3), nodes.get(1), 25);
        dynamicAlgorithm.addEdge(sub3);
        
        int finalCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should remain unchanged after third suboptimal insertion",
            initialCost, finalCost);
        
        Assert.assertTrue("Final arborescence should be valid",
            isValidArborescence(dynamicAlgorithm.getGraph(), 
                new Graph(dynamicAlgorithm.getCurrentArborescence())));
    }

    @Test
    public void testOptimalInsertionCreatingCycle() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Insert edge that creates a cycle: 1->2 and we already have 2->1
        Edge optimalEdge = new Edge(nodes.get(1), nodes.get(2), 1);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(optimalEdge);
        
        Assert.assertTrue("Arborescence should remain valid after inserting edge that creates cycle",
            isValidArborescence(dynamicAlgorithm.getGraph(), 
                new Graph(updatedArborescence)));
        
        // The algorithm should handle the cycle properly
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Cost should be positive and reasonable",
            resultCost > 0 && resultCost < 100);
    }

    @Test
    public void testInsertionChangingRoot() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Insert very light edge from node that was not root
        Edge lightEdge = new Edge(nodes.get(0), nodes.get(2), 1);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(lightEdge);
        
        Assert.assertTrue("Arborescence should remain valid after potentially changing root",
            isValidArborescence(dynamicAlgorithm.getGraph(), 
                new Graph(updatedArborescence)));
        
        // Check that total cost improved
        int initialCost = initialExpectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Cost should improve or stay same with optimal insertion",
            newCost <= initialCost);
    }

    /*
     * Helper methods
     */
    private boolean isValidArborescence(Graph graph, Graph arborescence) {
        if (arborescence.getNumNodes() != graph.getNumNodes() || 
            arborescence.getNumEdges() != graph.getNumNodes() - 1) {
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

        List<Node> allNodes = new ArrayList<>(graph.getNodes());
        for (Node node : incidentNodes.values()) {
            allNodes.remove(node);
        }
        if (allNodes.size() != 1) {
            return false; // More than one root or missing nodes
        }
        Node root = allNodes.get(0);

        if (!BFS(arborescence, root)) {
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
