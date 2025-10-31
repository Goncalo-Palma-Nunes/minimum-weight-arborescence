package optimalarborescence.unittests.mapper.graph;

import java.io.IOException;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.graph.Graph;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class NodeIndexMapperTest {

    private static final List<String> MLST_DATA = List.of(
        "AGTC", "AATT", "CGCG", "ATAT", "GTCA", "TATA", "CAGT", "GACT"
    );

    private static final String NODES_FILE_NAME = "test_nodeindex_mapper_nodes.dat";
    private static final String MLST_FILE_NAME = "test_nodeindex_mapper_mlst.dat";
    private static final String EDGES_FILE_NAME = "test_nodeindex_mapper_edges.dat";
    private static final List<Node> TEST_NODES = new ArrayList<>();
    static {
        for (int i = 0; i < MLST_DATA.size(); i++) {
            TEST_NODES.add(new Node(MLST_DATA.get(i), i));
        }
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Load nodes back from files
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME, MLST_FILE_NAME);
        Assert.assertEquals(TEST_NODES.size(), loadedNodes.size());
        for (Node originalNode : TEST_NODES) {
            Node loadedNode = loadedNodes.get(originalNode.getID());
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
        NodeIndexMapper.saveGraph(emptyGraph, mlstLength, Map.of(), NODES_FILE_NAME, MLST_FILE_NAME);

        // Load nodes back
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME, MLST_FILE_NAME);
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
        NodeIndexMapper.saveGraph(graphNodes, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Load nodes back
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(NODES_FILE_NAME, MLST_FILE_NAME);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Test offsets
        for (Node node : graph.getNodes()) {
            long expectedOffset = offsetMap.getOrDefault(node.getID(), -1L);
            long loadedOffset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, node.getID(), mlstLength);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Add a new node with ID 3 (next sequential ID after 0,1,2)
        Node newNode = new Node("GGGG", 3);
        NodeIndexMapper.addNode(newNode, NODES_FILE_NAME, MLST_FILE_NAME, mlstLength);

        // Verify by loading the MLST data file directly and checking it was appended
        long expectedOffset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 3, mlstLength);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Add multiple new nodes (sequential IDs after existing nodes 0,1)
        Node node1 = new Node("TTTT", 2);
        Node node2 = new Node("CCCC", 3);
        Node node3 = new Node("AAAA", 4);

        NodeIndexMapper.addNode(node1, NODES_FILE_NAME, MLST_FILE_NAME, mlstLength);
        NodeIndexMapper.addNode(node2, NODES_FILE_NAME, MLST_FILE_NAME, mlstLength);
        NodeIndexMapper.addNode(node3, NODES_FILE_NAME, MLST_FILE_NAME, mlstLength);

        // Verify each node was added by checking their offsets
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 2, mlstLength));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 3, mlstLength));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 4, mlstLength));
    }

    @Test
    public void testAddNodeWithDifferentMLSTLength() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 8; // Longer MLST length

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Add a new node with longer MLST data (ID 2, next after 0,1)
        Node newNode = new Node("AGTCAGTC", 2);
        NodeIndexMapper.addNode(newNode, NODES_FILE_NAME, MLST_FILE_NAME, mlstLength);

        // Verify the node was added with offset -1
        long offset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 2, mlstLength);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Get original offset
        long originalOffset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength);

        // Update offset for node 1
        long newOffset = 9999L;
        NodeIndexMapper.updateIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength, newOffset);

        // Verify the offset was updated
        long updatedOffset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Update offset to -1 (indicating no incoming edges)
        NodeIndexMapper.updateIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength, -1L);

        // Verify the offset is now -1
        long updatedOffset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Update offset multiple times
        NodeIndexMapper.updateIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength, 1000L);
        Assert.assertEquals(1000L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength));

        NodeIndexMapper.updateIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength, 2000L);
        Assert.assertEquals(2000L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength));

        NodeIndexMapper.updateIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength, 3000L);
        Assert.assertEquals(3000L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength));
    }

    @Test(expected = IOException.class)
    public void testUpdateIncomingEdgeOffsetOutOfRange() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Try to update offset for a node ID that doesn't exist
        NodeIndexMapper.updateIncomingEdgeOffset(MLST_FILE_NAME, 999, mlstLength, 5000L);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Prepare new offsets for multiple nodes
        Map<Integer, Long> newOffsets = new java.util.HashMap<>();
        newOffsets.put(1, 1111L);
        newOffsets.put(2, 2222L);
        newOffsets.put(3, 3333L);

        // Update all offsets at once
        NodeIndexMapper.updateIncomingEdgeOffsets(MLST_FILE_NAME, mlstLength, newOffsets);

        // Verify all offsets were updated
        Assert.assertEquals(1111L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength));
        Assert.assertEquals(2222L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 2, mlstLength));
        Assert.assertEquals(3333L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 3, mlstLength));
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Get original offsets
        long originalOffset1 = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength);
        long originalOffset3 = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 3, mlstLength);

        // Update only node 2
        Map<Integer, Long> newOffsets = new java.util.HashMap<>();
        newOffsets.put(2, 7777L);

        NodeIndexMapper.updateIncomingEdgeOffsets(MLST_FILE_NAME, mlstLength, newOffsets);

        // Verify only node 2 was updated, others remain unchanged
        Assert.assertEquals(originalOffset1, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength));
        Assert.assertEquals(7777L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 2, mlstLength));
        Assert.assertEquals(originalOffset3, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 3, mlstLength));
    }

    @Test
    public void testUpdateIncomingEdgeOffsetsEmpty() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Get original offset
        long originalOffset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength);

        // Update with empty map (no changes should occur)
        Map<Integer, Long> emptyOffsets = new java.util.HashMap<>();
        NodeIndexMapper.updateIncomingEdgeOffsets(MLST_FILE_NAME, mlstLength, emptyOffsets);

        // Verify offset remains unchanged
        long unchangedOffset = NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Update offsets with negative values (indicating no incoming edges)
        Map<Integer, Long> newOffsets = new java.util.HashMap<>();
        newOffsets.put(1, -1L);
        newOffsets.put(2, -1L);

        NodeIndexMapper.updateIncomingEdgeOffsets(MLST_FILE_NAME, mlstLength, newOffsets);

        // Verify offsets are now -1
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength));
        Assert.assertEquals(-1L, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 2, mlstLength));
    }

    @Test(expected = IOException.class)
    public void testUpdateIncomingEdgeOffsetsOutOfRange() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        Graph graph = new Graph(originalEdges);
        int mlstLength = 4;

        // Save initial graph
        Map<Integer, Long> offsetMap = EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Try to update offset for a node ID that doesn't exist
        Map<Integer, Long> invalidOffsets = new java.util.HashMap<>();
        invalidOffsets.put(999, 5000L);

        NodeIndexMapper.updateIncomingEdgeOffsets(MLST_FILE_NAME, mlstLength, invalidOffsets);
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
        NodeIndexMapper.saveGraph(graph, mlstLength, offsetMap, NODES_FILE_NAME, MLST_FILE_NAME);

        // Update with large offset values
        Map<Integer, Long> largeOffsets = new java.util.HashMap<>();
        largeOffsets.put(1, Long.MAX_VALUE - 1000);
        largeOffsets.put(2, Long.MAX_VALUE / 2);

        NodeIndexMapper.updateIncomingEdgeOffsets(MLST_FILE_NAME, mlstLength, largeOffsets);

        // Verify large offsets were stored correctly
        Assert.assertEquals(Long.MAX_VALUE - 1000, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 1, mlstLength));
        Assert.assertEquals(Long.MAX_VALUE / 2, NodeIndexMapper.getIncomingEdgeOffset(MLST_FILE_NAME, 2, mlstLength));
    }
}