package optimalarborescence.unittests.mapper.graph;

import java.io.IOException;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.sequences.AllelicProfile;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

public class EdgeListMapperTest {

    private static final List<String> MLST_DATA = List.of(
        "AGTC", "AATT", "CGCG", "ATAT", "GTCA"
    );

    private static final String EDGES_FILE_NAME = "test_edgelist_mapper_edges.dat";
    private static final String NODES_FILE_NAME = "test_edgelist_mapper_nodes.dat";

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

    /**
     * Helper method to initialize NodeIndexMapper with test nodes and update offsets.
     * This is required before calling addEdge or addEdges methods.
     */
    private void initializeNodeIndexMapper(List<Edge> initialEdges) throws IOException {
        int mlstLength = MLST_DATA.get(0).length();
        
        // Save edges and get offsets
        Map<Integer, Long> offsets = EdgeListMapper.saveEdgesToMappedFile(initialEdges, EDGES_FILE_NAME);
        
        // Save nodes with these offsets
        NodeIndexMapper.saveGraph(TEST_NODES, mlstLength, offsets, NODES_FILE_NAME);
    }

    @Test
    public void testSaveAndLoadEdgeList() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(1), 30));

        // Save edges to file
        EdgeListMapper.saveEdgesToMappedFile(originalEdges, EDGES_FILE_NAME);

        // Load edges back from file
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(originalEdges.size(), loadedEdges.size());

        for (int i = 0; i < originalEdges.size(); i++) {
            Edge orig = originalEdges.get(i);
            Edge loaded = loadedEdges.get(i);
            Assert.assertEquals(orig.getSource().getId(), loaded.getSource().getId());
            Assert.assertEquals(orig.getDestination().getId(), loaded.getDestination().getId());
            Assert.assertEquals(orig.getWeight(), loaded.getWeight());
        }
    }

    @Test
    public void testGetNumEdgesWithEdges() throws IOException {
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        edges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), 40));

        // Save edges to file
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Get number of edges
        int numEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(edges.size(), numEdges);
    }

    @Test
    public void testGetNumEdgesWithNoEdges() throws IOException {
        // Create an empty edge list file
        EdgeListMapper.saveEdgesToMappedFile(new ArrayList<>(), EDGES_FILE_NAME);

        // Get number of edges
        int numEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(0, numEdges);
    }

    // ===== Tests for addEdge method =====

    @Test
    public void testAddEdgeToEmptyFile() throws IOException {
        // Initialize nodes with empty edge list
        List<Edge> initialEdges = new ArrayList<>();
        initializeNodeIndexMapper(initialEdges);

        // Add a single edge
        Edge newEdge = new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 50);
        EdgeListMapper.addEdge(newEdge, EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(1, loadedEdges.size());
        Assert.assertEquals(newEdge.getSource().getId(), loadedEdges.get(0).getSource().getId());
        Assert.assertEquals(newEdge.getDestination().getId(), loadedEdges.get(0).getDestination().getId());
        Assert.assertEquals(newEdge.getWeight(), loadedEdges.get(0).getWeight());
    }

    @Test
    public void testAddEdgeToExistingFile() throws IOException {
        // Initialize nodes with initial edge list
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initialEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        initializeNodeIndexMapper(initialEdges);

        // Add a new edge
        Edge newEdge = new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30);
        EdgeListMapper.addEdge(newEdge, EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(3, loadedEdges.size());
        
        // Find the new edge in the loaded edges
        boolean found = false;
        for (Edge edge : loadedEdges) {
            if (edge.getSource().getId() == newEdge.getSource().getId() &&
                edge.getDestination().getId() == newEdge.getDestination().getId() &&
                edge.getWeight() == newEdge.getWeight()) {
                found = true;
                break;
            }
        }
        Assert.assertTrue("New edge should be in the loaded edges", found);
    }

    @Test
    public void testAddEdgeWithSameDestination() throws IOException {
        // Initialize nodes with initial edge list with edges to node 1
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initialEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(1), 20));
        initializeNodeIndexMapper(initialEdges);

        // Add another edge to node 1
        Edge newEdge = new Edge(TEST_NODES.get(3), TEST_NODES.get(1), 30);
        EdgeListMapper.addEdge(newEdge, EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(3, loadedEdges.size());
        
        // Count edges to node 1
        int edgesToNode1 = 0;
        for (Edge edge : loadedEdges) {
            if (edge.getDestination().getId() == 1) {
                edgesToNode1++;
            }
        }
        Assert.assertEquals(3, edgesToNode1);
    }

    @Test
    public void testAddMultipleEdgesSequentially() throws IOException {
        // Initialize nodes with initial edge list
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add multiple edges one by one
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20), EDGES_FILE_NAME);
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30), EDGES_FILE_NAME);
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), 40), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, loadedEdges.size());
    }

    @Test
    public void testAddEdgeWithDifferentWeights() throws IOException {
        // Initialize nodes with initial edge list
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add edges with various weights
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 0), EDGES_FILE_NAME);
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 999), EDGES_FILE_NAME);
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), -50), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, loadedEdges.size());
        
        // Verify weights are preserved
        boolean foundZero = false;
        boolean found999 = false;
        boolean foundNegative = false;
        
        for (Edge edge : loadedEdges) {
            if (edge.getWeight() == 0) foundZero = true;
            if (edge.getWeight() == 999) found999 = true;
            if (edge.getWeight() == -50) foundNegative = true;
        }
        
        Assert.assertTrue("Edge with weight 0 should be found", foundZero);
        Assert.assertTrue("Edge with weight 999 should be found", found999);
        Assert.assertTrue("Edge with weight -50 should be found", foundNegative);
    }

    // ===== Tests for addEdges method =====

    @Test
    public void testAddEdgesToEmptyFile() throws IOException {
        // Initialize nodes with empty edge list
        List<Edge> initialEdges = new ArrayList<>();
        initializeNodeIndexMapper(initialEdges);

        // Add multiple edges at once to node 1
        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        newEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(1), 20));
        newEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(1), 30));
        
        EdgeListMapper.addEdges(newEdges, TEST_NODES.get(1), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(3, loadedEdges.size());
        
        // Verify all edges are to node 1
        for (Edge edge : loadedEdges) {
            Assert.assertEquals(1, edge.getDestination().getId());
        }
    }

    @Test
    public void testAddEdgesToExistingFile() throws IOException {
        // Initialize nodes with initial edge list
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initialEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        initializeNodeIndexMapper(initialEdges);

        // Add multiple edges at once to node 3
        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 30));
        newEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(3), 40));
        
        EdgeListMapper.addEdges(newEdges, TEST_NODES.get(3), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, loadedEdges.size());
        
        // Count edges to node 3
        int edgesToNode3 = 0;
        for (Edge edge : loadedEdges) {
            if (edge.getDestination().getId() == 3) {
                edgesToNode3++;
            }
        }
        Assert.assertEquals(2, edgesToNode3);
    }

    @Test
    public void testAddEdgesWithExistingDestination() throws IOException {
        // Initialize nodes with initial edge list with some edges to node 2
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 10));
        initialEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        initializeNodeIndexMapper(initialEdges);

        // Add more edges to node 2
        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(2), 30));
        newEdges.add(new Edge(TEST_NODES.get(4), TEST_NODES.get(2), 40));
        
        EdgeListMapper.addEdges(newEdges, TEST_NODES.get(2), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, loadedEdges.size());
        
        // All edges should be to node 2
        for (Edge edge : loadedEdges) {
            Assert.assertEquals(2, edge.getDestination().getId());
        }
    }

    @Test
    public void testAddEdgesBulk() throws IOException {
        // Initialize nodes with initial edge list
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add a large number of edges at once
        List<Edge> newEdges = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            newEdges.add(new Edge(TEST_NODES.get(i % 5), TEST_NODES.get(4), 100 + i));
        }
        
        EdgeListMapper.addEdges(newEdges, TEST_NODES.get(4), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(11, loadedEdges.size()); // 1 initial + 10 new
        
        // Count edges to node 4
        int edgesToNode4 = 0;
        for (Edge edge : loadedEdges) {
            if (edge.getDestination().getId() == 4) {
                edgesToNode4++;
            }
        }
        Assert.assertEquals(10, edgesToNode4);
    }

    @Test
    public void testAddEdgesEmpty() throws IOException {
        // Initialize nodes with initial edge list
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add empty list of edges (should not modify the file)
        List<Edge> emptyEdges = new ArrayList<>();
        EdgeListMapper.addEdges(emptyEdges, TEST_NODES.get(2), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(1, loadedEdges.size()); // Should still have only the initial edge
    }

    @Test
    public void testAddEdgesThenAddSingleEdge() throws IOException {
        // Initialize nodes with initial edge list
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add multiple edges to node 2
        List<Edge> bulkEdges = new ArrayList<>();
        bulkEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20));
        bulkEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 30));
        EdgeListMapper.addEdges(bulkEdges, TEST_NODES.get(2), EDGES_FILE_NAME);

        // Add a single edge to node 3
        Edge singleEdge = new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 40);
        EdgeListMapper.addEdge(singleEdge, EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, loadedEdges.size()); // 1 initial + 2 bulk + 1 single
    }

    @Test
    public void testAddEdgesWithVariousWeights() throws IOException {
        // Initialize nodes with empty edge list
        List<Edge> initialEdges = new ArrayList<>();
        initializeNodeIndexMapper(initialEdges);

        // Add edges with various weights
        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(4), 0));
        newEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(4), Integer.MAX_VALUE));
        newEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(4), Integer.MIN_VALUE));
        newEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), -100));
        
        EdgeListMapper.addEdges(newEdges, TEST_NODES.get(4), EDGES_FILE_NAME);

        // Load and verify
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, loadedEdges.size());
        
        // Verify weights are preserved
        boolean foundZero = false;
        boolean foundMax = false;
        boolean foundMin = false;
        boolean foundNegative = false;
        
        for (Edge edge : loadedEdges) {
            if (edge.getWeight() == 0) foundZero = true;
            if (edge.getWeight() == Integer.MAX_VALUE) foundMax = true;
            if (edge.getWeight() == Integer.MIN_VALUE) foundMin = true;
            if (edge.getWeight() == -100) foundNegative = true;
        }
        
        Assert.assertTrue("Edge with weight 0 should be found", foundZero);
        Assert.assertTrue("Edge with MAX weight should be found", foundMax);
        Assert.assertTrue("Edge with MIN weight should be found", foundMin);
        Assert.assertTrue("Edge with negative weight should be found", foundNegative);
    }

    @Test
    public void testReadEdgeAtOffset() throws IOException {

        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(4), 0));
        newEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(4), 20));
        newEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(4), 30));
        EdgeListMapper.saveEdgesToMappedFile(newEdges, EDGES_FILE_NAME);

        Edge newEdge = new Edge(TEST_NODES.get(4), TEST_NODES.get(3), 40);
        EdgeListMapper.addEdge(newEdge, EDGES_FILE_NAME);

        // Read edge at specific offset (should be the second edge)
        long offset = EdgeListMapper.HEADER_SIZE + newEdges.size() * EdgeListMapper.BYTES_PER_EDGE;
        Edge readEdge = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offset);

        Assert.assertNotNull("Read edge should not be null", readEdge);
        Assert.assertEquals(newEdge.getSource().getId(), readEdge.getSource().getId());
        Assert.assertEquals(newEdge.getDestination().getId(), readEdge.getDestination().getId());
        Assert.assertEquals(newEdge.getWeight(), readEdge.getWeight());
    }

    @Test
    public void testReadEdgeAtOffsetOutOfBounds() throws IOException {

        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(4), 0));
        EdgeListMapper.saveEdgesToMappedFile(newEdges, EDGES_FILE_NAME);

        // Read edge at out-of-bounds offset
        long offset = EdgeListMapper.HEADER_SIZE + 10 * EdgeListMapper.BYTES_PER_EDGE; // Beyond file size
        Edge readEdge = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offset);

        Assert.assertNull("Read edge should be null for out-of-bounds offset", readEdge);
    }

    @Test
    public void testLoadLinkedList() throws IOException {
        List<Edge> newEdges = new ArrayList<>();
        newEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(4), 0));
        newEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(4), 20));
        newEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(2), 20));
        newEdges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(4), 30));
        newEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        EdgeListMapper.saveEdgesToMappedFile(newEdges, EDGES_FILE_NAME);

        List<Edge> expectedEdges = newEdges.stream().filter(e -> e.getDestination().getId() == TEST_NODES.get(2).getId()).toList();

        // Load linked list of edges
        long offset = EdgeListMapper.HEADER_SIZE +  2 * EdgeListMapper.BYTES_PER_EDGE; // Start from 3rd edge (first incidence in node 2)
        List<Edge> loadedEdges = EdgeListMapper.loadLinkedList(EDGES_FILE_NAME, offset);

        Assert.assertEquals("Loaded edges size should match", expectedEdges.size(), loadedEdges.size());
        for (int i = 0; i < expectedEdges.size(); i++) {
            Edge expected = expectedEdges.get(i);
            Edge actual = loadedEdges.get(i);
            Assert.assertEquals(expected.getSource().getId(), actual.getSource().getId());
            Assert.assertEquals(expected.getDestination().getId(), actual.getDestination().getId());
            Assert.assertEquals(expected.getWeight(), actual.getWeight());
        }
    }

    // ===== Tests for getOutgoingEdgeOffsets method =====

    /**
     * Test getting outgoing edges for node with no outgoing edges.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsNoOutgoingEdges() throws IOException {
        // Arrange - create edges where node 2 has no outgoing edges
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(2), 30));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for node 2 (has no outgoing edges)
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 2);

        // Assert
        Assert.assertNotNull("Offsets list should not be null", offsets);
        Assert.assertEquals("Node 2 should have no outgoing edges", 0, offsets.size());
    }

    /**
     * Test getting outgoing edges for node with single outgoing edge.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsSingleEdge() throws IOException {
        // Arrange - create edges where node 0 has one outgoing edge
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 20));
        edges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), 30));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for node 0
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);

        // Assert
        Assert.assertNotNull("Offsets list should not be null", offsets);
        Assert.assertEquals("Node 0 should have 1 outgoing edge", 1, offsets.size());

        // Verify the offset points to the correct edge
        long offset = offsets.get(0);
        Edge edge = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offset);
        Assert.assertNotNull("Edge should be readable at offset", edge);
        Assert.assertEquals("Source should be node 0", 0, edge.getSource().getId());
        Assert.assertEquals("Destination should be node 1", 1, edge.getDestination().getId());
        Assert.assertEquals("Weight should be 10", 10, edge.getWeight());
    }

    /**
     * Test getting outgoing edges for node with multiple outgoing edges.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsMultipleEdges() throws IOException {
        // Arrange - create edges where node 0 has multiple outgoing edges
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 30));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(4), 40));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for node 0
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);

        // Assert
        Assert.assertNotNull("Offsets list should not be null", offsets);
        Assert.assertEquals("Node 0 should have 3 outgoing edges", 3, offsets.size());

        // Verify each offset points to an edge from node 0
        for (long offset : offsets) {
            Edge edge = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offset);
            Assert.assertNotNull("Edge should be readable at offset", edge);
            Assert.assertEquals("All edges should originate from node 0", 0, edge.getSource().getId());
        }

        // Verify the destinations are correct
        List<Integer> destinations = new ArrayList<>();
        for (long offset : offsets) {
            Edge edge = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offset);
            destinations.add(edge.getDestination().getId());
        }
        Assert.assertTrue("Should have edge to node 1", destinations.contains(1));
        Assert.assertTrue("Should have edge to node 2", destinations.contains(2));
        Assert.assertTrue("Should have edge to node 3", destinations.contains(3));
    }

    /**
     * Test getting outgoing edges for all nodes in a graph.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsAllNodes() throws IOException {
        // Arrange - create a complete graph structure
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 30));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(3), 40));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 50));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act & Assert - check each node
        List<Long> offsets0 = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);
        Assert.assertEquals("Node 0 should have 2 outgoing edges", 2, offsets0.size());

        List<Long> offsets1 = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 1);
        Assert.assertEquals("Node 1 should have 2 outgoing edges", 2, offsets1.size());

        List<Long> offsets2 = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 2);
        Assert.assertEquals("Node 2 should have 1 outgoing edge", 1, offsets2.size());

        List<Long> offsets3 = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 3);
        Assert.assertEquals("Node 3 should have 0 outgoing edges", 0, offsets3.size());
    }

    /**
     * Test getting outgoing edges with self-loop.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsWithSelfLoop() throws IOException {
        // Arrange - create edges including a self-loop
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(1), 20)); // self-loop
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 30));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for node 1
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 1);

        // Assert
        Assert.assertNotNull("Offsets list should not be null", offsets);
        Assert.assertEquals("Node 1 should have 2 outgoing edges (including self-loop)", 2, offsets.size());

        // Verify one of them is a self-loop
        boolean foundSelfLoop = false;
        for (long offset : offsets) {
            Edge edge = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offset);
            if (edge.getSource().getId() == 1 && edge.getDestination().getId() == 1) {
                foundSelfLoop = true;
                Assert.assertEquals("Self-loop weight should be 20", 20, edge.getWeight());
            }
        }
        Assert.assertTrue("Should find self-loop edge", foundSelfLoop);
    }

    /**
     * Test getting outgoing edges from empty file.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsEmptyFile() throws IOException {
        // Arrange - create empty edge file
        List<Edge> edges = new ArrayList<>();
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for any node
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);

        // Assert
        Assert.assertNotNull("Offsets list should not be null", offsets);
        Assert.assertEquals("Should have no outgoing edges in empty file", 0, offsets.size());
    }

    /**
     * Test getting outgoing edges for non-existent node.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsNonExistentNode() throws IOException {
        // Arrange - create edges for nodes 0-3
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for non-existent node 999
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 999);

        // Assert
        Assert.assertNotNull("Offsets list should not be null", offsets);
        Assert.assertEquals("Non-existent node should have no outgoing edges", 0, offsets.size());
    }

    /**
     * Test getting outgoing edges verifying offset values are correct.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsVerifyOffsetValues() throws IOException {
        // Arrange - create edges with known positions
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10)); // offset = 4
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20)); // offset = 32
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 30)); // offset = 60
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for node 0
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);

        // Assert
        Assert.assertEquals("Node 0 should have 2 outgoing edges", 2, offsets.size());

        // Expected offsets: 4 (first edge) and 60 (third edge)
        Assert.assertTrue("Should contain offset 4", offsets.contains(4L));
        Assert.assertTrue("Should contain offset 60", offsets.contains(60L));
    }

    /**
     * Test getting outgoing edges after adding edges dynamically.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsAfterAddingEdges() throws IOException {
        // Arrange - create initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Act - get initial outgoing edges for node 0
        List<Long> offsetsBefore = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);
        Assert.assertEquals("Initially node 0 should have 1 outgoing edge", 1, offsetsBefore.size());

        // Add more edges from node 0
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20), EDGES_FILE_NAME);
        EdgeListMapper.addEdge(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 30), EDGES_FILE_NAME);

        // Get updated outgoing edges for node 0
        List<Long> offsetsAfter = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);

        // Assert
        Assert.assertEquals("After adding, node 0 should have 3 outgoing edges", 3, offsetsAfter.size());
    }

    /**
     * Test getting outgoing edges with various edge weights.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsVariousWeights() throws IOException {
        // Arrange - create edges with various weights
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 0));
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), Integer.MAX_VALUE));
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), Integer.MIN_VALUE));
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(4), -100));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for node 0
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);

        // Assert
        Assert.assertEquals("Node 0 should have 4 outgoing edges", 4, offsets.size());

        // Verify all weights are preserved
        List<Integer> weights = new ArrayList<>();
        for (long offset : offsets) {
            Edge edge = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offset);
            weights.add(edge.getWeight());
        }

        Assert.assertTrue("Should have edge with weight 0", weights.contains(0));
        Assert.assertTrue("Should have edge with MAX weight", weights.contains(Integer.MAX_VALUE));
        Assert.assertTrue("Should have edge with MIN weight", weights.contains(Integer.MIN_VALUE));
        Assert.assertTrue("Should have edge with negative weight", weights.contains(-100));
    }

    /**
     * Test getting outgoing edges in a graph with cycles.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsWithCycle() throws IOException {
        // Arrange - create a cycle: 0->1->2->0
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(0), 30));
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for each node in the cycle
        List<Long> offsets0 = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);
        List<Long> offsets1 = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 1);
        List<Long> offsets2 = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 2);

        // Assert - each node in the cycle should have exactly 1 outgoing edge
        Assert.assertEquals("Node 0 should have 1 outgoing edge", 1, offsets0.size());
        Assert.assertEquals("Node 1 should have 1 outgoing edge", 1, offsets1.size());
        Assert.assertEquals("Node 2 should have 1 outgoing edge", 1, offsets2.size());

        // Verify the cycle structure
        Edge edge0 = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offsets0.get(0));
        Edge edge1 = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offsets1.get(0));
        Edge edge2 = EdgeListMapper.readEdgeAtOffset(EDGES_FILE_NAME, offsets2.get(0));

        Assert.assertEquals("Edge from 0 should go to 1", 1, edge0.getDestination().getId());
        Assert.assertEquals("Edge from 1 should go to 2", 2, edge1.getDestination().getId());
        Assert.assertEquals("Edge from 2 should go to 0", 0, edge2.getDestination().getId());
    }

    /**
     * Test getting outgoing edges returns offsets in order they appear in file.
     */
    @Test
    public void testGetOutgoingEdgeOffsetsOrderPreserved() throws IOException {
        // Arrange - create multiple edges from node 0 in specific order
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10)); // First
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 15)); // Not from 0
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20)); // Second
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 25)); // Not from 0
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 30)); // Third
        EdgeListMapper.saveEdgesToMappedFile(edges, EDGES_FILE_NAME);

        // Act - get outgoing edges for node 0
        List<Long> offsets = EdgeListMapper.getOutgoingEdgeOffsets(EDGES_FILE_NAME, 0);

        // Assert - offsets should be in ascending order (file order)
        Assert.assertEquals("Node 0 should have 3 outgoing edges", 3, offsets.size());
        Assert.assertTrue("Offsets should be in ascending order", 
            offsets.get(0) < offsets.get(1) && offsets.get(1) < offsets.get(2));

        // Expected offsets: 4, 60, 116
        Assert.assertEquals("First offset should be 4", 4L, offsets.get(0).longValue());
        Assert.assertEquals("Second offset should be 60", 60L, offsets.get(1).longValue());
        Assert.assertEquals("Third offset should be 116", 116L, offsets.get(2).longValue());
    }

    // ===== Tests for addEdgesBatch method =====

    @Test
    public void testAddEdgesBatchSingleNodeMultipleEdges() throws IOException {
        // Initialize with initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add batch of edges for node 2
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        List<Edge> edgesForNode2 = new ArrayList<>();
        edgesForNode2.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20));
        edgesForNode2.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 30));
        edgesForNode2.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(2), 40));
        nodeEdgesMap.put(TEST_NODES.get(2), edgesForNode2);

        EdgeListMapper.addEdgesBatch(nodeEdgesMap, EDGES_FILE_NAME);

        // Verify all edges were added
        List<Edge> allEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, allEdges.size()); // 1 initial + 3 new

        // Verify new edges are in the list
        int countEdgesToNode2 = 0;
        for (Edge edge : allEdges) {
            if (edge.getDestination().getId() == 2) {
                countEdgesToNode2++;
            }
        }
        Assert.assertEquals("Should have 3 edges to node 2", 3, countEdgesToNode2);
    }

    @Test
    public void testAddEdgesBatchMultipleNodesMultipleEdges() throws IOException {
        // Initialize with initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add batch of edges for multiple nodes
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        
        List<Edge> edgesForNode2 = new ArrayList<>();
        edgesForNode2.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20));
        edgesForNode2.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 30));
        nodeEdgesMap.put(TEST_NODES.get(2), edgesForNode2);
        
        List<Edge> edgesForNode3 = new ArrayList<>();
        edgesForNode3.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 40));
        edgesForNode3.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(3), 50));
        edgesForNode3.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 60));
        nodeEdgesMap.put(TEST_NODES.get(3), edgesForNode3);

        EdgeListMapper.addEdgesBatch(nodeEdgesMap, EDGES_FILE_NAME);

        // Verify all edges were added
        List<Edge> allEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(6, allEdges.size()); // 1 initial + 5 new

        // Verify edge counts
        int countEdgesToNode2 = 0;
        int countEdgesToNode3 = 0;
        for (Edge edge : allEdges) {
            if (edge.getDestination().getId() == 2) countEdgesToNode2++;
            if (edge.getDestination().getId() == 3) countEdgesToNode3++;
        }
        Assert.assertEquals("Should have 2 edges to node 2", 2, countEdgesToNode2);
        Assert.assertEquals("Should have 3 edges to node 3", 3, countEdgesToNode3);
    }

    @Test
    public void testAddEdgesBatchEmptyMap() throws IOException {
        // Initialize with initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        int edgeCountBefore = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);

        // Add empty batch (should do nothing)
        EdgeListMapper.addEdgesBatch(new HashMap<>(), EDGES_FILE_NAME);

        int edgeCountAfter = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals("Edge count should not change", edgeCountBefore, edgeCountAfter);
    }

    @Test
    public void testAddEdgesBatchNullMap() throws IOException {
        // Initialize with initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        int edgeCountBefore = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);

        // Add null batch (should do nothing)
        EdgeListMapper.addEdgesBatch(null, EDGES_FILE_NAME);

        int edgeCountAfter = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals("Edge count should not change", edgeCountBefore, edgeCountAfter);
    }

    @Test
    public void testAddEdgesBatchUpdatesIncomingOffsets() throws IOException {
        // Initialize with initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Add batch of edges for node 2 (which has no incoming edges initially)
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        List<Edge> edgesForNode2 = new ArrayList<>();
        edgesForNode2.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 20));
        nodeEdgesMap.put(TEST_NODES.get(2), edgesForNode2);

        // Check offset before (should be -1)
        long offsetBefore = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2);
        Assert.assertEquals(-1L, offsetBefore);

        EdgeListMapper.addEdgesBatch(nodeEdgesMap, EDGES_FILE_NAME);

        // Check offset after (should be updated)
        long offsetAfter = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2);
        Assert.assertTrue("Offset should be updated to valid value", offsetAfter >= 0);
    }

    @Test
    public void testAddEdgesBatchLargeNumberOfEdges() throws IOException {
        // Initialize with initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Create a large graph with many nodes
        List<Node> manyNodes = new ArrayList<>();
        for (int i = 5; i < 105; i++) { // 100 new nodes
            String mlstData = String.format("A%03d", i);
            manyNodes.add(new Node(createProfile(mlstData), i));
        }

        // Add batch with many edges to a single node
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        List<Edge> manyEdges = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            manyEdges.add(new Edge(manyNodes.get(i), TEST_NODES.get(4), i * 10));
        }
        nodeEdgesMap.put(TEST_NODES.get(4), manyEdges);

        EdgeListMapper.addEdgesBatch(nodeEdgesMap, EDGES_FILE_NAME);

        // Verify edge count
        int totalEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(101, totalEdges); // 1 initial + 100 new
    }

    @Test
    public void testAddEdgesBatchPreservesExistingEdges() throws IOException {
        // Initialize with initial edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initialEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        initializeNodeIndexMapper(initialEdges);

        // Add new batch
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        List<Edge> edgesForNode3 = new ArrayList<>();
        edgesForNode3.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 30));
        nodeEdgesMap.put(TEST_NODES.get(3), edgesForNode3);

        EdgeListMapper.addEdgesBatch(nodeEdgesMap, EDGES_FILE_NAME);

        // Load edges after
        List<Edge> edgesAfter = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);

        // Verify original edges are still present
        Assert.assertEquals(3, edgesAfter.size());
        boolean found0to1 = false;
        boolean found1to2 = false;
        for (Edge edge : edgesAfter) {
            if (edge.getSource().getId() == 0 && edge.getDestination().getId() == 1 && edge.getWeight() == 10) {
                found0to1 = true;
            }
            if (edge.getSource().getId() == 1 && edge.getDestination().getId() == 2 && edge.getWeight() == 20) {
                found1to2 = true;
            }
        }
        Assert.assertTrue("Original edge 0->1 should be preserved", found0to1);
        Assert.assertTrue("Original edge 1->2 should be preserved", found1to2);
    }

    @Test
    public void testAddEdgesBatchWithExistingIncomingEdges() throws IOException {
        // Initialize with node 1 already having incoming edges
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        initializeNodeIndexMapper(initialEdges);

        // Get initial offset for node 1
        long initialOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        Assert.assertTrue("Node 1 should have incoming edges", initialOffset >= 0);

        // Add more edges to node 1
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        List<Edge> moreEdgesForNode1 = new ArrayList<>();
        moreEdgesForNode1.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(1), 20));
        moreEdgesForNode1.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(1), 30));
        nodeEdgesMap.put(TEST_NODES.get(1), moreEdgesForNode1);

        EdgeListMapper.addEdgesBatch(nodeEdgesMap, EDGES_FILE_NAME);

        // Verify total edges
        List<Edge> allEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(3, allEdges.size()); // 1 initial + 2 new

        // Count edges to node 1
        int countEdgesToNode1 = 0;
        for (Edge edge : allEdges) {
            if (edge.getDestination().getId() == 1) {
                countEdgesToNode1++;
            }
        }
        Assert.assertEquals("Should have 3 edges to node 1", 3, countEdgesToNode1);
    }

    /**
     * Test batch removal of edges incident to specified nodes.
     */
    @Test
    public void testRemoveEdgesBatch() throws IOException {
        // Create edges: 0->1, 1->2, 2->3, 3->4, 0->2
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        edges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), 40));
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(2), 15));

        initializeNodeIndexMapper(edges);

        // Verify initial state
        int initialCount = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(5, initialCount);

        // Remove all edges incident to nodes 1 and 3
        java.util.Set<Integer> nodesToRemove = java.util.Set.of(1, 3);
        EdgeListMapper.removeEdgesBatch(nodesToRemove, EDGES_FILE_NAME);

        // Load remaining edges
        List<Edge> remainingEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);

        // Should only have edge 0->2 remaining
        // Removed: 0->1 (dest=1), 1->2 (src=1), 2->3 (dest=3), 3->4 (src=3)
        Assert.assertEquals(1, remainingEdges.size());
        
        Edge remaining = remainingEdges.get(0);
        Assert.assertEquals(0, remaining.getSource().getId());
        Assert.assertEquals(2, remaining.getDestination().getId());
        Assert.assertEquals(15, remaining.getWeight());
    }

    /**
     * Test batch removal with empty set - should be a no-op.
     */
    @Test
    public void testRemoveEdgesBatchEmptySet() throws IOException {
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));

        initializeNodeIndexMapper(edges);

        int initialCount = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);

        // Remove empty set
        EdgeListMapper.removeEdgesBatch(java.util.Set.of(), EDGES_FILE_NAME);

        int finalCount = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(initialCount, finalCount);
    }

    /**
     * Test batch removal of all edges.
     */
    @Test
    public void testRemoveEdgesBatchAllEdges() throws IOException {
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(0), 30));

        initializeNodeIndexMapper(edges);

        // Remove all edges by removing all nodes
        java.util.Set<Integer> allNodes = java.util.Set.of(0, 1, 2);
        EdgeListMapper.removeEdgesBatch(allNodes, EDGES_FILE_NAME);

        // Verify no edges remain
        int finalCount = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(0, finalCount);

        List<Edge> remainingEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertTrue(remainingEdges.isEmpty());
    }

    /**
     * Test batch removal preserves linked list structure for remaining edges.
     */
    @Test
    public void testRemoveEdgesBatchPreservesLinkedLists() throws IOException {
        // Create multiple edges to same destination
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(4), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(4), 20));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(4), 30));
        edges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), 40));

        initializeNodeIndexMapper(edges);

        // Remove edges from nodes 1 and 3
        java.util.Set<Integer> nodesToRemove = java.util.Set.of(1, 3);
        EdgeListMapper.removeEdgesBatch(nodesToRemove, EDGES_FILE_NAME);

        // Load remaining edges
        List<Edge> remainingEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(2, remainingEdges.size());

        // Verify remaining edges are correct
        boolean found0to4 = false;
        boolean found2to4 = false;

        for (Edge edge : remainingEdges) {
            if (edge.getSource().getId() == 0 && edge.getDestination().getId() == 4) {
                found0to4 = true;
                Assert.assertEquals(10, edge.getWeight());
            }
            if (edge.getSource().getId() == 2 && edge.getDestination().getId() == 4) {
                found2to4 = true;
                Assert.assertEquals(30, edge.getWeight());
            }
        }

        Assert.assertTrue("Should find edge 0->4", found0to4);
        Assert.assertTrue("Should find edge 2->4", found2to4);
    }

    /**
     * Test batch removal with complex graph structure.
     */
    @Test
    public void testRemoveEdgesBatchComplexGraph() throws IOException {
        // Create a more complex graph
        List<Edge> edges = new ArrayList<>();
        // Chain: 0->1->2->3->4
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(3), 30));
        edges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(4), 40));
        // Additional edges
        edges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(3), 15));
        edges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(4), 25));
        edges.add(new Edge(TEST_NODES.get(2), TEST_NODES.get(4), 35));

        initializeNodeIndexMapper(edges);

        Assert.assertEquals(7, EdgeListMapper.getNumEdges(EDGES_FILE_NAME));

        // Remove node 2 (middle node with multiple incident edges)
        java.util.Set<Integer> nodesToRemove = java.util.Set.of(2);
        EdgeListMapper.removeEdgesBatch(nodesToRemove, EDGES_FILE_NAME);

        // Remaining edges: 0->1, 0->3, 1->4, 3->4
        // Removed: 1->2, 2->3, 2->4
        List<Edge> remainingEdges = EdgeListMapper.loadEdgesFromMappedFile(EDGES_FILE_NAME);
        Assert.assertEquals(4, remainingEdges.size());

        // Verify no edges involve node 2
        for (Edge edge : remainingEdges) {
            Assert.assertNotEquals(2, edge.getSource().getId());
            Assert.assertNotEquals(2, edge.getDestination().getId());
        }
    }
}
