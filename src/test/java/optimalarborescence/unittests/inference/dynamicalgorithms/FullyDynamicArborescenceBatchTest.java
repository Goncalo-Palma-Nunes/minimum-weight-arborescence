// package optimalarborescence.unittests.inference.dynamicalgorithms;

// import optimalarborescence.graph.Edge;
// import optimalarborescence.graph.Node;
// import optimalarborescence.graph.Graph;
// import optimalarborescence.inference.CameriniForest;
// import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
// import optimalarborescence.inference.dynamic.ATreeNode;
// import optimalarborescence.inference.dynamic.DynamicTarjanArborescence;

// import java.util.List;
// import java.util.ArrayList;
// import java.util.Comparator;
// import java.util.HashMap;
// import java.util.Map;
// import java.util.stream.Collectors;

// import org.junit.Assert;
// import org.junit.Before;
// import org.junit.Test;

// /**
//  * Tests for batch edge operations on FullyDynamicArborescence.
//  *
//  * Uses the same graph as FullyDynamicArborescenceDeletionTest (from section 2.2.4
//  * of the thesis), plus insertion-oriented graphs matching FullyDynamicArborescenceInsertionsTest.
//  *
//  * Each test verifies:
//  *   - isValidArborescence() — structural validity
//  *   - Cost matches a fresh static CameriniForest run on the same final graph — optimality
//  */
// public class FullyDynamicArborescenceBatchTest {

//     private static final Comparator<Edge> EDGE_COMPARATOR =
//         (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());

//     // Deletion graph (4 nodes, used for removal tests)
//     private List<Node> delNodes;
//     private List<Edge> delEdges;
//     private Graph delGraph;
//     private FullyDynamicArborescence delAlgorithm;

//     // Insertion graph (4 nodes, used for insertion tests)
//     private List<Node> insNodes;
//     private List<Edge> insEdges;
//     private Graph insGraph;
//     private FullyDynamicArborescence insAlgorithm;

//     @Before
//     public void setUp() {
//         // --- Deletion graph ---
//         delNodes = new ArrayList<>();
//         delNodes.add(new Node(0));
//         delNodes.add(new Node(1));
//         delNodes.add(new Node(2));
//         delNodes.add(new Node(3));

//         delEdges = new ArrayList<>();
//         delEdges.add(new Edge(delNodes.get(0), delNodes.get(1), 6));   // 0
//         delEdges.add(new Edge(delNodes.get(1), delNodes.get(2), 10));  // 1
//         delEdges.add(new Edge(delNodes.get(1), delNodes.get(3), 12));  // 2
//         delEdges.add(new Edge(delNodes.get(2), delNodes.get(1), 10));  // 3
//         delEdges.add(new Edge(delNodes.get(3), delNodes.get(0), 1));   // 4
//         delEdges.add(new Edge(delNodes.get(3), delNodes.get(2), 8));   // 5

//         delGraph = new Graph(delEdges);
//         List<ATreeNode> delRoots = new ArrayList<>();
//         DynamicTarjanArborescence delTarjan = new DynamicTarjanArborescence(
//             delRoots, new ArrayList<>(), new HashMap<>(), delGraph, EDGE_COMPARATOR);
//         delAlgorithm = new FullyDynamicArborescence(delGraph, delRoots, delTarjan);

//         // --- Insertion graph ---
//         insNodes = new ArrayList<>();
//         insNodes.add(new Node(0));
//         insNodes.add(new Node(1));
//         insNodes.add(new Node(2));
//         insNodes.add(new Node(3));

//         insEdges = new ArrayList<>();
//         insEdges.add(new Edge(insNodes.get(0), insNodes.get(1), 2));   // 0
//         insEdges.add(new Edge(insNodes.get(0), insNodes.get(3), 3));   // 1
//         insEdges.add(new Edge(insNodes.get(1), insNodes.get(0), 2));   // 2
//         insEdges.add(new Edge(insNodes.get(1), insNodes.get(3), 10));  // 3
//         insEdges.add(new Edge(insNodes.get(2), insNodes.get(1), 3));   // 4
//         insEdges.add(new Edge(insNodes.get(2), insNodes.get(3), 2));   // 5
//         insEdges.add(new Edge(insNodes.get(3), insNodes.get(2), 2));   // 6

//         insGraph = new Graph(insEdges);
//         List<ATreeNode> insRoots = new ArrayList<>();
//         DynamicTarjanArborescence insTarjan = new DynamicTarjanArborescence(
//             insRoots, new ArrayList<>(), new HashMap<>(), insGraph, EDGE_COMPARATOR);
//         insAlgorithm = new FullyDynamicArborescence(insGraph, insRoots, insTarjan);
//     }

//     // -------------------------------------------------------------------------
//     // Batch removal tests
//     // -------------------------------------------------------------------------

//     @Test
//     public void testBatchRemoveNonArborescenceEdgesOnly() {
//         delAlgorithm.inferPhylogeny(delGraph);
//         List<Edge> initialArborescence = new ArrayList<>(delAlgorithm.getCurrentArborescence());
//         int initialCost = costOf(initialArborescence);

//         // Remove edges that are NOT in the arborescence: 1->3 (12) and 2->1 (10)
//         List<Edge> toRemove = List.of(delEdges.get(2), delEdges.get(3));
//         List<Edge> result = delAlgorithm.removeEdges(toRemove);

//         Assert.assertEquals("Arborescence should be unchanged when only non-arborescence edges are removed",
//             initialCost, costOf(result));

//         Graph remainingGraph = graphMinus(delGraph, toRemove);
//         Assert.assertTrue("Result should be a valid arborescence",
//             isValidArborescence(remainingGraph, new Graph(result)));
//     }

//     @Test
//     public void testBatchRemoveTwoArborescenceEdges() {
//         delAlgorithm.inferPhylogeny(delGraph);

//         // Remove two arborescence edges: 3->2 (8) and 3->0 (1)
//         List<Edge> toRemove = List.of(delEdges.get(5), delEdges.get(4));
//         List<Edge> result = delAlgorithm.removeEdges(toRemove);

//         Graph remainingGraph = graphMinus(delGraph, toRemove);
//         Assert.assertTrue("Result should be a valid arborescence after batch removal",
//             isValidArborescence(remainingGraph, new Graph(result)));

//         int expectedCost = staticInferenceCost(remainingGraph);
//         Assert.assertEquals("Batch removal cost should match static inference on remaining graph",
//             expectedCost, costOf(result));
//     }

//     @Test
//     public void testBatchRemoveMixedArborescenceAndNonArborescence() {
//         delAlgorithm.inferPhylogeny(delGraph);

//         // Remove: 3->2 (8) [arborescence], 3->0 (1) [arborescence], 2->1 (10) [non-arborescence]
//         List<Edge> toRemove = List.of(delEdges.get(5), delEdges.get(4), delEdges.get(3));
//         List<Edge> result = delAlgorithm.removeEdges(toRemove);

//         Graph remainingGraph = graphMinus(delGraph, toRemove);
//         Assert.assertTrue("Result should be a valid arborescence after mixed batch removal",
//             isValidArborescence(remainingGraph, new Graph(result)));

//         int expectedCost = staticInferenceCost(remainingGraph);
//         Assert.assertEquals("Mixed batch removal cost should match static inference on remaining graph",
//             expectedCost, costOf(result));
//     }

//     @Test
//     public void testBatchRemoveEquivalentToSequential() {
//         // Run batch removal
//         delAlgorithm.inferPhylogeny(delGraph);
//         List<Edge> toRemove = List.of(delEdges.get(5), delEdges.get(4));
//         List<Edge> batchResult = delAlgorithm.removeEdges(toRemove);
//         int batchCost = costOf(batchResult);

//         // Run sequential removal on a fresh algorithm
//         List<ATreeNode> seqRoots = new ArrayList<>();
//         DynamicTarjanArborescence seqTarjan = new DynamicTarjanArborescence(
//             seqRoots, new ArrayList<>(), new HashMap<>(), new Graph(delEdges), EDGE_COMPARATOR);
//         Graph seqGraph = new Graph(delEdges);
//         FullyDynamicArborescence seqAlgorithm = new FullyDynamicArborescence(seqGraph, seqRoots, seqTarjan);
//         seqAlgorithm.inferPhylogeny(seqGraph);
//         seqAlgorithm.removeEdge(delEdges.get(5));
//         seqAlgorithm.removeEdge(delEdges.get(4));
//         int seqCost = costOf(seqAlgorithm.getCurrentArborescence());

//         Assert.assertEquals("Batch removal should produce same optimal cost as sequential removal",
//             seqCost, batchCost);
//     }

//     @Test
//     public void testBatchRemoveSingleEdge() {
//         // Batch with one edge should behave identically to single removeEdge
//         delAlgorithm.inferPhylogeny(delGraph);
//         List<Edge> result = delAlgorithm.removeEdges(List.of(delEdges.get(5)));

//         Graph remainingGraph = graphMinus(delGraph, List.of(delEdges.get(5)));
//         Assert.assertTrue("Single-element batch removal should produce valid arborescence",
//             isValidArborescence(remainingGraph, new Graph(result)));

//         int expectedCost = staticInferenceCost(remainingGraph);
//         Assert.assertEquals("Single-element batch removal cost should match static inference",
//             expectedCost, costOf(result));
//     }

//     @Test
//     public void testBatchRemoveEmptyList() {
//         delAlgorithm.inferPhylogeny(delGraph);
//         List<Edge> before = new ArrayList<>(delAlgorithm.getCurrentArborescence());
//         List<Edge> result = delAlgorithm.removeEdges(new ArrayList<>());
//         Assert.assertEquals("Empty batch should leave arborescence unchanged", costOf(before), costOf(result));
//     }

//     // -------------------------------------------------------------------------
//     // Batch insertion tests
//     // -------------------------------------------------------------------------

//     @Test
//     public void testBatchAddAllSuboptimalEdges() {
//         insAlgorithm.inferPhylogeny(insGraph);
//         int initialCost = costOf(insAlgorithm.getCurrentArborescence());

//         // Add edges that are strictly worse than what's already selected
//         List<Edge> toAdd = List.of(
//             new Edge(insNodes.get(1), insNodes.get(2), 20),
//             new Edge(insNodes.get(0), insNodes.get(2), 25)
//         );
//         List<Edge> result = insAlgorithm.addEdges(toAdd);

//         Assert.assertEquals("Adding only suboptimal edges should not change arborescence cost",
//             initialCost, costOf(result));
//         Assert.assertTrue("Result should still be a valid arborescence",
//             isValidArborescence(insAlgorithm.getGraph(), new Graph(result)));
//     }

//     @Test
//     public void testBatchAddOneOptimalOneMixed() {
//         insAlgorithm.inferPhylogeny(insGraph);

//         // Add one suboptimal and one optimal edge
//         Edge suboptimal = new Edge(insNodes.get(1), insNodes.get(2), 15);
//         Edge optimal = new Edge(insNodes.get(2), insNodes.get(1), 1);
//         List<Edge> toAdd = List.of(suboptimal, optimal);

//         List<Edge> result = insAlgorithm.addEdges(toAdd);

//         Assert.assertTrue("Result should be a valid arborescence",
//             isValidArborescence(insAlgorithm.getGraph(), new Graph(result)));

//         int expectedCost = staticInferenceCost(insAlgorithm.getGraph());
//         Assert.assertEquals("Batch add cost should match static inference on final graph",
//             expectedCost, costOf(result));
//     }

//     @Test
//     public void testBatchAddMultipleOptimalEdges() {
//         insAlgorithm.inferPhylogeny(insGraph);

//         // Add two optimal edges that both improve the arborescence
//         Edge optimal1 = new Edge(insNodes.get(2), insNodes.get(1), 1);
//         Edge optimal2 = new Edge(insNodes.get(2), insNodes.get(3), 1);
//         List<Edge> toAdd = List.of(optimal1, optimal2);

//         List<Edge> result = insAlgorithm.addEdges(toAdd);

//         Assert.assertTrue("Result should be a valid arborescence after batch add of optimal edges",
//             isValidArborescence(insAlgorithm.getGraph(), new Graph(result)));

//         int expectedCost = staticInferenceCost(insAlgorithm.getGraph());
//         Assert.assertEquals("Batch add cost should match static inference on final graph",
//             expectedCost, costOf(result));
//     }

//     @Test
//     public void testBatchAddEquivalentToSequential() {
//         insAlgorithm.inferPhylogeny(insGraph);
//         Edge optimal1 = new Edge(insNodes.get(2), insNodes.get(1), 1);
//         Edge optimal2 = new Edge(insNodes.get(2), insNodes.get(3), 1);
//         List<Edge> toAdd = List.of(optimal1, optimal2);

//         List<Edge> batchResult = insAlgorithm.addEdges(toAdd);
//         int batchCost = costOf(batchResult);

//         // Sequential on fresh algorithm
//         List<ATreeNode> seqRoots = new ArrayList<>();
//         Graph seqGraph = new Graph(insEdges);
//         DynamicTarjanArborescence seqTarjan = new DynamicTarjanArborescence(
//             seqRoots, new ArrayList<>(), new HashMap<>(), seqGraph, EDGE_COMPARATOR);
//         FullyDynamicArborescence seqAlgorithm = new FullyDynamicArborescence(seqGraph, seqRoots, seqTarjan);
//         seqAlgorithm.inferPhylogeny(seqGraph);
//         seqAlgorithm.addEdge(optimal1);
//         seqAlgorithm.addEdge(optimal2);
//         int seqCost = costOf(seqAlgorithm.getCurrentArborescence());

//         Assert.assertEquals("Batch add should produce same optimal cost as sequential add",
//             seqCost, batchCost);
//     }

//     @Test
//     public void testBatchAddSingleEdge() {
//         insAlgorithm.inferPhylogeny(insGraph);
//         Edge optimal = new Edge(insNodes.get(2), insNodes.get(1), 1);
//         List<Edge> result = insAlgorithm.addEdges(List.of(optimal));

//         Assert.assertTrue("Single-element batch add should produce valid arborescence",
//             isValidArborescence(insAlgorithm.getGraph(), new Graph(result)));

//         int expectedCost = staticInferenceCost(insAlgorithm.getGraph());
//         Assert.assertEquals("Single-element batch add cost should match static inference",
//             expectedCost, costOf(result));
//     }

//     @Test
//     public void testBatchAddEmptyList() {
//         insAlgorithm.inferPhylogeny(insGraph);
//         List<Edge> before = new ArrayList<>(insAlgorithm.getCurrentArborescence());
//         List<Edge> result = insAlgorithm.addEdges(new ArrayList<>());
//         Assert.assertEquals("Empty batch add should leave arborescence unchanged", costOf(before), costOf(result));
//     }

//     // -------------------------------------------------------------------------
//     // Helper methods
//     // -------------------------------------------------------------------------

//     private int costOf(List<Edge> edges) {
//         return edges.stream().mapToInt(Edge::getWeight).sum();
//     }

//     private Graph graphMinus(Graph g, List<Edge> toRemove) {
//         List<Edge> remaining = g.getEdges().stream()
//             .filter(e -> !toRemove.contains(e))
//             .collect(Collectors.toList());
//         return new Graph(remaining);
//     }

//     private int staticInferenceCost(Graph g) {
//         CameriniForest camerini = new CameriniForest(g, EDGE_COMPARATOR);
//         Graph result = camerini.inferPhylogeny(g);
//         return costOf(result.getEdges());
//     }

//     private boolean isValidArborescence(Graph graph, Graph arborescence) {
//         if (arborescence.getNumNodes() != graph.getNumNodes() ||
//             arborescence.getNumEdges() != graph.getNumNodes() - 1) {
//             return false;
//         }

//         Map<Integer, Node> incidentNodes = new HashMap<>();
//         for (Edge edge : arborescence.getEdges()) {
//             if (!graph.getEdges().contains(edge)) {
//                 return false;
//             }
//             Node dest = edge.getDestination();
//             if (incidentNodes.containsKey(dest.getId())) {
//                 return false;
//             }
//             incidentNodes.put(dest.getId(), dest);
//         }

//         List<Node> allNodes = new ArrayList<>(graph.getNodes());
//         for (Node node : incidentNodes.values()) {
//             allNodes.remove(node);
//         }
//         if (allNodes.size() != 1) {
//             return false;
//         }
//         Node root = allNodes.get(0);

//         return bfs(arborescence, root);
//     }

//     private boolean bfs(Graph graph, Node start) {
//         List<Node> visited = new ArrayList<>();
//         List<Node> queue = new ArrayList<>();
//         queue.add(start);

//         while (!queue.isEmpty()) {
//             Node current = queue.remove(0);
//             visited.add(current);
//             for (Edge edge : graph.getEdges()) {
//                 if (edge.getSource().equals(current)) {
//                     Node neighbor = edge.getDestination();
//                     if (!visited.contains(neighbor) && !queue.contains(neighbor)) {
//                         queue.add(neighbor);
//                     }
//                 }
//             }
//         }

//         return visited.size() == graph.getNumNodes();
//     }
// }
