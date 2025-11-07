package optimalarborescence.unittests.mapper.graph;

import java.io.File;
import java.io.IOException;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.junit.Before;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class EdgeListMapperRemovalTest {

    private static final List<String> MLST_DATA = List.of(
        "AGTC", "AATT", "CGCG", "ATAT"
    );

    private static final String BASE_FILE_NAME = "test_edgelist_mapper";
    private static final String EDGES_FILE_NAME = BASE_FILE_NAME + "_edges.dat";
    private static final String NODES_FILE_NAME = BASE_FILE_NAME + "_nodes.dat";

    // private static final List<Node> TEST_NODES = new ArrayList<>();
    // static {
    //     for (int i = 0; i < MLST_DATA.size(); i++) {
    //         TEST_NODES.add(new Node(MLST_DATA.get(i), i));
    //     }
    // }

    private static Map<Integer, Long> offsetMap = null;

    private static final int MLST_LENGTH = MLST_DATA.get(0).length();

    /**
     * Helper method to initialize NodeIndexMapper with test nodes and update offsets.
     * This is required before calling addEdge or addEdges methods.
     */
    private void initializeNodeIndexMapper(Graph g) throws IOException {
        // Save edges and get offsets
        offsetMap = EdgeListMapper.saveEdgesToMappedFile(g.getEdges(), EDGES_FILE_NAME);

        // Save nodes with these offsets
        NodeIndexMapper.saveGraph(g.getNodes(), MLST_LENGTH, offsetMap, NODES_FILE_NAME);
    }

    /**
     * Create a test graph with known structure.
     */
    private Graph createTestGraph() {
        Node n0 = new Node("ATCG", 0);
        Node n1 = new Node("GCTA", 1);
        Node n2 = new Node("TGAC", 2);
        Node n3 = new Node("CGAT", 3);

        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(n0, n1, 5));
        edges.add(new Edge(n0, n2, 3));
        edges.add(new Edge(n1, n2, 2));
        edges.add(new Edge(n1, n3, 4));
        edges.add(new Edge(n2, n3, 1));
        edges.add(new Edge(n3, n0, 6));
        
        return new Graph(edges);
    }
    
    /**
     * Delete test files created during testing.
     */
    private void deleteTestFiles(String baseName) {
        new File(baseName + "_edges.dat").delete();
        new File(baseName + "_nodes.dat").delete();
    }

    @Before
    public void setUp() throws IOException {
        Graph testGraph = createTestGraph();
        initializeNodeIndexMapper(testGraph);
    }

    @After
    public void tearDown() {
        deleteTestFiles(BASE_FILE_NAME);
    }

    @Test
    public void testEdgeRemovalHeadOfLinkedList() throws IOException {
        
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long edgeOffset = offsetMap.get(2); // Example offset for edge to remove
        EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, edgeOffset);

        long newOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 2);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);

        Assert.assertNotEquals(edgeOffset, newOffset);
        Assert.assertEquals(initialNumEdges - 1, newNumEdges);
    }

    @Test
    public void testEdgeRemovalOnlyEdgeInLinkedList() throws IOException {
        
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long edgeOffset = offsetMap.get(0); // Example offset for edge to remove
        EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, edgeOffset);

        long newOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 0);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);

        Assert.assertEquals(-1, newOffset);
        Assert.assertEquals(initialNumEdges - 1, newNumEdges);
    }

    @Test
    public void testRemoveSeveralEdges() throws IOException {
        
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        List<Integer> nodesToRemoveEdgesFrom = List.of(1, 3, 2);
        List<Long> offsetsToRemove = new ArrayList<>();

        for (int nodeId : nodesToRemoveEdgesFrom) {
            Long offset = offsetMap.get(nodeId);
            if (offset != null) {
                offsetsToRemove.add(offset);
            }
        }

        for (long offset : offsetsToRemove) {
            EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, offset);
        }

        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);

        Assert.assertEquals(initialNumEdges - offsetsToRemove.size(), newNumEdges);

        // Verify that the removed edges are no longer present
        for (int id : nodesToRemoveEdgesFrom) {
            long incomingOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, id);
            Assert.assertNotEquals((long) offsetMap.get(nodesToRemoveEdgesFrom.indexOf(id)), incomingOffset);
        }
    }
}