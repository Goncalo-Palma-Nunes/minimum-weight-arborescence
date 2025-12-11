package optimalarborescence.unittests.inference.staticalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.TarjanArborescence;

import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Comparator;

import org.junit.Assert;
import org.junit.Test;

/**
 * Based on the graph used in the examples of section 2.2.4 of 
 * <p>
 * Espada, J.; Francisco, A.P.; Rocher, T.; Russo, L.M.S.; Vaz, C. On Finding Optimal (Dynamic) Arborescences. 
 * Algorithms 2023, 16, 559. https://doi.org/10.3390/a16120559 
 */
public class TarjanArborescenceSimpleGraphDeletionTest {
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
    public void testOneRemoveOptimalEdge() {

        Edge edge = edges.get(5);
        List<Edge> edgesAfterRemoval = originalGraph.getEdges().stream().filter(e -> e != edge).collect(Collectors.toList());

        Graph newGraph = new Graph(edgesAfterRemoval);
        Graph phylogeny = new TarjanArborescence(newGraph, EDGE_COMPARATOR).inferPhylogeny(newGraph);

        List<Edge> expectedEdges = List.of(
            edges.get(0),
            edges.get(4),
            edges.get(1)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = phylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertTrue(isValidArborescence(newGraph, phylogeny));
        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveTwoOptimalEdges() {
        List<Edge> edgesToRemove = List.of(
            edges.get(5),
            edges.get(4)
        );
        List<Edge> edgesAfterRemoval = originalGraph.getEdges().stream().filter(e -> !edgesToRemove.contains(e)).collect(Collectors.toList());

        Graph newGraph = new Graph(edgesAfterRemoval);
        Graph phylogeny = new TarjanArborescence(newGraph, EDGE_COMPARATOR).inferPhylogeny(newGraph);

        List<Edge> expectedEdges = List.of(
            edges.get(0),
            edges.get(1),
            edges.get(2)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = phylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertTrue(isValidArborescence(newGraph, phylogeny));
        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveTwoOptimalAndOneSuboptimalEdge() {
        List<Edge> edgesToRemove = List.of(
            edges.get(5),
            edges.get(4),
            edges.get(3)
        );
        List<Edge> edgesAfterRemoval = originalGraph.getEdges().stream().filter(e -> !edgesToRemove.contains(e)).collect(Collectors.toList());

        Graph newGraph = new Graph(edgesAfterRemoval);
        Graph phylogeny = new TarjanArborescence(newGraph, EDGE_COMPARATOR).inferPhylogeny(newGraph);

        List<Edge> expectedEdges = List.of(
            edges.get(0),
            edges.get(1),
            edges.get(2)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = phylogeny.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertTrue(isValidArborescence(newGraph, phylogeny));
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
