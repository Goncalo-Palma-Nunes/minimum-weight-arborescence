package optimalarborescence.unittests.mapper.graph;

import java.io.IOException;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

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
            TEST_NODES.add(new Node(MLST_DATA.get(i), i));
        }
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
}
