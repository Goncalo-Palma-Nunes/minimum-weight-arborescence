package optimalarborescence.unittests.mapper.graph;

import java.io.IOException;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.List;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

public class EdgeListMapperTest {

    private static final List<String> MLST_DATA = List.of(
        "AGTC", "AATT", "CGCG", "ATAT", "GTCA"
    );

    private static final String FILE_NAME = "test_edgelist_mapper.dat";

    private static final List<Node> TEST_NODES = new ArrayList<>();
    static {
        for (int i = 0; i < MLST_DATA.size(); i++) {
            TEST_NODES.add(new Node(MLST_DATA.get(i), i));
        }
    }

    @Test
    public void testSaveAndLoadEdgeList() throws IOException {
        List<Edge> originalEdges = new ArrayList<>();
        originalEdges.add(new Edge(TEST_NODES.get(0), TEST_NODES.get(1), 10));
        originalEdges.add(new Edge(TEST_NODES.get(1), TEST_NODES.get(2), 20));
        originalEdges.add(new Edge(TEST_NODES.get(3), TEST_NODES.get(1), 30));

        // Save edges to file
        EdgeListMapper.saveEdgesToMappedFile(originalEdges, FILE_NAME);

        // Load edges back from file
        List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile(FILE_NAME);
        Assert.assertEquals(originalEdges.size(), loadedEdges.size());

        // Edges are saved sorted by destination id, so we should sort the originalEdges for comparison
        originalEdges.sort((e1, e2) -> Integer.compare(
            e1.getDestination().getID(), e2.getDestination().getID()));

        for (int i = 0; i < originalEdges.size(); i++) {
            Edge orig = originalEdges.get(i);
            Edge loaded = loadedEdges.get(i);
            Assert.assertEquals(orig.getSource().getId(), loaded.getSource().getId());
            Assert.assertEquals(orig.getDestination().getId(), loaded.getDestination().getId());
            Assert.assertEquals(orig.getWeight(), loaded.getWeight());
        }
    }
}
