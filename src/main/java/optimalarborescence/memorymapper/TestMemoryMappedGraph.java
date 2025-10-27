package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Test class to verify memory-mapped graph storage.
 */
public class TestMemoryMappedGraph {
    
    public static void main(String[] args) {
        try {
            // Create a sample graph
            System.out.println("Creating test graph...");
            Graph graph = createTestGraph();
            
            System.out.println("Original graph:");
            System.out.println(graph);
            System.out.println("\nOriginal edges count: " + graph.getNumEdges());
            System.out.println("Original nodes count: " + graph.getNumNodes());
            
            // Save to memory-mapped files
            String baseName = "test_graph";
            int mlstLength = 20; // Fixed MLST data length
            
            System.out.println("\n=== Saving graph to memory-mapped files ===");
            GraphMapper.saveGraph(graph, mlstLength, baseName);
            System.out.println("Graph saved successfully!");
            
            // Load from memory-mapped files
            System.out.println("\n=== Loading graph from memory-mapped files ===");
            Graph loadedGraph = GraphMapper.loadGraph(baseName);
            System.out.println("Graph loaded successfully!");
            
            System.out.println("\nLoaded graph:");
            System.out.println(loadedGraph);
            System.out.println("\nLoaded edges count: " + loadedGraph.getNumEdges());
            System.out.println("Loaded nodes count: " + loadedGraph.getNumNodes());
            
            // Test individual operations
            System.out.println("\n=== Testing individual node operations ===");
            Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(
                baseName + "_nodes.dat", baseName + "_mlst.dat");
            
            for (int nodeId = 0; nodeId < 4; nodeId++) {
                long offset = GraphMapper.getIncomingEdgeOffset(baseName, nodeId, mlstLength);
                System.out.println("Node " + nodeId + " incoming edge offset: " + offset);
                
                if (offset >= 0) {
                    List<Edge> incomingEdges = GraphMapper.getIncomingEdges(
                        baseName, nodeId, mlstLength, nodeMap);
                    System.out.println("  Incoming edges for node " + nodeId + ": " + incomingEdges.size());
                    for (Edge edge : incomingEdges) {
                        System.out.println("    " + edge);
                    }
                }
            }
            
            // Verify edge order (sorted by destination)
            System.out.println("\n=== Verifying edge order (sorted by destination) ===");
            List<Edge> edges = EdgeListMapper.loadEdgesFromMappedFile(
                baseName + "_edges.dat", nodeMap);
            
            int prevDest = -1;
            boolean sorted = true;
            for (Edge edge : edges) {
                int dest = edge.getDestination().getID();
                if (dest < prevDest) {
                    sorted = false;
                    System.out.println("ERROR: Edges not sorted! " + prevDest + " -> " + dest);
                }
                prevDest = dest;
                System.out.println(edge);
            }
            
            if (sorted) {
                System.out.println("\n✓ Edges are correctly sorted by destination!");
            }
            
            System.out.println("\n=== Test completed successfully! ===");
            
        } catch (IOException e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Create a test graph with known structure.
     * Graph structure:
     *   0 -> 1 (weight 5)
     *   0 -> 2 (weight 3)
     *   1 -> 2 (weight 2)
     *   1 -> 3 (weight 4)
     *   2 -> 3 (weight 1)
     *   3 -> 0 (weight 6)
     */
    private static Graph createTestGraph() {
        Node n0 = new Node("ATCG", 0);
        Node n1 = new Node("GCTA", 1);
        Node n2 = new Node("TGAC", 2);
        Node n3 = new Node("CGAT", 3);
        
        List<Edge> edges = List.of(
            new Edge(n0, n1, 5),
            new Edge(n0, n2, 3),
            new Edge(n1, n2, 2),
            new Edge(n1, n3, 4),
            new Edge(n2, n3, 1),
            new Edge(n3, n0, 6)
        );
        
        return new Graph(edges);
    }
}
