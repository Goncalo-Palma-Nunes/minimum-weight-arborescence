package optimalarborescence.unittests.inference.dynamicalgorithms;



import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.inference.dynamic.DynamicTarjanArborescence;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Dynamic version of CameriniForestDirectedStronglyConnectedGraphTest
 * Based on the graph used in the examples of section 4.9.2 of 
 * <p>
 * Joaquim Espada's master thesis: "Large scale phylogenetic inference from noisy data based
 * on minimum weight spanning arborescences"
 */
/**
 * Dynamic version of CameriniForestDirectedStronglyConnectedGraphTest
 * Based on the graph used in the examples of section 4.9.2 of 
 * <p>
 * Joaquim Espada's master thesis: "Large scale phylogenetic inference from noisy data based
 * on minimum weight spanning arborescences"
 */
public class FullyDynamicArborescenceDirectedStronglyConnectedGraphTest {
    // Default comparator for edges - min heap based on weight
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());


    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;
    private FullyDynamicArborescence dynamicAlgorithm;
    private DynamicTarjanArborescence dynamicTarjan;
    private List<Edge> expectedEdges;

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

        expectedEdges = List.of(
            new Edge(nodes.get(1), nodes.get(0), 2),
            new Edge(nodes.get(0), nodes.get(3), 3),
            new Edge(nodes.get(3), nodes.get(2), 2)
        );
        
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
    public void testFullyDynamicArborescenceDirectedStronglyConnectedGraph() {
        Graph result = dynamicAlgorithm.inferPhylogeny(originalGraph);
        List<Edge> resultEdges = dynamicAlgorithm.getCurrentArborescence();

        Assert.assertNotNull(result);
        Assert.assertEquals(expectedEdges.size(), resultEdges.size());
        Assert.assertTrue(isValidArborescence(originalGraph, new Graph(resultEdges)));

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = resultEdges.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveEdgeFromArborescence() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Remove edge 1->0 (weight 2) which is in the optimal arborescence
        Edge edgeToRemove = edges.get(2); // 1->0
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(edgeToRemove);

        // After removing 1->0, should still have a valid arborescence
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !e.equals(edgeToRemove))
                .toList()
        );
        
        Assert.assertTrue("Should be a valid arborescence", 
            isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Should have n-1 edges", 
            updatedGraph.getNumNodes() - 1, updatedArborescence.size());
    }

    @Test
    public void testRemoveAnotherEdgeFromArborescence() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Remove edge 3->2 (weight 2) which is in the optimal arborescence
        Edge edgeToRemove = edges.get(6); // 3->2
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(edgeToRemove);

        // After removing 3->2, should still have a valid arborescence
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !e.equals(edgeToRemove))
                .toList()
        );
        
        Assert.assertTrue("Should be a valid arborescence", 
            isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Should have n-1 edges", 
            updatedGraph.getNumNodes() - 1, updatedArborescence.size());
    }

    @Test
    public void testRemoveNotInArborescence() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Remove edge 2->3 (weight 2) which is in the optimal arborescence
        Edge edgeToRemove = edges.get(5); // 2->3
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(edgeToRemove);

        // After removing 2->3, should still have a valid arborescence
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !e.equals(edgeToRemove))
                .toList()
        );
        
        Assert.assertTrue("Should be a valid arborescence", 
            isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Should have n-1 edges", 
            updatedGraph.getNumNodes() - 1, updatedArborescence.size());
    }

    @Test
    public void testAddNewEdge() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        int initialCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        // Add a new edge that could improve the arborescence: 2->0 with weight 1
        Edge newEdge = new Edge(nodes.get(2), nodes.get(0), 1);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(newEdge);
        
        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        // The new edge should improve the arborescence (lower cost)
        Assert.assertTrue("Cost should decrease or stay the same with better edge",
            newCost <= initialCost);
        
        Graph updatedGraph = new Graph(originalGraph.getEdges());
        updatedGraph.addEdge(newEdge);
        Assert.assertTrue(isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
    }

    @Test
    public void testAddNonOptimalNewEdge() {
        dynamicAlgorithm.inferPhylogeny(originalGraph).getEdges();
        int initialCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        // Add a new edge that could improve the arborescence: 1->2 with weight 10
        Edge newEdge = new Edge(nodes.get(1), nodes.get(2), 10);
        List<Edge> updatedArborescence = dynamicAlgorithm.addEdge(newEdge);
        
        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        // The new edge should improve the arborescence (lower cost)
        Assert.assertTrue("Cost should stay the same",
            newCost == initialCost);
        
        Graph updatedGraph = new Graph(originalGraph.getEdges());
        updatedGraph.addEdge(newEdge);
        Assert.assertTrue(isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
    }

    @Test
    public void testRemoveAndReAddEdge() {
        dynamicAlgorithm.inferPhylogeny(originalGraph);
        int initialCost = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
        // Remove an arborescence edge
        Edge removedEdge = edges.get(6); // 3->2 (2)
        dynamicAlgorithm.removeEdge(removedEdge);
        
        int costAfterRemoval = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Cost should not change after removing arborescence edge, because there are two optimum branchings",
            costAfterRemoval == initialCost);
        
        // Add it back
        dynamicAlgorithm.addEdge(removedEdge);
        int costAfterReadd = dynamicAlgorithm.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        
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
}
