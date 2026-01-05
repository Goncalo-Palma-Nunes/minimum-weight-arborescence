package optimalarborescence.unittests.mapper.graph;

import java.io.IOException;
import java.io.RandomAccessFile;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.graph.Graph;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.sequences.AllelicProfile;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Test;

public class NodeIndexMapperTest {

    private static final List<String> MLST_DATA = List.of(
        "AGTC", "AATT", "CGCG", "ATAT", "GTCA", "TATA", "CAGT", "GACT"
    );

    private static final String NODES_FILE_NAME = "test_nodeindex_mapper_nodes.dat";
    private static final String EDGES_FILE_NAME = "test_nodeindex_mapper_edges.dat";
    private static final List<Node> TEST_NODES = new ArrayList<>();
    static {
        for (int i = 0; i < MLST_DATA.size(); i++) {
            TEST_NODES.add(new Node(createProfile(MLST_DATA.get(i)), i));
        }
    }

    private static AllelicProfile createProfile(String data) {
        Character[] chars = new Character[data.length()];
        for (int i = 0; i < data.length(); i++) {
            chars[i] = data.charAt(i);
        }
        return new AllelicProfile(chars, data.length());
    }

    @Test
    public void testSaveAndLoadNodeIndexMapping() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(1), 30));
        originalEdges.add(new Edge(TEST_NODES.get(4), TEST_NODES.get(5), 40));
        originalEdges.add(new Edge(TEST_NODES.get(6), TEST_NODES.get(7), 50));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4; // Fixed MLST length for all nodes

        // Save graph to memory-mapped files
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Load nodes back from files
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals(TEST_NODES.size(), loadedNodes.size());
        for (Node originalNode : TEST_NODES) {
            Node loadedNode = loadedNodes.get(originalNode.getId());
            Assert.assertNotNull(loadedNode);
            Assert.assertEquals(originalNode.getId(), loadedNode.getId());
            Assert.assertEquals(originalNode.getMLSTdata(), loadedNode.getMLSTdata());
        }
    }

    @Test
    public void testSaveAndLoadNodeListWithNoNodes() throws IOException {
        Graph emptyGraph = new Graph(new ArrayList<>());
        int mlstLength = 4; // Fixed MLST length

        // Save empty graph
        NodeIndexMapper.saveGraph(emptyGraph, mlstLength, Map.of(), NODES_FILE_NAME);

        // Load nodes back
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertTrue(loadedNodes.isEmpty());
    }

    @Test
    public void testSaveAndLoadNodeList() throws IOException {
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 15));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 25));
        int mlstLength = 4; // Fixed MLST length
        List<Node> graphNodes = List.of(
            TEST_NODES.get(0),
            TEST_NODES.get(1),
            TEST_NODES.get(2),
            TEST_NODES.get(3)
        );

        // Save graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graphNodes, mlstLength, offsetMap, NODES_FILE_NAME);

        // Load nodes back
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals(graphNodes.size(), loadedNodes.size()); // Nodes with IDs 0,1,2,3

        for (int i = 0; i < graphNodes.size(); i++) {
            Node originalNode = graphNodes.get(i);
            Node loadedNode = loadedNodes.get(i);
            Assert.assertNotNull(loadedNode);
            Assert.assertEquals(originalNode.getId(), loadedNode.getId());
            Assert.assertEquals(originalNode.getMLSTdata(), loadedNode.getMLSTdata());
        }
    }

    @Test
    public void testGetIncomingEdgesOffset() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(1), 30));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4; // Fixed MLST length for all nodes

        // Save graph to memory-mapped files
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Test offsets
        for (Node node : graph.getNodes()) {
            long expectedOffset = offsetMap.getOrDefault(node.getId(), -1L);
            long loadedOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, node.getId());
            Assert.assertEquals(expectedOffset, loadedOffset);
        }
    }

    // ===== Tests for addNode method =====

    @Test
    public void testAddNodeToExistingFiles() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Add a new node with ID 3 (next sequential ID after 0,1,2)
        Node newNode = new Node(createProfile("GGGG"), 3);
        NodeIndexMapper.addNode(newNode, NODES_FILE_NAME, mlstLength);

        // Verify by loading the MLST data file directly and checking it was appended
        long expectedOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3);
        // New node should have offset -1 (no incoming edges initially)
        Assert.assertEquals(-1L, expectedOffset);
    }

    @Test
    public void testAddMultipleNodes() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Add multiple new nodes (sequential IDs after existing nodes 0,1)
        Node node1 = new Node(createProfile("TTTT"), 2);
        Node node2 = new Node(createProfile("CCCC"), 3);
        Node node3 = new Node(createProfile("AAAA"), 4);

        NodeIndexMapper.addNode(node1, NODES_FILE_NAME, mlstLength);
        NodeIndexMapper.addNode(node2, NODES_FILE_NAME, mlstLength);
        NodeIndexMapper.addNode(node3, NODES_FILE_NAME, mlstLength);

        // Verify each node was added by checking their offsets
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 4));
    }

    @Test
    public void testAddNodeWithDifferentMLSTLength() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 8; // Longer MLST length

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Add a new node with longer MLST data (ID 2, next after 0,1)
        Node newNode = new Node(createProfile("AGTCAGTC"), 2);
        NodeIndexMapper.addNode(newNode, NODES_FILE_NAME, mlstLength);

        // Verify the node was added with offset -1
        long offset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2);
        Assert.assertEquals(-1L, offset);
    }

    // ===== Tests for updateIncomingEdgeOffset method =====

    @Test
    public void testUpdateIncomingEdgeOffset() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get original offset
        long originalOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);

        // Update offset for node 1
        long newOffset = 9999L;
        NodeIndexMapper.updateIncomingEdgeOffset(NODES_FILE_NAME, 1, newOffset);

        // Verify the offset was updated
        long updatedOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        Assert.assertEquals(newOffset, updatedOffset);
        Assert.assertNotEquals(originalOffset, updatedOffset);
    }

    @Test
    public void testUpdateIncomingEdgeOffsetToNegative() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Update offset to -1 (indicating no incoming edges)
        NodeIndexMapper.updateIncomingEdgeOffset(NODES_FILE_NAME, 1, -1L);

        // Verify the offset is now -1
        long updatedOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        Assert.assertEquals(-1L, updatedOffset);
    }

    @Test
    public void testUpdateIncomingEdgeOffsetMultipleTimes() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Update offset multiple times
        NodeIndexMapper.updateIncomingEdgeOffset(NODES_FILE_NAME, 1, 1000L);
        Assert.assertEquals(1000L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));

        NodeIndexMapper.updateIncomingEdgeOffset(NODES_FILE_NAME, 1, 2000L);
        Assert.assertEquals(2000L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));

        NodeIndexMapper.updateIncomingEdgeOffset(NODES_FILE_NAME, 1, 3000L);
        Assert.assertEquals(3000L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));
    }

    @Test(expected = IOException.class)
    public void testUpdateIncomingEdgeOffsetOutOfRange() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Try to update offset for a node ID that doesn't exist
        NodeIndexMapper.updateIncomingEdgeOffset(NODES_FILE_NAME, 999, 5000L);
    }

    // ===== Tests for updateIncomingEdgeOffsets method =====

    @Test
    public void testUpdateIncomingEdgeOffsetsBulk() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Prepare new offsets for multiple nodes
        Map<Integer, Long> newOffsets = new java.util.HashMap<>();
        newOffsets.put(1, 1111L);
        newOffsets.put(2, 2222L);
        newOffsets.put(3, 3333L);

        // Update all offsets at once
        NodeIndexMapper.updateIncomingEdgeOffsets(NODES_FILE_NAME, newOffsets);

        // Verify all offsets were updated
        Assert.assertEquals(1111L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));
        Assert.assertEquals(2222L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2));
        Assert.assertEquals(3333L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3));
    }

    @Test
    public void testUpdateIncomingEdgeOffsetsPartial() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get original offsets
        long originalOffset1 = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        long originalOffset3 = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3);

        // Update only node 2
        Map<Integer, Long> newOffsets = new java.util.HashMap<>();
        newOffsets.put(2, 7777L);

        NodeIndexMapper.updateIncomingEdgeOffsets(NODES_FILE_NAME, newOffsets);

        // Verify only node 2 was updated, others remain unchanged
        Assert.assertEquals(originalOffset1, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));
        Assert.assertEquals(7777L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2));
        Assert.assertEquals(originalOffset3, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3));
    }

    @Test
    public void testUpdateIncomingEdgeOffsetsEmpty() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get original offset
        long originalOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);

        // Update with empty map (no changes should occur)
        Map<Integer, Long> emptyOffsets = new java.util.HashMap<>();
        NodeIndexMapper.updateIncomingEdgeOffsets(NODES_FILE_NAME, emptyOffsets);

        // Verify offset remains unchanged
        long unchangedOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        Assert.assertEquals(originalOffset, unchangedOffset);
    }

    @Test
    public void testUpdateIncomingEdgeOffsetsWithNegativeValues() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Update offsets with negative values (indicating no incoming edges)
        Map<Integer, Long> newOffsets = new java.util.HashMap<>();
        newOffsets.put(1, -1L);
        newOffsets.put(2, -1L);

        NodeIndexMapper.updateIncomingEdgeOffsets(NODES_FILE_NAME, newOffsets);

        // Verify offsets are now -1
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2));
    }

    @Test(expected = IOException.class)
    public void testUpdateIncomingEdgeOffsetsOutOfRange() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Try to update offset for a node ID that doesn't exist
        Map<Integer, Long> invalidOffsets = new java.util.HashMap<>();
        invalidOffsets.put(999, 5000L);

        NodeIndexMapper.updateIncomingEdgeOffsets(NODES_FILE_NAME, invalidOffsets);
    }

    @Test
    public void testUpdateIncomingEdgeOffsetsLargeValues() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Update with large offset values
        Map<Integer, Long> largeOffsets = new java.util.HashMap<>();
        largeOffsets.put(1, Long.MAX_VALUE - 1000);
        largeOffsets.put(2, Long.MAX_VALUE / 2);

        NodeIndexMapper.updateIncomingEdgeOffsets(NODES_FILE_NAME, largeOffsets);

        // Verify large offsets were stored correctly
        Assert.assertEquals(Long.MAX_VALUE - 1000, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));
        Assert.assertEquals(Long.MAX_VALUE / 2, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2));
    }

    // ===== Tests for removeNode method =====

    @Test
    public void testRemoveNodeMiddle() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph with nodes 0, 1, 2, 3
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get initial offsets
        long offset1Before = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        long offset3Before = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3);

        // Remove node 2 (middle node)
        Node nodeToRemove = TEST_NODES.get(2);
        NodeIndexMapper.removeNode(nodeToRemove, NODES_FILE_NAME);

        // Verify node count decreased
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("Node count should decrease by 1", 3, loadedNodes.size());

        // Verify node 2 is no longer in the file (node IDs are preserved, not position-based)
        Assert.assertFalse("Node 2 should not exist after removal", loadedNodes.containsKey(2));
        
        // Verify other nodes remain intact with their original IDs
        Assert.assertTrue("Node 0 should still exist", loadedNodes.containsKey(0));
        Assert.assertTrue("Node 1 should still exist", loadedNodes.containsKey(1));
        Assert.assertTrue("Node 3 should still exist", loadedNodes.containsKey(3));
        
        long offset1After = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        long offset3After = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3);
        Assert.assertEquals("Node 1 offset unchanged", offset1Before, offset1After);
        Assert.assertEquals("Node 3 offset unchanged", offset3Before, offset3After);
    }

    @Test
    public void testRemoveNodeLast() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get offsets before removal
        long offset0Before = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 0);
        long offset1Before = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        long offset2Before = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2);

        // Remove last node (node 3)
        Node nodeToRemove = TEST_NODES.get(3);
        NodeIndexMapper.removeNode(nodeToRemove, NODES_FILE_NAME);

        // Verify node count decreased
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("Node count should decrease by 1", 3, loadedNodes.size());

        // Verify other nodes remain intact
        Assert.assertEquals("Node 0 offset unchanged", offset0Before, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 0));
        Assert.assertEquals("Node 1 offset unchanged", offset1Before, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1));
        Assert.assertEquals("Node 2 offset unchanged", offset2Before, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2));
    }

    @Test
    public void testRemoveNodeFirst() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get offset of last node before removal
        long offset3Before = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3);

        // Remove first node (node 0)
        Node nodeToRemove = TEST_NODES.get(0);
        NodeIndexMapper.removeNode(nodeToRemove, NODES_FILE_NAME);

        // Verify node count decreased
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("Node count should decrease by 1", 3, loadedNodes.size());

        // Verify node 0 is no longer in the file (node IDs are preserved, not position-based)
        Assert.assertFalse("Node 0 should not exist after removal", loadedNodes.containsKey(0));
        
        // Verify other nodes remain intact with their original IDs
        Assert.assertTrue("Node 1 should still exist", loadedNodes.containsKey(1));
        Assert.assertTrue("Node 2 should still exist", loadedNodes.containsKey(2));
        Assert.assertTrue("Node 3 should still exist", loadedNodes.containsKey(3));
        
        long offset3After = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3);
        Assert.assertEquals("Node 3 offset unchanged", offset3Before, offset3After);
    }

    @Test
    public void testRemoveNodeMultipleTimes() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        originalEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), 40));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph with nodes 0, 1, 2, 3, 4
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Verify initial count
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("Initial node count", 5, loadedNodes.size());

        // Remove node 1
        NodeIndexMapper.removeNode(TEST_NODES.get(1), NODES_FILE_NAME);
        loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("After first removal", 4, loadedNodes.size());

        // Remove node 3 (note: node 4 moved to position 1 after first removal)
        NodeIndexMapper.removeNode(TEST_NODES.get(3), NODES_FILE_NAME);
        loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("After second removal", 3, loadedNodes.size());

        // Remove node 0
        NodeIndexMapper.removeNode(TEST_NODES.get(0), NODES_FILE_NAME);
        loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("After third removal", 2, loadedNodes.size());
    }

    @Test
    public void testRemoveNodeOnlyNode() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(0), 10)); // Self-loop
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save graph with only one node
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Verify initial count
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("Should have 1 node", 1, loadedNodes.size());

        // Remove the only node
        NodeIndexMapper.removeNode(TEST_NODES.get(0), NODES_FILE_NAME);

        // Verify no nodes remain
        loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("Should have 0 nodes", 0, loadedNodes.size());
    }

    @Test(expected = IOException.class)
    public void testRemoveNodeOutOfRangeBeyondMax() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph with nodes 0, 1 (maxNodeId = 1)
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Try to remove node with ID just beyond max (maxNodeId is 1, try to remove 2)
        Node nodeJustBeyondMax = TEST_NODES.get(2);
        NodeIndexMapper.removeNode(nodeJustBeyondMax, NODES_FILE_NAME);
    }

    @Test(expected = IOException.class)
    public void testRemoveNodeOutOfRangeTooLarge() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph with nodes 0, 1, 2
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Try to remove node with ID beyond max (maxNodeId is 2, try to remove 999)
        Node invalidNode = new Node(createProfile("AAAA"), 999);
        NodeIndexMapper.removeNode(invalidNode, NODES_FILE_NAME);
    }

    @Test(expected = IOException.class)
    public void testRemoveNodeInvalidFile() throws IOException {
        // Create a corrupted/invalid file (too small)
        try (RandomAccessFile raf = new RandomAccessFile(NODES_FILE_NAME, "rw")) {
            raf.setLength(2); // Less than HEADER_SIZE (8 bytes)
        }

        // Try to remove node from invalid file
        Node node = TEST_NODES.get(0);
        NodeIndexMapper.removeNode(node, NODES_FILE_NAME);
    }

    @Test
    public void testRemoveNodePreservesMLSTData() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get MLST data before removal
        Map<Integer, Node> nodesBefore = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        AllelicProfile mlst0 = (AllelicProfile) nodesBefore.get(0).getMLSTdata();
        AllelicProfile mlst1 = (AllelicProfile) nodesBefore.get(1).getMLSTdata();
        AllelicProfile mlst3 = (AllelicProfile) nodesBefore.get(3).getMLSTdata();

        // Remove node 2 (with new format, node IDs are preserved, so node 3 stays as node 3)
        NodeIndexMapper.removeNode(TEST_NODES.get(2), NODES_FILE_NAME);

        // Verify MLST data preserved with correct node IDs
        Map<Integer, Node> nodesAfter = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals("Node 0 MLST preserved", mlst0, nodesAfter.get(0).getMLSTdata());
        Assert.assertEquals("Node 1 MLST preserved", mlst1, nodesAfter.get(1).getMLSTdata());
        Assert.assertEquals("Node 3 MLST preserved with its original ID", mlst3, nodesAfter.get(3).getMLSTdata());
    }

    // ===== Tests for addNodesBatch method =====

    @Test
    public void testAddNodesBatchToExistingFile() throws IOException {
        // Initialize with a small graph
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph with nodes 0, 1
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Add multiple nodes in batch (IDs 2, 3, 4)
        List<Node> newNodes = new ArrayList<>();
        newNodes.add(new Node(createProfile("TTTT"), 2));
        newNodes.add(new Node(createProfile("CCCC"), 3));
        newNodes.add(new Node(createProfile("AAAA"), 4));
        
        NodeIndexMapper.addNodesBatch(newNodes, NODES_FILE_NAME, mlstLength);

        // Verify all nodes were added
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals(5, loadedNodes.size()); // 0, 1, 2, 3, 4

        // Verify MLST data for new nodes
        AllelicProfile expectedMlst2 = createProfile("TTTT");
        AllelicProfile expectedMlst3 = createProfile("CCCC");
        AllelicProfile expectedMlst4 = createProfile("AAAA");
        Assert.assertEquals(expectedMlst2, loadedNodes.get(2).getMLSTdata());
        Assert.assertEquals(expectedMlst3, loadedNodes.get(3).getMLSTdata());
        Assert.assertEquals(expectedMlst4, loadedNodes.get(4).getMLSTdata());

        // Verify all new nodes have initial offset of -1
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 3));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 4));
    }

    @Test
    public void testAddNodesBatchLargeNumber() throws IOException {
        // Initialize with empty graph
        Graph emptyGraph = new Graph(new ArrayList<>());
        int mlstLength = 4;
        NodeIndexMapper.saveGraph(emptyGraph, mlstLength, Map.of(), NODES_FILE_NAME);

        // Add 100 nodes in one batch
        List<Node> newNodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String mlstData = String.format("A%03d", i); // "A000", "A001", ..., "A099"
            newNodes.add(new Node(createProfile(mlstData), i));
        }
        
        NodeIndexMapper.addNodesBatch(newNodes, NODES_FILE_NAME, mlstLength);

        // Verify all nodes were added
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals(100, loadedNodes.size());

        // Spot check a few nodes
        AllelicProfile mlst0 = (AllelicProfile) loadedNodes.get(0).getMLSTdata();
        AllelicProfile mlst50 = (AllelicProfile) loadedNodes.get(50).getMLSTdata();
        AllelicProfile mlst99 = (AllelicProfile) loadedNodes.get(99).getMLSTdata();
        Assert.assertTrue("MLST should start with A", mlst0.toString().startsWith("A"));
        Assert.assertTrue("MLST should start with A", mlst50.toString().startsWith("A"));
        Assert.assertTrue("MLST should start with A", mlst99.toString().startsWith("A"));
    }

    @Test
    public void testAddNodesBatchEmptyList() throws IOException {
        // Initialize with small graph
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get initial node count
        Map<Integer, Node> nodesBefore = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        int countBefore = nodesBefore.size();

        // Add empty list (should do nothing)
        NodeIndexMapper.addNodesBatch(new ArrayList<>(), NODES_FILE_NAME, mlstLength);

        // Verify no change
        Map<Integer, Node> nodesAfter = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals(countBefore, nodesAfter.size());
    }

    @Test
    public void testAddNodesBatchPreservesExistingNodes() throws IOException {
        // Initialize with nodes 0, 1, 2
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Get original MLST data
        Map<Integer, Node> nodesBefore = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        AllelicProfile mlst0 = (AllelicProfile) nodesBefore.get(0).getMLSTdata();
        AllelicProfile mlst1 = (AllelicProfile) nodesBefore.get(1).getMLSTdata();
        AllelicProfile mlst2 = (AllelicProfile) nodesBefore.get(2).getMLSTdata();

        // Add new nodes in batch (IDs 3, 4)
        List<Node> newNodes = new ArrayList<>();
        newNodes.add(new Node(createProfile("GGGG"), 3));
        newNodes.add(new Node(createProfile("TTTT"), 4));
        NodeIndexMapper.addNodesBatch(newNodes, NODES_FILE_NAME, mlstLength);

        // Verify original nodes preserved
        Map<Integer, Node> nodesAfter = NodeIndexMapper.loadNodes(NODES_FILE_NAME);
        Assert.assertEquals(5, nodesAfter.size());
        Assert.assertEquals(mlst0, nodesAfter.get(0).getMLSTdata());
        Assert.assertEquals(mlst1, nodesAfter.get(1).getMLSTdata());
        Assert.assertEquals(mlst2, nodesAfter.get(2).getMLSTdata());
        AllelicProfile expectedMlst3 = createProfile("GGGG");
        AllelicProfile expectedMlst4 = createProfile("TTTT");
        Assert.assertEquals(expectedMlst3, nodesAfter.get(3).getMLSTdata());
        Assert.assertEquals(expectedMlst4, nodesAfter.get(4).getMLSTdata());
    }

    // ===== Tests for getIncomingEdgeOffsetsBatch method =====

    @Test
    public void testGetIncomingEdgeOffsetsBatchMultipleNodes() throws IOException {
        // Create graph with multiple nodes with different offsets
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(2), 30));
        originalEdges.add(new Edge(TEST_NODES.get(4), TEST_NODES.get(3), 40));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Request offsets for nodes 1, 2, 3 in batch
        Set<Integer> nodeIds = Set.of(1, 2, 3);
        Map<Integer, Long> batchOffsets = NodeIndexMapper.getIncomingEdgeOffsetsBatch(NODES_FILE_NAME, nodeIds);

        // Verify batch results match individual lookups
        Assert.assertEquals(3, batchOffsets.size());
        Assert.assertEquals(offsetMap.get(1), batchOffsets.get(1));
        Assert.assertEquals(offsetMap.get(2), batchOffsets.get(2));
        Assert.assertEquals(offsetMap.get(3), batchOffsets.get(3));

        // Double-check against individual getIncomingEdgeOffset calls
        for (int nodeId : nodeIds) {
            long expectedOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, nodeId);
            Assert.assertEquals("Batch offset should match individual lookup for node " + nodeId, 
                              expectedOffset, (long) batchOffsets.get(nodeId));
        }
    }

    @Test
    public void testGetIncomingEdgeOffsetsBatchSingleNode() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Request offset for single node
        Set<Integer> nodeIds = Set.of(1);
        Map<Integer, Long> batchOffsets = NodeIndexMapper.getIncomingEdgeOffsetsBatch(NODES_FILE_NAME, nodeIds);

        Assert.assertEquals(1, batchOffsets.size());
        Assert.assertEquals(offsetMap.get(1), batchOffsets.get(1));
    }

    @Test
    public void testGetIncomingEdgeOffsetsBatchEmptySet() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Request offsets for empty set
        Map<Integer, Long> batchOffsets = NodeIndexMapper.getIncomingEdgeOffsetsBatch(NODES_FILE_NAME, Set.of());

        Assert.assertTrue("Empty set should return empty map", batchOffsets.isEmpty());
    }

    @Test
    public void testGetIncomingEdgeOffsetsBatchNullSet() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Request offsets for null set
        Map<Integer, Long> batchOffsets = NodeIndexMapper.getIncomingEdgeOffsetsBatch(NODES_FILE_NAME, null);

        Assert.assertTrue("Null set should return empty map", batchOffsets.isEmpty());
    }

    @Test
    public void testGetIncomingEdgeOffsetsBatchWithNoIncomingEdges() throws IOException {
        // Create nodes with no incoming edges (offset = -1)
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Request offset for node 0 which has no incoming edges
        Set<Integer> nodeIds = Set.of(0);
        Map<Integer, Long> batchOffsets = NodeIndexMapper.getIncomingEdgeOffsetsBatch(NODES_FILE_NAME, nodeIds);

        Assert.assertEquals(1, batchOffsets.size());
        Assert.assertEquals(-1L, (long) batchOffsets.get(0));
    }

    @Test
    public void testGetIncomingEdgeOffsetsBatchLargeSet() throws IOException {
        // Create a large graph
        List<Edge> originalEdges = new ArrayList<>();
        List<Node> allNodes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String mlstData = String.format("A%03d", i);
            allNodes.add(new Node(createProfile(mlstData), i));
        }
        
        // Create edges from i to i+1
        for (int i = 0; i < 49; i++) {
            originalEdges.add(new Edge(allNodes.get(i), allNodes.get(i + 1), i * 10));
        }
        
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Request offsets for many nodes at once
        Set<Integer> nodeIds = new HashSet<>();
        for (int i = 1; i < 50; i++) { // Skip node 0 (has no incoming edges)
            nodeIds.add(i);
        }
        
        Map<Integer, Long> batchOffsets = NodeIndexMapper.getIncomingEdgeOffsetsBatch(NODES_FILE_NAME, nodeIds);

        // Verify batch results
        Assert.assertEquals(49, batchOffsets.size());
        
        // Spot check a few to ensure correctness
        for (int nodeId : List.of(1, 10, 25, 40, 49)) {
            long expectedOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, nodeId);
            Assert.assertEquals("Offset mismatch for node " + nodeId, 
                              expectedOffset, (long) batchOffsets.get(nodeId));
        }
    }

    @Test
    public void testGetIncomingEdgeOffsetsBatchMixedNodes() throws IOException {
        // Create nodes with some having incoming edges and some not
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 10)); // 2 has incoming
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(3), 20)); // 3 has incoming
        // Nodes 0, 1 have no incoming edges
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME);

        // Request all nodes (0, 1, 2, 3 only - node 4 doesn't exist in graph)
        Set<Integer> nodeIds = Set.of(0, 1, 2, 3);
        Map<Integer, Long> batchOffsets = NodeIndexMapper.getIncomingEdgeOffsetsBatch(NODES_FILE_NAME, nodeIds);

        Assert.assertEquals(4, batchOffsets.size());
        Assert.assertEquals(-1L, (long) batchOffsets.get(0)); // No incoming
        Assert.assertEquals(-1L, (long) batchOffsets.get(1)); // No incoming
        Assert.assertTrue(batchOffsets.get(2) >= 0); // Has incoming
        Assert.assertTrue(batchOffsets.get(3) >= 0); // Has incoming
    }
}