package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.inference.dynamic.DynamicTarjanArborescence;

import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Dynamic version of TarjanArborescenceSimpleGraphDeletionTest.
 * Tests the FullyDynamicArborescence class with edge deletion scenarios.
 * 
 * Based on the graph used in section 2.2.4 of:
 * Espada, J.; Francisco, A.P.; Rocher, T.; Russo, L.M.S.; Vaz, C. On Finding Optimal (Dynamic) Arborescences. 
 * Algorithms 2023, 16, 559. https://doi.org/10.3390/a16120559 
 */
public class FullyDynamicArborescenceDeletionTest {
    // Default comparator for edges - min heap based on weight
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());



    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;
    private FullyDynamicArborescence dynamicAlgorithm;
    private DynamicTarjanArborescence dynamicTarjan;

    @Before
    public void setUp() {
        nodes = new ArrayList<>() {
            {
                add(new Node(0));
                add(new Node(1));
                add(new Node(2));
                add(new Node(3));
            }
        };

        edges = new ArrayList<>() {
            {
                add(new Edge(nodes.get(0), nodes.get(1), 6));
                add(new Edge(nodes.get(1), nodes.get(2), 10));
                add(new Edge(nodes.get(1), nodes.get(3), 12));
                add(new Edge(nodes.get(2), nodes.get(1), 10));
                add(new Edge(nodes.get(3), nodes.get(0), 1));
                add(new Edge(nodes.get(3), nodes.get(2), 8));
            }
        };

        originalGraph = new Graph(edges);
        
        List<ATreeNode> roots = new ArrayList<>();
        dynamicTarjan = new DynamicTarjanArborescence(roots,
            new ArrayList<>(), // No contracted edges initially
            new HashMap<>(), // No reduced costs initially
            originalGraph,
            EDGE_COMPARATOR
        );
        dynamicAlgorithm = new FullyDynamicArborescence(originalGraph, roots, dynamicTarjan);
    }

    @Test
    public void testOneRemoveOptimalEdge() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);

        Edge edge = edges.get(5); // 3->2 (weight 8)
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(edge);

        List<Edge> expectedEdges = List.of(
            edges.get(0), // 0->1 (6)
            edges.get(4), // 3->0 (1)
            edges.get(1)  // 1->2 (10)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !e.equals(edge))
                .collect(Collectors.toList())
        );
        
        Assert.assertTrue(isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Total cost does not match expected value after edge removal.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveTwoOptimalEdges() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        List<Edge> edgesToRemove = List.of(
            edges.get(5), // 3->2 (8)
            edges.get(4)  // 3->0 (1)
        );
        
        // Remove first edge
        dynamicAlgorithm.removeEdge(edgesToRemove.get(0));
        // Remove second edge
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(edgesToRemove.get(1));

        List<Edge> expectedEdges = List.of(
            edges.get(0), // 0->1 (6)
            edges.get(1), // 1->2 (10)
            edges.get(2)  // 1->3 (12)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !edgesToRemove.contains(e))
                .collect(Collectors.toList())
        );
        
        Assert.assertTrue(isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Total cost does not match expected value after removing two edges.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveTwoOptimalAndOneSuboptimalEdge() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        List<Edge> edgesToRemove = List.of(
            edges.get(5), // 3->2 (8)
            edges.get(4), // 3->0 (1)
            edges.get(3)  // 2->1 (10) - suboptimal
        );
        
        // Remove edges one by one
        for (Edge edge : edgesToRemove) {
            dynamicAlgorithm.removeEdge(edge);
        }
        
        List<Edge> updatedArborescence = dynamicAlgorithm.getCurrentArborescence();

        List<Edge> expectedEdges = List.of(
            edges.get(0), // 0->1 (6)
            edges.get(1), // 1->2 (10)
            edges.get(2)  // 1->3 (12)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !edgesToRemove.contains(e))
                .collect(Collectors.toList())
        );
        
        Assert.assertTrue(isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Total cost does not match expected value after removing three edges.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveNonArborescenceEdge() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        List<Edge> initialArborescence = new ArrayList<>(dynamicAlgorithm.getCurrentArborescence());
        int initialCost = initialArborescence.stream().mapToInt(Edge::getWeight).sum();

        // Remove edge not in arborescence: 1->3 (12)
        Edge nonArborescenceEdge = edges.get(2);
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(nonArborescenceEdge);

        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Assert.assertEquals("Arborescence should remain unchanged",
            initialArborescence, updatedArborescence);
        Assert.assertEquals("Cost should remain unchanged after removing non-arborescence edge",
            initialCost, newCost);
    }

    @Test
    public void testSequentialRemovals() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Remove non-arborescence edge first
        dynamicAlgorithm.removeEdge(edges.get(2)); // 1->3 (12)
        int costAfterFirst = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        // Remove arborescence edge
        dynamicAlgorithm.removeEdge(edges.get(5)); // 3->2 (8)
        int costAfterSecond = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        Assert.assertTrue("Cost should increase after removing arborescence edge",
            costAfterSecond > costAfterFirst);
        
        // Remove another arborescence edge
        dynamicAlgorithm.removeEdge(edges.get(4)); // 3->0 (1)
        List<Edge> finalArborescence = dynamicAlgorithm.getCurrentArborescence();
        
        Graph finalGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !e.equals(edges.get(2)) && 
                            !e.equals(edges.get(5)) && 
                            !e.equals(edges.get(4)))
                .collect(Collectors.toList())
        );
        
        Assert.assertTrue("Final arborescence should be valid",
            isValidArborescence(finalGraph, new Graph(finalArborescence)));
    }

    @Test
    public void testRemoveAndReAdd() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        int initialCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        // Remove an arborescence edge
        Edge removedEdge = edges.get(5); // 3->2 (8)
        dynamicAlgorithm.removeEdge(removedEdge);
        
        int costAfterRemoval = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Cost should change after removing arborescence edge",
            costAfterRemoval != initialCost);
        
        // Add it back
        dynamicAlgorithm.addEdge(removedEdge);
        int costAfterReadd = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        printTestResults(dynamicAlgorithm.getCurrentArborescence(), costAfterReadd, initialCost);
        Assert.assertEquals("Cost should return to initial after re-adding edge",
            initialCost, costAfterReadd);
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

    private void printTestResults(List<Edge> arborescence, int newCost, int expectedCost) {
        System.out.println("Current Arborescence:");
        for (Edge edge : arborescence) {
            System.out.println(edge);
        }
        System.out.println("New cost: " + newCost);
        System.out.println("Expected cost: " + expectedCost);
    }
}
