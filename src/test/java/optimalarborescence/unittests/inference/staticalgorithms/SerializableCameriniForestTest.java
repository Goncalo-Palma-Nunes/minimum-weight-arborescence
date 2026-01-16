package optimalarborescence.unittests.inference.staticalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.SerializableCameriniForest;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.sequences.SequenceTypingData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.After;
import org.junit.Before;

/**
 * Unit tests for SerializableCameriniForest to verify lazy loading functionality.
 * Tests that the serializable version produces the same results as the in-memory version
 * while properly loading edges from memory-mapped files.
 */
public class SerializableCameriniForestTest {

    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;
    private String tempGraphFile;
    private static final int SEQUENCE_LENGTH = 7; // Example MLST sequence length

    // Default comparator for edges - min heap based on weight
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());

    @Before
    public void setUp() throws IOException {
        // Create test nodes with sequence data
        nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // Create simple test sequences (all zeros for simplicity)
            Long[] alleles = new Long[SEQUENCE_LENGTH];
            for (int j = 0; j < SEQUENCE_LENGTH; j++) {
                alleles[j] = (long) i; // Different value per node for uniqueness
            }
            SequenceTypingData seq = new SequenceTypingData(alleles, SEQUENCE_LENGTH);
            nodes.add(new Node(seq, i));
        }

        // Create test edges (same as CameriniForestSimpleGraphTest)
        edges = new ArrayList<>();
        edges.add(new Edge(nodes.get(0), nodes.get(1), 6));
        edges.add(new Edge(nodes.get(1), nodes.get(2), 10));
        edges.add(new Edge(nodes.get(1), nodes.get(3), 12));
        edges.add(new Edge(nodes.get(2), nodes.get(1), 10));
        edges.add(new Edge(nodes.get(3), nodes.get(0), 1));
        edges.add(new Edge(nodes.get(3), nodes.get(2), 8));

        originalGraph = new Graph(edges);

        // Create temporary file for memory-mapped graph
        Path tempPath = Files.createTempFile("test_graph_", ".mmap");
        tempGraphFile = tempPath.toString();
        
        // Save graph to memory-mapped file
        GraphMapper.saveGraph(originalGraph, SEQUENCE_LENGTH, tempGraphFile);
    }

    @After
    public void tearDown() {
        // Clean up temporary files
        if (tempGraphFile != null) {
            try {
                // Delete main graph file
                Files.deleteIfExists(Path.of(tempGraphFile));
                
                // Delete associated files (nodes, edges, metadata)
                String baseName = tempGraphFile;
                Files.deleteIfExists(Path.of(baseName + ".nodes"));
                Files.deleteIfExists(Path.of(baseName + ".edges"));
                Files.deleteIfExists(Path.of(baseName + ".metadata"));
            } catch (IOException e) {
                System.err.println("Failed to clean up temp files: " + e.getMessage());
            }
        }
    }

    @Test
    public void testInMemoryOperation() {
        // Test backward compatibility - in-memory operation without files
        SerializableCameriniForest camerini = new SerializableCameriniForest(originalGraph, EDGE_COMPARATOR);
        Graph result = camerini.inferPhylogeny(originalGraph);

        Assert.assertNotNull(result);
        Assert.assertEquals(originalGraph.getNumNodes(), result.getNumNodes());
        Assert.assertEquals(originalGraph.getNumNodes() - 1, result.getNumEdges());
        Assert.assertFalse("Should not be using memory-mapped files", camerini.isUsingMemoryMappedFiles());
    }

    @Test
    public void testMemoryMappedOperation() throws IOException {
        // Test memory-mapped file operation with lazy loading
        SerializableCameriniForest camerini = new SerializableCameriniForest(
            originalGraph, EDGE_COMPARATOR, tempGraphFile, SEQUENCE_LENGTH);
        
        Assert.assertTrue("Should be using memory-mapped files", camerini.isUsingMemoryMappedFiles());
        Assert.assertEquals(tempGraphFile, camerini.getBaseName());
        
        Graph result = camerini.inferPhylogeny(originalGraph);

        Assert.assertNotNull(result);
        Assert.assertEquals(originalGraph.getNumNodes(), result.getNumNodes());
        Assert.assertEquals(originalGraph.getNumNodes() - 1, result.getNumEdges());
    }

    @Test
    public void testSameResultsInMemoryVsMemoryMapped() throws IOException {
        // Verify that in-memory and memory-mapped versions produce identical results
        
        // In-memory version
        CameriniForest inMemoryCamerini = new CameriniForest(originalGraph, EDGE_COMPARATOR);
        Graph inMemoryResult = inMemoryCamerini.inferPhylogeny(originalGraph);
        
        // Memory-mapped version
        SerializableCameriniForest mmCamerini = new SerializableCameriniForest(
            originalGraph, EDGE_COMPARATOR, tempGraphFile, SEQUENCE_LENGTH);
        Graph mmResult = mmCamerini.inferPhylogeny(originalGraph);

        // Compare results
        Assert.assertEquals("Number of nodes should match", 
            inMemoryResult.getNumNodes(), mmResult.getNumNodes());
        Assert.assertEquals("Number of edges should match", 
            inMemoryResult.getNumEdges(), mmResult.getNumEdges());
        
        // Compare total cost
        long inMemoryCost = inMemoryResult.getEdges().stream()
            .mapToLong(Edge::getWeight)
            .sum();
        long mmCost = mmResult.getEdges().stream()
            .mapToLong(Edge::getWeight)
            .sum();
        Assert.assertEquals("Arborescence cost should match", inMemoryCost, mmCost);
    }

    @Test
    public void testSetBaseName() throws IOException {
        // Test transitioning from in-memory to file-based operation
        SerializableCameriniForest camerini = new SerializableCameriniForest(originalGraph, EDGE_COMPARATOR);
        
        Assert.assertFalse("Should initially not be using memory-mapped files", 
            camerini.isUsingMemoryMappedFiles());
        
        // Enable file-based operation
        camerini.setBaseName(tempGraphFile, SEQUENCE_LENGTH);
        
        Assert.assertTrue("Should now be using memory-mapped files", 
            camerini.isUsingMemoryMappedFiles());
        Assert.assertEquals(tempGraphFile, camerini.getBaseName());
        
        // Should still produce correct results
        Graph result = camerini.inferPhylogeny(originalGraph);
        Assert.assertNotNull(result);
        Assert.assertEquals(originalGraph.getNumNodes() - 1, result.getNumEdges());
    }

    @Test
    public void testLazyLoadingEdges() throws IOException {
        // Create a larger graph to better test lazy loading
        List<Node> largeNodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Long[] alleles = new Long[SEQUENCE_LENGTH];
            for (int j = 0; j < SEQUENCE_LENGTH; j++) {
                alleles[j] = (long) i;
            }
            SequenceTypingData seq = new SequenceTypingData(alleles, SEQUENCE_LENGTH);
            largeNodes.add(new Node(seq, i));
        }

        // Create a complete graph (all possible edges)
        List<Edge> largeEdges = new ArrayList<>();
        for (int i = 0; i < largeNodes.size(); i++) {
            for (int j = 0; j < largeNodes.size(); j++) {
                if (i != j) {
                    // Weight based on distance between indices
                    int weight = Math.abs(i - j);
                    largeEdges.add(new Edge(largeNodes.get(i), largeNodes.get(j), weight));
                }
            }
        }

        Graph largeGraph = new Graph(largeEdges);
        
        // Save to temp file
        Path largeTempPath = Files.createTempFile("test_large_graph_", ".mmap");
        String largeTempFile = largeTempPath.toString();
        
        try {
            GraphMapper.saveGraph(largeGraph, SEQUENCE_LENGTH, largeTempFile);
            
            // Use SerializableCameriniForest with lazy loading
            SerializableCameriniForest camerini = new SerializableCameriniForest(
                largeGraph, EDGE_COMPARATOR, largeTempFile, SEQUENCE_LENGTH);
            
            Graph result = camerini.inferPhylogeny(largeGraph);
            
            Assert.assertNotNull(result);
            Assert.assertEquals(largeGraph.getNumNodes() - 1, result.getNumEdges());
            
        } finally {
            // Cleanup
            Files.deleteIfExists(Path.of(largeTempFile));
            Files.deleteIfExists(Path.of(largeTempFile + ".nodes"));
            Files.deleteIfExists(Path.of(largeTempFile + ".edges"));
            Files.deleteIfExists(Path.of(largeTempFile + ".metadata"));
        }
    }
}
