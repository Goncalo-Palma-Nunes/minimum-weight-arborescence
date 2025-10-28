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
}