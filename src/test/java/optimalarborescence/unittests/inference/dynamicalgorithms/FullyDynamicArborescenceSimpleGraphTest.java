package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.inference.dynamic.DynamicTarjanArborescence;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Comparator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Dynamic version of TarjanArborescenceSimpleGraphTest.
 * Tests the FullyDynamicArborescence class with edge insertions and deletions.
 * 
 * Based on the graph used in section 2.2.4 of:
 * Espada, J.; Francisco, A.P.; Rocher, T.; Russo, L.M.S.; Vaz, C. On Finding Optimal (Dynamic) Arborescences. 
 * Algorithms 2023, 16, 559. https://doi.org/10.3390/a16120559 
 */
public class FullyDynamicArborescenceSimpleGraphTest {
    // Default comparator for edges - min heap based on weight
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());


    private static final String ALLELIC_PROFILE = "ACGT";

    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;
    private FullyDynamicArborescence dynamicAlgorithm;
    private DynamicTarjanArborescence dynamicTarjan;

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
                add(new Edge(nodes.get(0), nodes.get(1), 6));
                add(new Edge(nodes.get(1), nodes.get(2), 10));
                add(new Edge(nodes.get(1), nodes.get(3), 12));
                add(new Edge(nodes.get(2), nodes.get(1), 10));
                add(new Edge(nodes.get(3), nodes.get(0), 1));
                add(new Edge(nodes.get(3), nodes.get(2), 8));
            }
        };

        originalGraph = new Graph(edges);
        
        // Initialize with empty ATrees for now
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
    public void testInitialArborescence() {
        Graph result = dynamicAlgorithm.inferPhylogeny(originalGraph);

        Assert.assertNotNull(result);
        Assert.assertEquals(originalGraph.getNumNodes(), result.getNumNodes());
        Assert.assertEquals(originalGraph.getNumNodes() - 1, result.getNumEdges());

        List<Edge> expectedEdges = List.of(
            new Edge(nodes.get(3), nodes.get(0), 1),
            new Edge(nodes.get(0), nodes.get(1), 6),
            new Edge(nodes.get(3), nodes.get(2), 8)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = result.getEdges().stream().mapToInt(Edge::getWeight).sum();
        
        Assert.assertTrue(isValidArborescence(originalGraph, result));
        Assert.assertEquals("Total cost of the initial arborescence does not match expected value.",
            expectedCost, resultCost);
    }

    @Test
    public void testInsertSuboptimalEdge() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        List<Edge> initialArborescence = new ArrayList<>(dynamicAlgorithm.getCurrentArborescence());
        int initialCost = initialArborescence.stream().mapToInt(Edge::getWeight).sum();

        // Insert a suboptimal edge (weight 15, higher than any existing edge to node 2)
        Edge suboptimalEdge = new Edge(nodes.get(0), nodes.get(2), 15);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(suboptimalEdge);

        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Assert.assertTrue("Arborescence should remain valid after inserting suboptimal edge",
            isValidArborescence(dynamicAlgorithm.getGraph(), new Graph(updatedArborescence)));
        Assert.assertEquals("Cost should remain unchanged after inserting suboptimal edge",
            initialCost, newCost);
    }

    @Test
    public void testInsertOptimalEdge() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Insert an optimal edge (weight 2, better than existing edge 3->2 with weight 8)
        Edge optimalEdge = new Edge(nodes.get(0), nodes.get(2), 2);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(optimalEdge);

        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        int expectedCost = 1 + 6 + 2; // 3->0 (1) + 0->1 (6) + 0->2 (2)
        
        Assert.assertTrue("Arborescence should remain valid after inserting optimal edge",
            isValidArborescence(dynamicAlgorithm.getGraph(), new Graph(updatedArborescence)));
        Assert.assertTrue("Cost should decrease or stay same after inserting optimal edge",
            newCost <= 15); // Initial cost was 15
        Assert.assertEquals("Cost should match expected after inserting optimal edge",
            expectedCost, newCost);
    }

    @Test
    public void testRemoveNonArborescenceEdge() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        List<Edge> initialArborescence = new ArrayList<>(dynamicAlgorithm.getCurrentArborescence());
        int initialCost = initialArborescence.stream().mapToInt(Edge::getWeight).sum();

        // Remove an edge that is NOT in the arborescence (1->3 with weight 12)
        Edge nonArborescenceEdge = edges.get(2); // 1->3
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(nonArborescenceEdge);

        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Assert.assertTrue("Arborescence should remain valid after removing non-arborescence edge",
            isValidArborescence(dynamicAlgorithm.getGraph(), new Graph(updatedArborescence)));
        Assert.assertEquals("Cost should remain unchanged after removing non-arborescence edge",
            initialCost, newCost);
        Assert.assertEquals("Arborescence structure should remain unchanged",
            initialArborescence, updatedArborescence);
    }

    @Test
    public void testRemoveArborescenceEdge() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Remove an edge that IS in the arborescence (3->2 with weight 8)
        Edge arborescenceEdge = edges.get(5); // 3->2
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(arborescenceEdge);
        
        Assert.assertTrue("Arborescence should remain valid after removing arborescence edge",
            isValidArborescence(dynamicAlgorithm.getGraph(), new Graph(updatedArborescence)));
        
        // The new arborescence should use 1->2 (weight 10) instead
        int expectedCost = 1 + 6 + 10; // 3->0 (1) + 0->1 (6) + 1->2 (10)
        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should match expected after removing arborescence edge",
            expectedCost, newCost);
    }

    @Test
    public void testUpdateEdgeToLowerWeight() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Update edge 1->2 from weight 10 to weight 5
        Edge updatedEdge = new Edge(nodes.get(1), nodes.get(2), 5);
        List<Edge> updatedArborescence = dynamicAlgorithm.updateEdge(updatedEdge);
        
        Assert.assertTrue("Arborescence should remain valid after updating edge weight",
            isValidArborescence(dynamicAlgorithm.getGraph(), new Graph(updatedArborescence)));
        
        // Edge 1->2 with weight 5 is better than 3->2 with weight 8, so it should be used
        int expectedCost = 1 + 6 + 5; // 3->0 (1) + 0->1 (6) + 1->2 (5)
        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should decrease after updating edge to lower weight",
            expectedCost, newCost);
    }

    @Test
    public void testUpdateEdgeToHigherWeight() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Update edge 3->0 from weight 1 to weight 10
        Edge updatedEdge = new Edge(nodes.get(3), nodes.get(0), 10);
        List<Edge> updatedArborescence = dynamicAlgorithm.updateEdge(updatedEdge);
        
        Assert.assertTrue("Arborescence should remain valid after updating edge weight",
            isValidArborescence(dynamicAlgorithm.getGraph(), new Graph(updatedArborescence)));
        
        // Since 3->0 became more expensive, the arborescence structure might change
        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Cost should be positive after updating edge to higher weight",
            newCost > 0);
    }

    @Test
    public void testSequentialInsertions() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        int initialCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();

        // Insert first suboptimal edge
        Edge suboptimal1 = new Edge(nodes.get(1), nodes.get(2), 15);
        dynamicAlgorithm.addEdge(suboptimal1);
        
        int costAfterFirst = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should remain unchanged after first suboptimal insertion",
            initialCost, costAfterFirst);

        // Insert optimal edge
        Edge optimal = new Edge(nodes.get(0), nodes.get(2), 2);
        dynamicAlgorithm.addEdge(optimal);
        
        int costAfterOptimal = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Cost should improve after optimal insertion",
            costAfterOptimal < initialCost);

        // Insert another suboptimal edge
        Edge suboptimal2 = new Edge(nodes.get(2), nodes.get(3), 20);
        dynamicAlgorithm.addEdge(suboptimal2);
        
        int finalCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should remain unchanged after second suboptimal insertion",
            costAfterOptimal, finalCost);
        
        Assert.assertTrue("Final arborescence should be valid",
            isValidArborescence(dynamicAlgorithm.getGraph(), 
                new Graph(dynamicAlgorithm.getCurrentArborescence())));
    }

    @Test
    public void testMixedInsertionsAndRemovals() {
        // First compute initial arborescence
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Insert optimal edge
        Edge optimal = new Edge(nodes.get(0), nodes.get(2), 2);
        dynamicAlgorithm.addEdge(optimal);
        
        int costAfterInsertion = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        // Remove a non-arborescence edge
        Edge toRemove = edges.get(2); // 1->3
        dynamicAlgorithm.removeEdge(toRemove);
        
        int costAfterRemoval = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should remain unchanged after removing non-arborescence edge",
            costAfterInsertion, costAfterRemoval);
        
        // Insert another edge
        Edge anotherEdge = new Edge(nodes.get(2), nodes.get(0), 3);
        dynamicAlgorithm.addEdge(anotherEdge);
        
        Assert.assertTrue("Final arborescence should be valid",
            isValidArborescence(dynamicAlgorithm.getGraph(), 
                new Graph(dynamicAlgorithm.getCurrentArborescence())));
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
