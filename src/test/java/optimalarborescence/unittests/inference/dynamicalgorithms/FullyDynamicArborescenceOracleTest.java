// package optimalarborescence.unittests.inference.dynamicalgorithms;

// import optimalarborescence.graph.Edge;
// import optimalarborescence.graph.Node;
// import optimalarborescence.graph.Graph;
// import optimalarborescence.inference.CameriniForest;
// import optimalarborescence.inference.dynamic.FullyDynamicArborescence;
// import optimalarborescence.inference.dynamic.ATreeNode;
// import optimalarborescence.inference.dynamic.DynamicTarjanArborescence;

// import java.util.*;
// import java.util.stream.Collectors;
// import java.util.stream.IntStream;

// import org.junit.Test;
// import static org.junit.Assert.*;

// /**
//  * Oracle-based randomized tests for FullyDynamicArborescence.
//  *
//  * Strategy: after every add/remove operation, the dynamic algorithm's result must
//  * have the same total weight as CameriniForest run from scratch on the same graph.
//  * This is the fundamental correctness criterion — no knowledge of the algorithm's
//  * internals is required to interpret a failure.
//  *
//  * Each test method exercises one random seed. Different seeds produce different
//  * graph topologies and operation sequences, covering a wide range of algorithm paths.
//  *
//  * How to read a failure:
//  *   "seed=5 op=12 weight mismatch: expected 7 but was 9"
//  *   → at operation 12 (0-indexed) with seed 5, the dynamic answer was suboptimal.
//  *   Reproduce by running just that seed and adding System.out.println in runOracleTest.
//  */
// public class FullyDynamicArborescenceOracleTest {

//     private static final Comparator<Edge> EDGE_COMPARATOR =
//         (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());

//     // -----------------------------------------------------------------------
//     // Helpers
//     // -----------------------------------------------------------------------

//     /** Run CameriniForest from scratch and return its arborescence weight. */
//     private int staticMWAWeight(Graph g) {
//         CameriniForest oracle = new CameriniForest(g, EDGE_COMPARATOR);
//         Graph result = oracle.inferPhylogeny(g);
//         return result.getEdges().stream().mapToInt(Edge::getWeight).sum();
//     }

//     /**
//      * Check that {@code arborescence} is a valid spanning arborescence of {@code graph}:
//      * - exactly numNodes-1 edges
//      * - each non-root node has exactly one incoming arborescence edge
//      * - exactly one root (no incoming arborescence edge)
//      * - root can reach all nodes via BFS on arborescence edges
//      */
//     private boolean isValidArborescence(List<Edge> arborescence, Graph graph) {
//         int n = graph.getNodes().size();
//         if (arborescence.size() != n - 1) return false;

//         // Build in-degree map; fail immediately on a node with two incoming edges
//         Map<Integer, Integer> parent = new HashMap<>();
//         for (Edge e : arborescence) {
//             int dst = e.getDestination().getId();
//             if (parent.containsKey(dst)) return false;
//             parent.put(dst, e.getSource().getId());
//         }

//         // Exactly one root
//         Set<Integer> nodeIds = graph.getNodes().stream()
//             .map(Node::getId).collect(Collectors.toSet());
//         List<Integer> roots = nodeIds.stream()
//             .filter(id -> !parent.containsKey(id))
//             .collect(Collectors.toList());
//         if (roots.size() != 1) return false;
//         int root = roots.get(0);

//         // BFS reachability from root using arborescence edges
//         Map<Integer, List<Integer>> children = new HashMap<>();
//         for (Edge e : arborescence) {
//             children.computeIfAbsent(e.getSource().getId(), k -> new ArrayList<>())
//                     .add(e.getDestination().getId());
//         }
//         Set<Integer> visited = new HashSet<>();
//         Queue<Integer> queue = new LinkedList<>();
//         queue.add(root);
//         while (!queue.isEmpty()) {
//             int cur = queue.poll();
//             if (!visited.add(cur)) continue;
//             for (int child : children.getOrDefault(cur, Collections.emptyList())) {
//                 queue.add(child);
//             }
//         }
//         return visited.equals(nodeIds);
//     }

//     /** Build a fresh FullyDynamicArborescence wrapping the given graph. */
//     private FullyDynamicArborescence newDynamic(Graph g) {
//         List<ATreeNode> roots = new ArrayList<>();
//         DynamicTarjanArborescence tarjan = new DynamicTarjanArborescence(
//             roots, new ArrayList<>(), new HashMap<>(), g, EDGE_COMPARATOR);
//         return new FullyDynamicArborescence(g, roots, tarjan);
//     }

//     // -----------------------------------------------------------------------
//     // Oracle test driver
//     // -----------------------------------------------------------------------

//     /**
//      * Core oracle loop.
//      *
//      * @param seed     RNG seed — deterministic and reproducible
//      * @param numNodes number of graph vertices
//      * @param numOps   number of random add/remove operations to apply
//      */
//     private void runOracleTest(long seed, int numNodes, int numOps) {
//         Random rng = new Random(seed);
//         List<Node> nodes = IntStream.range(0, numNodes)
//             .mapToObj(Node::new)
//             .collect(Collectors.toList());

//         // --- Build initial graph ---
//         // Random edges (~50% density) with random positive integer weights
//         List<Edge> initialEdges = new ArrayList<>();
//         for (int i = 0; i < numNodes; i++) {
//             for (int j = 0; j < numNodes; j++) {
//                 if (i != j && rng.nextBoolean()) {
//                     initialEdges.add(new Edge(nodes.get(i), nodes.get(j), rng.nextInt(20) + 1));
//                 }
//             }
//         }
//         // Guarantee at least one incoming edge per non-root node so the first
//         // inference can always produce a spanning arborescence.
//         for (int j = 1; j < numNodes; j++) {
//             final int dst = j;
//             boolean hasIncoming = initialEdges.stream()
//                 .anyMatch(e -> e.getDestination().getId() == dst);
//             if (!hasIncoming) {
//                 initialEdges.add(new Edge(nodes.get(0), nodes.get(j), rng.nextInt(20) + 1));
//             }
//         }

//         Graph g = new Graph(new ArrayList<>(initialEdges));
//         FullyDynamicArborescence dynamic = newDynamic(g);
//         dynamic.inferPhylogeny(g);

//         // --- Initial assertion ---
//         // dynamic.getGraph() is the same object as g; both are updated in-place.
//         assertWeightMatchesOracle(seed, -1, dynamic, g);

//         // --- Operation loop ---
//         for (int op = 0; op < numOps; op++) {
//             List<Edge> currentEdges = new ArrayList<>(g.getEdges());

//             // Flip a coin: remove an existing edge, or add a new one.
//             // Only remove if the graph would still have enough edges to span.
//             boolean doRemove = rng.nextBoolean() && currentEdges.size() > numNodes;

//             if (doRemove) {
//                 Edge toRemove = currentEdges.get(rng.nextInt(currentEdges.size()));
//                 dynamic.removeEdge(toRemove);
//                 // g is updated in-place by removeEdge via getGraph().removeEdge(edge)
//             } else {
//                 // Pick a random directed pair that does not already have an edge
//                 int srcId = rng.nextInt(numNodes);
//                 int dstId = rng.nextInt(numNodes);
//                 if (srcId == dstId) continue;
//                 final int s = srcId, d = dstId;
//                 boolean exists = g.getEdges().stream().anyMatch(
//                     e -> e.getSource().getId() == s && e.getDestination().getId() == d);
//                 if (exists) continue;
//                 Edge toAdd = new Edge(nodes.get(srcId), nodes.get(dstId), rng.nextInt(20) + 1);
//                 dynamic.addEdge(toAdd);
//                 // g is updated in-place by addEdge via getGraph().addEdge(edge)
//             }

//             // Only assert when a spanning arborescence can plausibly exist:
//             // every non-root node must have at least one incoming edge.
//             boolean arbPossible = nodes.stream()
//                 .filter(n -> g.getEdges().stream()
//                     .noneMatch(e -> e.getDestination().getId() == n.getId()))
//                 .count() <= 1;
//             if (!arbPossible) continue;

//             assertWeightMatchesOracle(seed, op, dynamic, g);
//         }
//     }

//     /**
//      * Compare dynamic result against a fresh static run and check structural validity.
//      * Skips the comparison when the dynamic result isn't a full spanning arborescence
//      * (e.g., after a removal left the graph in a state where spanning is impossible).
//      */
//     private void assertWeightMatchesOracle(long seed, int op, FullyDynamicArborescence dynamic, Graph g) {
//         List<Edge> dynArb = dynamic.getCurrentArborescence();
//         int n = g.getNodes().size();

//         // If dynamic returned a partial result skip — both algorithms should fail similarly
//         if (dynArb.size() != n - 1) return;

//         String ctx = "seed=" + seed + (op < 0 ? " initial" : " op=" + op);

//         assertTrue(ctx + ": invalid arborescence structure",
//             isValidArborescence(dynArb, g));

//         int dynWeight  = dynArb.stream().mapToInt(Edge::getWeight).sum();
//         int oraWeight  = staticMWAWeight(g);
//         assertEquals(ctx + ": dynamic weight differs from static oracle", oraWeight, dynWeight);
//     }

//     // -----------------------------------------------------------------------
//     // Test cases — one per seed, increasing graph size and operation count
//     // -----------------------------------------------------------------------

//     // 5-node graphs, 30 operations
//     @Test public void oracleSeed0()  { runOracleTest(0,  5, 30); }
//     @Test public void oracleSeed1()  { runOracleTest(1,  5, 30); }
//     @Test public void oracleSeed2()  { runOracleTest(2,  5, 30); }
//     @Test public void oracleSeed3()  { runOracleTest(3,  5, 30); }
//     @Test public void oracleSeed4()  { runOracleTest(4,  5, 30); }

//     // 6-node graphs, 40 operations — more likely to produce cycle contractions
//     @Test public void oracleSeed5()  { runOracleTest(5,  6, 40); }
//     @Test public void oracleSeed6()  { runOracleTest(6,  6, 40); }
//     @Test public void oracleSeed7()  { runOracleTest(7,  6, 40); }
//     @Test public void oracleSeed8()  { runOracleTest(8,  6, 40); }
//     @Test public void oracleSeed9()  { runOracleTest(9,  6, 40); }

//     // 7-node graphs, 50 operations
//     @Test public void oracleSeed10() { runOracleTest(10, 7, 50); }
//     @Test public void oracleSeed11() { runOracleTest(11, 7, 50); }
//     @Test public void oracleSeed12() { runOracleTest(12, 7, 50); }
//     @Test public void oracleSeed42() { runOracleTest(42, 7, 50); }
//     @Test public void oracleSeed99() { runOracleTest(99, 7, 50); }

//     // 8-node graphs, 60 operations — stresses integrateOldSubtrees and multi-level c-nodes
//     @Test public void oracleSeed100() { runOracleTest(100, 8, 60); }
//     @Test public void oracleSeed200() { runOracleTest(200, 8, 60); }
//     @Test public void oracleSeed300() { runOracleTest(300, 8, 60); }
//     @Test public void oracleSeed400() { runOracleTest(400, 8, 60); }
//     @Test public void oracleSeed500() { runOracleTest(500, 8, 60); }
// }
