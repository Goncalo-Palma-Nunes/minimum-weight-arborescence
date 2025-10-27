package optimalarborescence.memorymapper;

import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;

import java.io.IOException;

/**
 * Example demonstrating how to migrate from Graph.exportEdgeListAndIndex()
 * to the new memory-mapped GraphMapper API.
 */
public class MigrationExample {
    
    public static void main(String[] args) {
        try {
            // Create a test graph
            Graph graph = createSampleGraph();
            
            System.out.println("=== OLD METHOD (Graph.exportEdgeListAndIndex) ===");
            System.out.println("This method uses DataOutputStream and sorts by source (outgoing edges)");
            
            // Old way (now deprecated in favor of memory-mapped storage)
            String oldEdgeFile = "old_edges.bin";
            String oldIndexFile = "old_index.bin";
            
            graph.exportEdgeListAndIndex(oldEdgeFile, oldIndexFile);
            System.out.println("✓ Saved using old method");
            
            Graph loadedOld = Graph.loadFromEdgeListAndIndex(oldEdgeFile, oldIndexFile);
            System.out.println("✓ Loaded using old method");
            System.out.println("  Edges: " + loadedOld.getNumEdges() + ", Nodes: " + loadedOld.getNumNodes());
            
            System.out.println("\n=== NEW METHOD (GraphMapper) ===");
            System.out.println("This method uses memory-mapped files and sorts by destination (incoming edges)");
            
            // New way (recommended)
            String baseName = "new_graph";
            int mlstLength = 20; // Fixed MLST length for all nodes
            
            GraphMapper.saveGraph(graph, mlstLength, baseName);
            System.out.println("✓ Saved using new method");
            System.out.println("  Created files:");
            System.out.println("    - " + baseName + "_edges.dat (edges sorted by destination)");
            System.out.println("    - " + baseName + "_nodes.dat (node indices)");
            System.out.println("    - " + baseName + "_mlst.dat (MLST data + incoming edge offsets)");
            
            Graph loadedNew = GraphMapper.loadGraph(baseName);
            System.out.println("✓ Loaded using new method");
            System.out.println("  Edges: " + loadedNew.getNumEdges() + ", Nodes: " + loadedNew.getNumNodes());
            
            System.out.println("\n=== ADVANTAGES OF NEW METHOD ===");
            System.out.println("1. Memory-mapped files (faster, less memory for large graphs)");
            System.out.println("2. Sorted by destination (efficient incoming edge queries)");
            System.out.println("3. Efficient random access to any node's data");
            System.out.println("4. Can query incoming edges without loading entire graph");
            
            // Demonstrate efficient querying
            System.out.println("\n=== EFFICIENT INCOMING EDGE QUERY ===");
            int queryNodeId = 2;
            var nodeMap = NodeIndexMapper.loadNodes(baseName + "_nodes.dat", baseName + "_mlst.dat");
            var incomingEdges = GraphMapper.getIncomingEdges(baseName, queryNodeId, mlstLength, nodeMap);
            
            System.out.println("Incoming edges for node " + queryNodeId + ":");
            for (Edge edge : incomingEdges) {
                System.out.println("  " + edge);
            }
            System.out.println("(Retrieved without loading entire graph!)");
            
            System.out.println("\n=== MIGRATION COMPLETE ===");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Create a sample graph for demonstration.
     */
    private static Graph createSampleGraph() {
        Node n0 = new Node("ATCG", 0);
        Node n1 = new Node("GCTA", 1);
        Node n2 = new Node("TGAC", 2);
        Node n3 = new Node("CGAT", 3);
        
        java.util.List<Edge> edges = java.util.List.of(
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
