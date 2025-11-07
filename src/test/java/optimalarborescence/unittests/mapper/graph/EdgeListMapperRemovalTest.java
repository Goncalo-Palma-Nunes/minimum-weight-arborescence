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

    /** Reads the memory mapped file sequentially and prints the edge information
     * and respective offsets for debugging purposes.
     */
    private void printOffsetPerEach(String fileName) throws IOException {
        try (var raf = new java.io.RandomAccessFile(fileName, "r")) {
            var channel = raf.getChannel();
            long fileSize = channel.size();
            long offset = EdgeListMapper.HEADER_SIZE;
            System.out.println("Edges in file " + fileName + ":");
            while (offset < fileSize) {
                var mbb = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, offset, EdgeListMapper.BYTES_PER_EDGE);
                mbb.order(java.nio.ByteOrder.nativeOrder());
                int src = mbb.getInt();
                int destId = mbb.getInt();
                int weight = mbb.getInt();
                long next = mbb.getLong();
                long prev = mbb.getLong();
                System.out.println("Offset: " + offset + " | Edge: " + src + " -> " + destId + " (weight " + weight + ") | Next: " + next + " | Prev: " + prev);
                offset += EdgeListMapper.BYTES_PER_EDGE;
            }
        }
    }

    private void printOffsets() {
        System.out.println(" <----- Current Offsets -----> ");
        for (var entry : offsetMap.entrySet()) {
            System.out.println("Node ID: " + entry.getKey() + " | Offset: " + entry.getValue());
        }
        System.out.println(" <--------------------------> ");
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

    @Test
    public void testRemoveNonExistentEdge() throws IOException {
        
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long invalidOffset = EdgeListMapper.HEADER_SIZE + (initialNumEdges + 1) * EdgeListMapper.BYTES_PER_EDGE; // An offset that does not exist

        // check exception is thrown
        boolean exceptionThrown = false;
        try {
            EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, invalidOffset);
        } catch (IOException e) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

    @Test
    public void testRemoveEdgeFromEmptyFile() throws IOException {
        deleteTestFiles(BASE_FILE_NAME);
        initializeNodeIndexMapper(new Graph(new ArrayList<>()));

        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long invalidOffset = EdgeListMapper.HEADER_SIZE; // No edges exist
        
        EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, invalidOffset);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(initialNumEdges, newNumEdges);

        invalidOffset = -10; // Negative offset
        EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, invalidOffset);
        newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(initialNumEdges, newNumEdges);
    }

    @Test
    public void testRemoveInvalidOffset() throws IOException {
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long invalidOffset = EdgeListMapper.HEADER_SIZE + 1L; // Misaligned offset

        // check no exception is thrown
        EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, invalidOffset);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(initialNumEdges, newNumEdges);
    }

    @Test
    public void testRemoveEdgeAtEndOfFile() throws IOException {
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long edgeOffset = EdgeListMapper.HEADER_SIZE + (initialNumEdges - 1) * EdgeListMapper.BYTES_PER_EDGE; // Offset of last edge
        EdgeListMapper.removeEdgeAtOffset(EDGES_FILE_NAME, edgeOffset);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(initialNumEdges - 1, newNumEdges);
    }

    @Test
    public void testRemoveLinkedList() throws IOException {
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long startOffset = offsetMap.get(1); // Example offset for start of linked list
        EdgeListMapper.removeLinkedList(EDGES_FILE_NAME, startOffset);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertTrue(newNumEdges < initialNumEdges);

        long newStartOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, 1);
        Assert.assertEquals(-1, newStartOffset);
    }

    @Test
    public void testRemoveLinkedListFromEmptyFile() throws IOException {
        deleteTestFiles(BASE_FILE_NAME);
        initializeNodeIndexMapper(new Graph(new ArrayList<>()));

        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long startOffset = EdgeListMapper.HEADER_SIZE; // Start of linked list
        EdgeListMapper.removeLinkedList(EDGES_FILE_NAME, startOffset);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(initialNumEdges, newNumEdges);
        Assert.assertEquals(0, newNumEdges);
    }

    @Test
    public void testRemoveLinkedListWithInvalidOffset() throws IOException {
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        long invalidOffset = EdgeListMapper.HEADER_SIZE + 1L; // Misaligned offset
        // check no exception is thrown
        EdgeListMapper.removeLinkedList(EDGES_FILE_NAME, invalidOffset);
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(initialNumEdges, newNumEdges);
    }

    @Test
    public void testRemoveAllLinkedLists() throws IOException {
        int initialNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        // printOffsets();
        for (int nodeId : offsetMap.keySet()) {
            // printOffsetPerEach(EDGES_FILE_NAME);
            long startOffset = NodeIndexMapper.getIncomingEdgeOffset(NODES_FILE_NAME, nodeId);
            if (startOffset >= 0) {
                EdgeListMapper.removeLinkedList(EDGES_FILE_NAME, startOffset);
            }
        }
        int newNumEdges = EdgeListMapper.getNumEdges(EDGES_FILE_NAME);
        Assert.assertEquals(0, newNumEdges);
        Assert.assertTrue(newNumEdges <= initialNumEdges);
    }
}