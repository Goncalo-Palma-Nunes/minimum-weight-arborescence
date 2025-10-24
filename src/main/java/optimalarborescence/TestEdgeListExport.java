package optimalarborescence;

import optimalarborescence.graph.*;
import java.util.List;
import java.util.ArrayList;

public class TestEdgeListExport {
    public static void main(String[] args) {
        try {
            // Create a simple test graph
            List<Node> nodes = new ArrayList<>();
            nodes.add(new Node("A", 0));
            nodes.add(new Node("T", 1));
            nodes.add(new Node("C", 2));
            nodes.add(new Node("G", 3));
            
            List<Edge> edges = new ArrayList<>();
            edges.add(new Edge(nodes.get(0), nodes.get(1), 2));
            edges.add(new Edge(nodes.get(0), nodes.get(3), 3));
            edges.add(new Edge(nodes.get(1), nodes.get(0), 2));
            edges.add(new Edge(nodes.get(2), nodes.get(1), 3));
            edges.add(new Edge(nodes.get(2), nodes.get(3), 2));
            edges.add(new Edge(nodes.get(3), nodes.get(2), 2));
            
            Graph originalGraph = new Graph(edges);
            System.out.println("Original graph:");
            System.out.println(originalGraph);
            
            // Export to files
            String edgeFile = "/tmp/test_edges.bin";
            String indexFile = "/tmp/test_index.bin";
            originalGraph.exportEdgeListAndIndex(edgeFile, indexFile);
            System.out.println("\nExported to " + edgeFile + " and " + indexFile);
            
            // Load from files
            Graph loadedGraph = Graph.loadFromEdgeListAndIndex(edgeFile, indexFile);
            System.out.println("\nLoaded graph:");
            System.out.println(loadedGraph);
            
            // Verify edge counts match
            System.out.println("\nOriginal edges: " + originalGraph.getEdges().size());
            System.out.println("Loaded edges: " + loadedGraph.getEdges().size());
            
            if (originalGraph.getEdges().size() == loadedGraph.getEdges().size()) {
                System.out.println("\n✓ Edge counts match!");
            } else {
                System.out.println("\n✗ Edge counts don't match!");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
