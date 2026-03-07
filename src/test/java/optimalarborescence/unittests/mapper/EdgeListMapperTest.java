package optimalarborescence.unittests.mapper;

import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.sequences.AllelicProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * Unit tests for EdgeListMapper class.
 */
public class EdgeListMapperTest {

    private static final String TEST_BASE_NAME = "test_edge_mapper";
    private static final String TEST_NODES_FILE = TEST_BASE_NAME + "_nodes.dat";
    private static final String TEST_EDGES_FILE = TEST_BASE_NAME + "_edges.dat";
    private static final int MLST_LENGTH = 7;
    
    private List<Node> testNodes;
    private List<Edge> testEdges;

    private static AllelicProfile createProfile(String data) {
        Character[] chars = new Character[data.length()];
        for (int i = 0; i < data.length(); i++) {
            chars[i] = data.charAt(i);
        }
        return new AllelicProfile(chars, data.length());
    }

    @Before
    public void setup() throws IOException {
        // Create test nodes
        testNodes = new ArrayList<>();
        testNodes.add(new Node(createProfile("AAAAAAA"), 0));
        testNodes.add(new Node(createProfile("CCCCCCC"), 1));
        testNodes.add(new Node(createProfile("GGGGGGG"), 2));
        testNodes.add(new Node(createProfile("TTTTTTT"), 3));
        testNodes.add(new Node(createProfile("ACGTACG"), 4));

        // Save nodes to file (needed for some operations)
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_NODES_FILE);

        // Create test edges
        testEdges = new ArrayList<>();
        testEdges.add(new Edge(testNodes.get(0), testNodes.get(1), 10)); // 0->1
        testEdges.add(new Edge(testNodes.get(0), testNodes.get(2), 20)); // 0->2
        testEdges.add(new Edge(testNodes.get(1), testNodes.get(2), 15)); // 1->2
        testEdges.add(new Edge(testNodes.get(2), testNodes.get(3), 25)); // 2->3
        testEdges.add(new Edge(testNodes.get(3), testNodes.get(4), 30)); // 3->4
    }

    @After
    public void cleanup() {
        // Clean up test files
        try {
            Files.deleteIfExists(Path.of(TEST_NODES_FILE));
            
            // Clean up per-node edge files
            for (int i = 0; i < 10; i++) {
                Files.deleteIfExists(Path.of(TEST_BASE_NAME + "_edges_node" + i + ".dat"));
            }
        } catch (IOException e) {
            System.err.println("Failed to clean up test files: " + e.getMessage());
        }
    }

    @Test
    public void testWriteAndLoadEdgeArray() {
        String testFile = TEST_BASE_NAME + "_edges_node1.dat";
        
        // Write edges to file
        EdgeListMapper.writeEdgeArray(testFile, testEdges);

        // Verify file was created
        assertTrue("File should exist", new File(testFile).exists());

        // Load edges back
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);

        // Verify count
        assertEquals("Should load same number of edges", testEdges.size(), loadedEdges.size());

        // Verify each edge
        for (int i = 0; i < testEdges.size(); i++) {
            Edge original = testEdges.get(i);
            Edge loaded = loadedEdges.get(i);

            assertEquals("Source ID should match", original.getSource().getId(), loaded.getSource().getId());
            assertEquals("Destination ID should match", original.getDestination().getId(), loaded.getDestination().getId());
            assertEquals("Weight should match", original.getWeight(), loaded.getWeight());
        }
    }

    @Test
    public void testWriteEmptyEdgeArray() {
        String testFile = TEST_BASE_NAME + "_edges_node0.dat";
        
        // Write empty list
        List<Edge> emptyList = new ArrayList<>();
        EdgeListMapper.writeEdgeArray(testFile, emptyList);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have no edges", 0, loadedEdges.size());
    }

    @Test
    public void testGetNumEdges() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node1.dat";
        
        // Write edges
        EdgeListMapper.writeEdgeArray(testFile, testEdges);

        // Get count
        long count = EdgeListMapper.getNumEdges(testFile);
        assertEquals("Edge count should match", testEdges.size(), count);
    }

    @Test
    public void testAddEdge() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Create initial edges for node 2
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(testEdges.get(1)); // 0->2
        initialEdges.add(testEdges.get(2)); // 1->2
        EdgeListMapper.writeEdgeArray(testFile, initialEdges);

        // Add a new edge to node 2
        Edge newEdge = new Edge(testNodes.get(4), testNodes.get(2), 35); // 4->2
        EdgeListMapper.addEdge(newEdge, TEST_EDGES_FILE);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have one more edge", initialEdges.size() + 1, loadedEdges.size());

        // Verify the new edge is present
        Edge lastEdge = loadedEdges.get(loadedEdges.size() - 1);
        assertEquals("New edge source should match", 4, lastEdge.getSource().getId());
        assertEquals("New edge destination should match", 2, lastEdge.getDestination().getId());
        assertEquals("New edge weight should match", 35, lastEdge.getWeight());
    }

    @Test
    public void testAddEdgesToNode() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Create initial file
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(testEdges.get(1)); // 0->2
        EdgeListMapper.writeEdgeArray(testFile, initialEdges);

        // Add multiple edges to node 2
        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(testEdges.get(2)); // 1->2
        newEdges.add(new Edge(testNodes.get(4), testNodes.get(2), 35)); // 4->2
        
        EdgeListMapper.addEdges(newEdges, testNodes.get(2), TEST_EDGES_FILE);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have all edges", initialEdges.size() + newEdges.size(), loadedEdges.size());
    }

    @Test
    public void testAddEdgesEmpty() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node1.dat";
        
        // Create initial file
        EdgeListMapper.writeEdgeArray(testFile, testEdges);

        // Add empty list (should be no-op)
        EdgeListMapper.addEdges(new ArrayList<>(), testNodes.get(1), TEST_EDGES_FILE);

        // Verify no change
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Edge count should be unchanged", testEdges.size(), loadedEdges.size());
    }

    @Test
    public void testAddEdgesBatch() throws IOException {
        // Create map of nodes to their incoming edges
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        
        List<Edge> edgesForNode1 = new ArrayList<>();
        edgesForNode1.add(testEdges.get(0)); // 0->1
        nodeEdgesMap.put(testNodes.get(1), edgesForNode1);
        
        List<Edge> edgesForNode2 = new ArrayList<>();
        edgesForNode2.add(testEdges.get(1)); // 0->2
        edgesForNode2.add(testEdges.get(2)); // 1->2
        nodeEdgesMap.put(testNodes.get(2), edgesForNode2);

        // Add edges in batch
        EdgeListMapper.addEdgesBatch(nodeEdgesMap, TEST_EDGES_FILE);

        // Verify node 1 edges
        String file1 = TEST_BASE_NAME + "_edges_node1.dat";
        List<Edge> node1Edges = EdgeListMapper.loadEdgeArray(file1);
        assertEquals("Node 1 should have correct edges", 1, node1Edges.size());

        // Verify node 2 edges
        String file2 = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> node2Edges = EdgeListMapper.loadEdgeArray(file2);
        assertEquals("Node 2 should have correct edges", 2, node2Edges.size());
    }

    @Test
    public void testAddEdgesToExistingNodes() throws IOException {
        // Create initial edges for node 2
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(testEdges.get(1)); // 0->2
        EdgeListMapper.writeEdgeArray(testFile, initialEdges);

        // Add more edges to existing node
        Map<Node, List<Edge>> newEdgesMap = new HashMap<>();
        List<Edge> additionalEdges = new ArrayList<>();
        additionalEdges.add(testEdges.get(2)); // 1->2
        newEdgesMap.put(testNodes.get(2), additionalEdges);

        EdgeListMapper.addEdgesToExistingNodes(newEdgesMap, TEST_NODES_FILE, TEST_EDGES_FILE);

        // Verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have combined edges", initialEdges.size() + additionalEdges.size(), loadedEdges.size());
    }

    @Test
    public void testEdgeExists() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Create edges for node 2
        List<Edge> edgesForNode2 = new ArrayList<>();
        edgesForNode2.add(testEdges.get(1)); // 0->2
        edgesForNode2.add(testEdges.get(2)); // 1->2
        EdgeListMapper.writeEdgeArray(testFile, edgesForNode2);

        // Test existing edge
        assertTrue("Edge 0->2 should exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 0, 2));
        assertTrue("Edge 1->2 should exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 1, 2));

        // Test non-existing edge
        assertFalse("Edge 3->2 should not exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 3, 2));
    }

    @Test
    public void testRemoveEdge() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Create edges for node 2
        List<Edge> edgesForNode2 = new ArrayList<>();
        edgesForNode2.add(testEdges.get(1)); // 0->2
        edgesForNode2.add(testEdges.get(2)); // 1->2
        EdgeListMapper.writeEdgeArray(testFile, edgesForNode2);

        // Remove one edge
        EdgeListMapper.removeEdge(TEST_EDGES_FILE, 0, 2);

        // Verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have one less edge", 1, loadedEdges.size());
        assertFalse("Edge 0->2 should not exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 0, 2));
        assertTrue("Edge 1->2 should still exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 1, 2));
    }

    @Test
    public void testRemoveLastEdge() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Create single edge
        List<Edge> singleEdge = new ArrayList<>();
        singleEdge.add(testEdges.get(1)); // 0->2
        EdgeListMapper.writeEdgeArray(testFile, singleEdge);

        // Remove the edge
        EdgeListMapper.removeEdge(TEST_EDGES_FILE, 0, 2);

        // Verify file is empty
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have no edges", 0, loadedEdges.size());
    }

    @Test
    public void testRemoveEdges() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Create edges for node 2
        List<Edge> edgesForNode2 = new ArrayList<>();
        edgesForNode2.add(testEdges.get(1)); // 0->2
        edgesForNode2.add(testEdges.get(2)); // 1->2
        EdgeListMapper.writeEdgeArray(testFile, edgesForNode2);

        // Remove all edges for node 2 (by deleting the file)
        EdgeListMapper.removeEdges(TEST_EDGES_FILE, 2);

        // Verify file is deleted
        assertFalse("File should be deleted", new File(testFile).exists());
    }

    @Test
    public void testRemoveEdgesBatch() throws IOException {
        // Create edges for multiple nodes
        String file1 = TEST_BASE_NAME + "_edges_node1.dat";
        List<Edge> edgesNode1 = new ArrayList<>();
        edgesNode1.add(testEdges.get(0)); // 0->1
        EdgeListMapper.writeEdgeArray(file1, edgesNode1);

        String file2 = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edgesNode2 = new ArrayList<>();
        edgesNode2.add(testEdges.get(1)); // 0->2
        EdgeListMapper.writeEdgeArray(file2, edgesNode2);

        String file3 = TEST_BASE_NAME + "_edges_node3.dat";
        List<Edge> edgesNode3 = new ArrayList<>();
        edgesNode3.add(testEdges.get(3)); // 2->3
        EdgeListMapper.writeEdgeArray(file3, edgesNode3);

        // Remove edges for nodes 1 and 3
        Set<Integer> nodesToRemove = new HashSet<>();
        nodesToRemove.add(1);
        nodesToRemove.add(3);
        
        EdgeListMapper.removeEdgesBatch(nodesToRemove, TEST_EDGES_FILE);

        // Verify
        assertFalse("Node 1 file should be deleted", new File(file1).exists());
        assertTrue("Node 2 file should still exist", new File(file2).exists());
        assertFalse("Node 3 file should be deleted", new File(file3).exists());
    }

    @Test
    public void testRemoveEdgesBatchEmpty() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node1.dat";
        EdgeListMapper.writeEdgeArray(testFile, testEdges);

        // Remove empty set (should be no-op)
        EdgeListMapper.removeEdgesBatch(new HashSet<>(), TEST_EDGES_FILE);

        // Verify file still exists
        assertTrue("File should still exist", new File(testFile).exists());
    }

    @Test
    public void testRemoveOutgoingEdges() throws IOException {
        // Create edges: 0->1, 0->2, 1->2
        String file1 = TEST_BASE_NAME + "_edges_node1.dat";
        List<Edge> edgesNode1 = new ArrayList<>();
        edgesNode1.add(testEdges.get(0)); // 0->1
        EdgeListMapper.writeEdgeArray(file1, edgesNode1);

        String file2 = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edgesNode2 = new ArrayList<>();
        edgesNode2.add(testEdges.get(1)); // 0->2
        edgesNode2.add(testEdges.get(2)); // 1->2
        EdgeListMapper.writeEdgeArray(file2, edgesNode2);

        // Remove all outgoing edges from node 0
        EdgeListMapper.removeOutgoingEdges(TEST_EDGES_FILE, 0);

        // Verify node 0->1 is removed
        List<Edge> node1Edges = EdgeListMapper.loadEdgeArray(file1);
        assertEquals("Node 1 should have no edges from node 0", 0, node1Edges.size());

        // Verify node 0->2 is removed but 1->2 remains
        List<Edge> node2Edges = EdgeListMapper.loadEdgeArray(file2);
        assertEquals("Node 2 should have one edge", 1, node2Edges.size());
        assertEquals("Remaining edge should be from node 1", 1, node2Edges.get(0).getSource().getId());
    }

    @Test
    public void testGetOutgoingEdges() throws IOException {
        // Create edges: 0->1, 0->2, 1->2
        // Need to create edge files for ALL nodes in the node map, even if empty
        for (int i = 0; i < testNodes.size(); i++) {
            String file = TEST_BASE_NAME + "_edges_node" + i + ".dat";
            EdgeListMapper.writeEdgeArray(file, new ArrayList<>());
        }
        
        String file1 = TEST_BASE_NAME + "_edges_node1.dat";
        List<Edge> edgesNode1 = new ArrayList<>();
        edgesNode1.add(testEdges.get(0)); // 0->1
        EdgeListMapper.writeEdgeArray(file1, edgesNode1);

        String file2 = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edgesNode2 = new ArrayList<>();
        edgesNode2.add(testEdges.get(1)); // 0->2
        edgesNode2.add(testEdges.get(2)); // 1->2
        EdgeListMapper.writeEdgeArray(file2, edgesNode2);

        // Get outgoing edges from node 0
        List<Edge> outgoingEdges = EdgeListMapper.getOutgoingEdges(TEST_EDGES_FILE, 0);

        // Verify
        assertEquals("Node 0 should have 2 outgoing edges", 2, outgoingEdges.size());
        
        // Check that both edges have source = 0
        for (Edge edge : outgoingEdges) {
            assertEquals("All edges should be from node 0", 0, edge.getSource().getId());
        }
    }

    @Test
    public void testGetOutgoingEdgesNone() throws IOException {
        // Create edge files for all nodes (getOutgoingEdges expects them to exist)
        for (int i = 0; i < testNodes.size(); i++) {
            String file = TEST_BASE_NAME + "_edges_node" + i + ".dat";
            EdgeListMapper.writeEdgeArray(file, new ArrayList<>());
        }
        
        // Create edges but none from node 4
        String file1 = TEST_BASE_NAME + "_edges_node1.dat";
        List<Edge> edgesNode1 = new ArrayList<>();
        edgesNode1.add(testEdges.get(0)); // 0->1
        EdgeListMapper.writeEdgeArray(file1, edgesNode1);

        // Get outgoing edges from node 4 (none exist)
        List<Edge> outgoingEdges = EdgeListMapper.getOutgoingEdges(TEST_EDGES_FILE, 4);

        // Verify
        assertEquals("Node 4 should have no outgoing edges", 0, outgoingEdges.size());
    }

    @Test
    public void testLargeEdgeSet() {
        String testFile = TEST_BASE_NAME + "_edges_node1.dat";
        
        // Create a large set of edges
        List<Edge> largeSet = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            largeSet.add(new Edge(testNodes.get(0), testNodes.get(1), i));
        }

        // Write and load
        EdgeListMapper.writeEdgeArray(testFile, largeSet);
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);

        // Verify
        assertEquals("Should load all edges", largeSet.size(), loadedEdges.size());
        
        // Spot check a few edges
        assertEquals("First edge weight should match", 0, loadedEdges.get(0).getWeight());
        assertEquals("Middle edge weight should match", 5000, loadedEdges.get(5000).getWeight());
        assertEquals("Last edge weight should match", 9999, loadedEdges.get(9999).getWeight());
    }

    @Test
    public void testMultipleOperations() throws IOException {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Write initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(testEdges.get(1)); // 0->2
        EdgeListMapper.writeEdgeArray(testFile, initialEdges);

        // Add an edge
        Edge newEdge = new Edge(testNodes.get(1), testNodes.get(2), 15);
        EdgeListMapper.addEdge(newEdge, TEST_EDGES_FILE);

        // Verify we have 2 edges now
        List<Edge> afterAdd = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have 2 edges after add", 2, afterAdd.size());

        // Remove an edge
        EdgeListMapper.removeEdge(TEST_EDGES_FILE, 0, 2);

        // Verify we have 1 edge now
        List<Edge> afterRemove = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have 1 edge after remove", 1, afterRemove.size());

        // Add another edge
        Edge anotherEdge = new Edge(testNodes.get(3), testNodes.get(2), 25);
        EdgeListMapper.addEdge(anotherEdge, TEST_EDGES_FILE);

        // Verify final state
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);
        assertEquals("Should have 2 edges at end", 2, loadedEdges.size());
        assertFalse("Edge 0->2 should not exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 0, 2));
        assertTrue("Edge 1->2 should exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 1, 2));
        assertTrue("Edge 3->2 should exist", EdgeListMapper.edgeExists(TEST_EDGES_FILE, 3, 2));
    }

    // -----------------------------------------------------------------------
    // findMinSafeEdgeInFile tests
    // -----------------------------------------------------------------------

    private static final Comparator<int[]> NATURAL_CMP = Comparator.comparingInt(e -> e[0]);

    /** Reflectively invokes the private findMinSafeEdgeInFile method. */
    private Edge invokeFind(String filename, int targetId, UnionFindStronglyConnected uf) throws Exception {
        Method method = EdgeListMapper.class.getDeclaredMethod(
                "findMinSafeEdgeInFile", String.class, int.class, UnionFindStronglyConnected.class, Comparator.class);
        method.setAccessible(true);
        return (Edge) method.invoke(null, filename, targetId, uf, NATURAL_CMP);
    }

    @Test
    public void testFindMinSafeEdge_EmptyFile_ReturnsNull() throws Exception {
        // Edge file for node 2 exists but contains zero edges.
        String nodeFile = TEST_BASE_NAME + "_edges_node2.dat";
        EdgeListMapper.writeEdgeArray(nodeFile, new ArrayList<>());

        UnionFindStronglyConnected uf = new UnionFindStronglyConnected(5);

        Edge result = invokeFind(TEST_EDGES_FILE, 2, uf);
        assertNull("Empty file should return null", result);
    }

    @Test
    public void testFindMinSafeEdge_AllEdgesInSameSCC_ReturnsNull() throws Exception {
        // Edges 0->2 and 1->2 both have their source merged into node 2's SCC.
        String nodeFile = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(testNodes.get(0), testNodes.get(2), 10)); // 0->2
        edges.add(new Edge(testNodes.get(1), testNodes.get(2), 20)); // 1->2
        EdgeListMapper.writeEdgeArray(nodeFile, edges);

        UnionFindStronglyConnected uf = new UnionFindStronglyConnected(5);
        uf.union(0, 2); // source 0 and target 2 in same component
        uf.union(1, 2); // source 1 and target 2 in same component

        Edge result = invokeFind(TEST_EDGES_FILE, 2, uf);
        assertNull("All edges in same SCC should return null", result);
    }

    @Test
    public void testFindMinSafeEdge_AllEdgesInDifferentSCCs_ReturnsMin() throws Exception {
        // Default UF: every node is its own component, so all edges are "safe".
        String nodeFile = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(testNodes.get(0), testNodes.get(2), 30)); // 0->2, weight 30
        edges.add(new Edge(testNodes.get(1), testNodes.get(2), 10)); // 1->2, weight 10
        edges.add(new Edge(testNodes.get(3), testNodes.get(2), 20)); // 3->2, weight 20
        EdgeListMapper.writeEdgeArray(nodeFile, edges);

        UnionFindStronglyConnected uf = new UnionFindStronglyConnected(5);

        Edge result = invokeFind(TEST_EDGES_FILE, 2, uf);
        assertNotNull("Should find a minimum edge", result);
        assertEquals("Source should be node 1", 1, result.getSource().getId());
        assertEquals("Destination should be node 2", 2, result.getDestination().getId());
        assertEquals("Weight should be 10", 10, result.getWeight());
    }

    @Test
    public void testFindMinSafeEdge_MixedSCCs_ReturnsMinAmongValid() throws Exception {
        // 0 and 2 are in the same SCC; 1 and 3 are separate.
        // Edges: 0->2 (weight 5, invalid), 1->2 (weight 15, valid), 3->2 (weight 8, valid).
        String nodeFile = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(testNodes.get(0), testNodes.get(2), 5));  // same SCC – must be ignored
        edges.add(new Edge(testNodes.get(1), testNodes.get(2), 15)); // valid
        edges.add(new Edge(testNodes.get(3), testNodes.get(2), 8));  // valid, lower weight
        EdgeListMapper.writeEdgeArray(nodeFile, edges);

        UnionFindStronglyConnected uf = new UnionFindStronglyConnected(5);
        uf.union(0, 2); // merge source 0 with target 2

        Edge result = invokeFind(TEST_EDGES_FILE, 2, uf);
        assertNotNull("Should find a minimum edge among valid ones", result);
        assertEquals("Source should be node 3", 3, result.getSource().getId());
        assertEquals("Weight should be 8", 8, result.getWeight());
    }

    @Test
    public void testFindMinSafeEdge_SingleValidEdge_ReturnsThatEdge() throws Exception {
        // Only one edge crosses SCC boundaries.
        String nodeFile = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(testNodes.get(0), testNodes.get(2), 50)); // same SCC – ignored
        edges.add(new Edge(testNodes.get(1), testNodes.get(2), 25)); // only valid edge
        EdgeListMapper.writeEdgeArray(nodeFile, edges);

        UnionFindStronglyConnected uf = new UnionFindStronglyConnected(5);
        uf.union(0, 2);

        Edge result = invokeFind(TEST_EDGES_FILE, 2, uf);
        assertNotNull("Should find the single valid edge", result);
        assertEquals("Source should be node 1", 1, result.getSource().getId());
        assertEquals("Weight should be 25", 25, result.getWeight());
    }

    @Test
    public void testFindMinSafeEdge_TieInWeight_ReturnsOneOfThem() throws Exception {
        // Two valid edges with the same weight; either is an acceptable answer.
        String nodeFile = TEST_BASE_NAME + "_edges_node2.dat";
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(testNodes.get(0), testNodes.get(2), 10)); // valid
        edges.add(new Edge(testNodes.get(1), testNodes.get(2), 10)); // valid, same weight
        EdgeListMapper.writeEdgeArray(nodeFile, edges);

        UnionFindStronglyConnected uf = new UnionFindStronglyConnected(5);

        Edge result = invokeFind(TEST_EDGES_FILE, 2, uf);
        assertNotNull("Should return an edge on weight tie", result);
        assertEquals("Weight should be 10", 10, result.getWeight());
        assertEquals("Destination should be node 2", 2, result.getDestination().getId());
    }

    @Test
    public void testEdgeWithSameSourceDest() {
        String testFile = TEST_BASE_NAME + "_edges_node1.dat";
        
        // Create self-loop edge
        List<Edge> selfLoop = new ArrayList<>();
        selfLoop.add(new Edge(testNodes.get(1), testNodes.get(1), 100));

        // Write and load
        EdgeListMapper.writeEdgeArray(testFile, selfLoop);
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);

        // Verify
        assertEquals("Should have one edge", 1, loadedEdges.size());
        assertEquals("Source should match", 1, loadedEdges.get(0).getSource().getId());
        assertEquals("Destination should match", 1, loadedEdges.get(0).getDestination().getId());
        assertEquals("Weight should match", 100, loadedEdges.get(0).getWeight());
    }

    @Test
    public void testParallelEdges() {
        String testFile = TEST_BASE_NAME + "_edges_node2.dat";
        
        // Create multiple edges with same source and destination but different weights
        List<Edge> parallelEdges = new ArrayList<>();
        parallelEdges.add(new Edge(testNodes.get(0), testNodes.get(2), 10));
        parallelEdges.add(new Edge(testNodes.get(0), testNodes.get(2), 20));
        parallelEdges.add(new Edge(testNodes.get(0), testNodes.get(2), 30));

        // Write and load
        EdgeListMapper.writeEdgeArray(testFile, parallelEdges);
        List<Edge> loadedEdges = EdgeListMapper.loadEdgeArray(testFile);

        // Verify
        assertEquals("Should have all parallel edges", 3, loadedEdges.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("Source should be 0", 0, loadedEdges.get(i).getSource().getId());
            assertEquals("Destination should be 2", 2, loadedEdges.get(i).getDestination().getId());
        }
    }
}
