package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.inference.dynamic.DynamicTarjanArborescence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the addNode and removeNode operations of FullyDynamicArborescence.
 *
 * Each test uses a 4-node base graph (nodes 0-3), computes the initial arborescence,
 * performs the node operation, and then verifies that:
 *   1. The resulting arborescence is structurally valid.
 *   2. Its total weight equals the weight produced by CameriniForest run from scratch
 *      on the same modified graph (oracle comparison).
 *
 * addNode tests add node 4 to {0,1,2,3}, resulting in {0,1,2,3,4}.
 *
 * removeNode tests remove node 1 (a middle ID), leaving sparse IDs {0,2,3} in each
 * scenario. Each test uses a different edge configuration so that node 1 plays a
 * different role (leaf, internal, root) across the three scenarios.
 *
 * Base graph for addNode tests (section 2.2.4 of Espada et al., Algorithms 2023):
 *   0->1 (6), 1->2 (10), 1->3 (12), 2->1 (10), 3->0 (1), 3->2 (8)
 * Optimal arborescence: 3->0 (1), 0->1 (6), 3->2 (8)  total = 15
 */
public class FullyDynamicArborescenceNodeTest {

    private static final Comparator<Edge> EDGE_COMPARATOR =
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());

    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;
    private FullyDynamicArborescence dynamicAlgorithm;

    @Before
    public void setUp() {
        nodes = new ArrayList<>();
        nodes.add(new Node(0));
        nodes.add(new Node(1));
        nodes.add(new Node(2));
        nodes.add(new Node(3));

        edges = new ArrayList<>();
        edges.add(new Edge(nodes.get(0), nodes.get(1), 6));
        edges.add(new Edge(nodes.get(1), nodes.get(2), 10));
        edges.add(new Edge(nodes.get(1), nodes.get(3), 12));
        edges.add(new Edge(nodes.get(2), nodes.get(1), 10));
        edges.add(new Edge(nodes.get(3), nodes.get(0), 1));
        edges.add(new Edge(nodes.get(3), nodes.get(2), 8));

        originalGraph = new Graph(edges);

        List<ATreeNode> roots = new ArrayList<>();
        DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
            roots, new ArrayList<>(), new HashMap<>(), originalGraph, EDGE_COMPARATOR);
        dynamicAlgorithm = new FullyDynamicArborescence(originalGraph, roots, dynamicTarjan);

        // Compute the initial arborescence so the ATree is populated
        dynamicAlgorithm.inferPhylogeny(originalGraph);
    }

    // -----------------------------------------------------------------------
    // addNode tests
    // -----------------------------------------------------------------------

    /**
     * Add a new node (id=4) that connects only to an existing node (3->4, weight 3).
     * The resulting graph has a unique arborescence extension: edge 3->4 must be selected.
     */
    @Test
    public void testAddNodeWithSingleIncomingEdge() {
        Node newNode = new Node(4);
        Edge incomingEdge = new Edge(nodes.get(3), newNode, 3);

        List<Edge> newEdges = List.of(incomingEdge);
        List<Edge> result = dynamicAlgorithm.addNode(newNode, newEdges);

        Graph resultGraph = new Graph(result);
        Assert.assertTrue("Arborescence must be valid after addNode",
            isValidArborescence(dynamicAlgorithm.getGraph(), resultGraph));
        Assert.assertEquals("Weight must match CameriniForest oracle",
            staticWeight(dynamicAlgorithm.getGraph()),
            result.stream().mapToInt(Edge::getWeight).sum());
    }

    /**
     * Add a new node (id=4) with two incoming edges from different existing nodes.
     * The dynamic algorithm must pick the cheaper one (0->4 weight 2 beats 1->4 weight 9).
     */
    @Test
    public void testAddNodeWithMultipleIncomingEdges() {
        Node newNode = new Node(4);
        Edge cheapEdge = new Edge(nodes.get(0), newNode, 2);
        Edge expensiveEdge = new Edge(nodes.get(1), newNode, 9);

        List<Edge> newEdges = List.of(cheapEdge, expensiveEdge);
        List<Edge> result = dynamicAlgorithm.addNode(newNode, newEdges);

        Graph resultGraph = new Graph(result);
        Assert.assertTrue("Arborescence must be valid after addNode with multiple incoming edges",
            isValidArborescence(dynamicAlgorithm.getGraph(), resultGraph));
        Assert.assertEquals("Weight must match CameriniForest oracle",
            staticWeight(dynamicAlgorithm.getGraph()),
            result.stream().mapToInt(Edge::getWeight).sum());
    }

    /**
     * Add a new node (id=4) with an outgoing edge to an existing node (4->2, weight 1).
     * Edge 4->2 (weight 1) is cheaper than the current best path to node 2 (3->2, weight 8),
     * so the arborescence must change to use 4->2, but 4 itself needs an incoming edge.
     */
    @Test
    public void testAddNodeWithOutgoingEdgeThatImprovesCost() {
        Node newNode = new Node(4);
        Edge incoming = new Edge(nodes.get(3), newNode, 5);  // 3->4 (5)
        Edge outgoing = new Edge(newNode, nodes.get(2), 1);   // 4->2 (1)

        List<Edge> newEdges = List.of(incoming, outgoing);
        List<Edge> result = dynamicAlgorithm.addNode(newNode, newEdges);

        Graph resultGraph = new Graph(result);
        Assert.assertTrue("Arborescence must be valid after addNode with outgoing edge",
            isValidArborescence(dynamicAlgorithm.getGraph(), resultGraph));
        Assert.assertEquals("Weight must match CameriniForest oracle",
            staticWeight(dynamicAlgorithm.getGraph()),
            result.stream().mapToInt(Edge::getWeight).sum());
    }

    // -----------------------------------------------------------------------
    // removeNode tests
    //
    // All tests remove node 1 (a middle ID), leaving sparse IDs {0,2,3} after
    // removal. This exercises the sparse-ID code path in CameriniForest.
    // Each test uses a different edge set so node 1 plays a different role.
    // -----------------------------------------------------------------------

    /**
     * Node 1 is a LEAF in the optimal arborescence.
     *
     * Graph: 0->1(5), 0->2(3), 0->3(1), 2->3(10), 3->2(10)
     * Optimal arborescence: root=0, {0->1(5), 0->2(3), 0->3(1)}  total = 9
     * After removing node 1: remaining nodes {0,2,3} (sparse), edges {0->2(3), 0->3(1), 2->3(10), 3->2(10)}
     * Expected arborescence: root=0, {0->2(3), 0->3(1)}  total = 4
     */
    @Test
    public void testRemoveLeafNode() {
        Node n0 = new Node(0), n1 = new Node(1), n2 = new Node(2), n3 = new Node(3);
        List<Edge> leafEdges = new ArrayList<>();
        leafEdges.add(new Edge(n0, n1, 5));
        leafEdges.add(new Edge(n0, n2, 3));
        leafEdges.add(new Edge(n0, n3, 1));
        leafEdges.add(new Edge(n2, n3, 10));
        leafEdges.add(new Edge(n3, n2, 10));

        Graph g = new Graph(leafEdges);
        List<ATreeNode> roots = new ArrayList<>();
        DynamicTarjanArborescence dta = new DynamicTarjanArborescence(
            roots, new ArrayList<>(), new HashMap<>(), g, EDGE_COMPARATOR);
        FullyDynamicArborescence alg = new FullyDynamicArborescence(g, roots, dta);
        alg.inferPhylogeny(g);

        List<Edge> incident = new ArrayList<>();
        for (Edge e : alg.getGraph().getEdges()) {
            if (e.getSource().equals(n1) || e.getDestination().equals(n1)) incident.add(e);
        }

        List<Edge> result = alg.removeNode(n1, incident);

        Assert.assertFalse("Removed node must not appear in arborescence",
            result.stream().anyMatch(e -> e.getSource().equals(n1) || e.getDestination().equals(n1)));
        Assert.assertTrue("Arborescence must be valid after removing leaf node",
            isValidArborescence(alg.getGraph(), new Graph(result)));
        Assert.assertEquals("Weight must match CameriniForest oracle",
            staticWeight(alg.getGraph()),
            result.stream().mapToInt(Edge::getWeight).sum());
    }

    /**
     * Node 1 is an INTERNAL node in the optimal arborescence.
     *
     * Graph: 0->1(2), 1->2(3), 1->3(4), 2->3(20), 3->2(20), 0->2(15), 0->3(16)
     * Optimal arborescence: root=0, {0->1(2), 1->2(3), 1->3(4)}  total = 9
     * After removing node 1: remaining nodes {0,2,3} (sparse), edges {0->2(15), 0->3(16), 2->3(20), 3->2(20)}
     * Expected arborescence: root=0, {0->2(15), 0->3(16)}  total = 31
     */
    @Test
    public void testRemoveInternalNode() {
        Node n0 = new Node(0), n1 = new Node(1), n2 = new Node(2), n3 = new Node(3);
        List<Edge> intEdges = new ArrayList<>();
        intEdges.add(new Edge(n0, n1, 2));
        intEdges.add(new Edge(n1, n2, 3));
        intEdges.add(new Edge(n1, n3, 4));
        intEdges.add(new Edge(n2, n3, 20));
        intEdges.add(new Edge(n3, n2, 20));
        intEdges.add(new Edge(n0, n2, 15));
        intEdges.add(new Edge(n0, n3, 16));

        Graph g = new Graph(intEdges);
        List<ATreeNode> roots = new ArrayList<>();
        DynamicTarjanArborescence dta = new DynamicTarjanArborescence(
            roots, new ArrayList<>(), new HashMap<>(), g, EDGE_COMPARATOR);
        FullyDynamicArborescence alg = new FullyDynamicArborescence(g, roots, dta);
        alg.inferPhylogeny(g);

        List<Edge> incident = new ArrayList<>();
        for (Edge e : alg.getGraph().getEdges()) {
            if (e.getSource().equals(n1) || e.getDestination().equals(n1)) incident.add(e);
        }

        List<Edge> result = alg.removeNode(n1, incident);

        Assert.assertFalse("Removed node must not appear in arborescence",
            result.stream().anyMatch(e -> e.getSource().equals(n1) || e.getDestination().equals(n1)));
        Assert.assertTrue("Arborescence must be valid after removing internal node",
            isValidArborescence(alg.getGraph(), new Graph(result)));
        Assert.assertEquals("Weight must match CameriniForest oracle",
            staticWeight(alg.getGraph()),
            result.stream().mapToInt(Edge::getWeight).sum());
    }

    /**
     * Node 1 is the ROOT of the optimal arborescence.
     *
     * Graph: 1->0(3), 1->2(4), 1->3(5), 0->2(20), 2->0(20), 0->3(20), 3->0(20)
     * Optimal arborescence: root=1, {1->0(3), 1->2(4), 1->3(5)}  total = 12
     * After removing node 1: remaining nodes {0,2,3} (sparse), edges {0->2(20), 2->0(20), 0->3(20), 3->0(20)}
     * Expected arborescence: root=0, {0->2(20), 0->3(20)}  total = 40  (or 2->0+..., etc. oracle decides)
     */
    @Test
    public void testRemoveRootNode() {
        Node n0 = new Node(0), n1 = new Node(1), n2 = new Node(2), n3 = new Node(3);
        List<Edge> rootEdges = new ArrayList<>();
        rootEdges.add(new Edge(n1, n0, 3));
        rootEdges.add(new Edge(n1, n2, 4));
        rootEdges.add(new Edge(n1, n3, 5));
        rootEdges.add(new Edge(n0, n2, 20));
        rootEdges.add(new Edge(n2, n0, 20));
        rootEdges.add(new Edge(n0, n3, 20));
        rootEdges.add(new Edge(n3, n0, 20));

        Graph g = new Graph(rootEdges);
        List<ATreeNode> roots = new ArrayList<>();
        DynamicTarjanArborescence dta = new DynamicTarjanArborescence(
            roots, new ArrayList<>(), new HashMap<>(), g, EDGE_COMPARATOR);
        FullyDynamicArborescence alg = new FullyDynamicArborescence(g, roots, dta);
        alg.inferPhylogeny(g);

        List<Edge> incident = new ArrayList<>();
        for (Edge e : alg.getGraph().getEdges()) {
            if (e.getSource().equals(n1) || e.getDestination().equals(n1)) incident.add(e);
        }

        List<Edge> result = alg.removeNode(n1, incident);

        Assert.assertFalse("Removed node must not appear in arborescence",
            result.stream().anyMatch(e ->
                e.getSource().equals(n1) || e.getDestination().equals(n1)));
        Assert.assertTrue("Arborescence must be valid after removing root node",
            isValidArborescence(alg.getGraph(), new Graph(result)));
        Assert.assertEquals("Weight must match CameriniForest oracle",
            staticWeight(alg.getGraph()),
            result.stream().mapToInt(Edge::getWeight).sum());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Run CameriniForest from scratch on the given graph and return the total
     * weight of the minimum spanning arborescence. This is the oracle value that
     * the dynamic algorithm's result must match.
     */
    private int staticWeight(Graph g) {
        CameriniForest oracle = new CameriniForest(g, EDGE_COMPARATOR);
        Graph result = oracle.inferPhylogeny(g);
        return result.getEdges().stream().mapToInt(Edge::getWeight).sum();
    }

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
                return false;
            }
            incidentNodes.put(dest.getId(), dest);
        }

        List<Node> allNodes = new ArrayList<>(graph.getNodes());
        for (Node node : incidentNodes.values()) {
            allNodes.remove(node);
        }
        if (allNodes.size() != 1) {
            return false;
        }
        Node root = allNodes.get(0);

        return bfs(arborescence, root);
    }

    private boolean bfs(Graph graph, Node start) {
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
