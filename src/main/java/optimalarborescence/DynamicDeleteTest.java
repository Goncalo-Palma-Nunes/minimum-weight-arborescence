package optimalarborescence;

import optimalarborescence.graph.*;
import optimalarborescence.inference.InferenceAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.inference.dynamic.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.distance.*;

import java.util.List;
import java.util.ArrayList;

public class DynamicDeleteTest {
    private static final boolean VERBOSE = true;

    // Note: Test taken from Joaquim Espada's thesis

    /* Graph Parameters */
    private static final List<Node> NODES = new ArrayList<>() {{
        add(new Node("A", 0));
        add(new Node("T", 1));
        add(new Node("C", 2));
        add(new Node("G", 3));
    }};
    private static final int NUM_NODES = NODES.size();
    private static final List<Edge> edges = new ArrayList<>() {{
        add(new Edge(NODES.get(0), NODES.get(1), 6));
        add(new Edge(NODES.get(1), NODES.get(2), 10));
        add(new Edge(NODES.get(1), NODES.get(3), 12));
        add(new Edge(NODES.get(3), NODES.get(0), 1));
        add(new Edge(NODES.get(3), NODES.get(2), 8));
        add(new Edge(NODES.get(2), NODES.get(1), 10));
    }};

    public static void main(String[] args) {
        System.out.println("##################### Fully Dynamic Arborescence Test ############################");
        
        // Create graph and tarjan instance
        Graph graph = createGraph();
        TarjanArborescence tarjan = new TarjanArborescence(graph);
        
        // Initialize FullyDynamicArborescence
        // Note: We need to create ATree roots from the initial inference
        // For now, we'll use an empty list as roots since we're starting fresh
        List<ATreeNode> initialRoots = new ArrayList<>();
        FullyDynamicArborescence fullyDynamic = new FullyDynamicArborescence(
            graph, 
            initialRoots, 
            tarjan
        );
        
        System.out.println("Initial graph:");
        System.out.println(graph);
        
        // Infer initial phylogeny using FullyDynamicArborescence
        System.out.println("\nInferring initial phylogeny with FullyDynamicArborescence...");
        Graph initialArborescence = fullyDynamic.inferPhylogeny(graph);
        System.out.println("\nInitial arborescence:");
        System.out.println(initialArborescence);
        
        // Test edge removal
        Edge edgeToRemove = edges.get(4); // Edge (G, C) with weight 8
        System.out.println("\n--- Testing edge removal ---");
        System.out.println("Removing edge: " + edgeToRemove);
        
        List<Edge> updatedArborescence = fullyDynamic.removeEdge(edgeToRemove);
        
        System.out.println("\nUpdated arborescence after removal:");
        for (Edge e : updatedArborescence) {
            System.out.println("  " + e);
        }

        // // Remove second edge
        Edge secondEdgeToRemove = edges.get(3); // Edge (C, T) with weight 10
        System.out.println("Removing edge: " + secondEdgeToRemove);

        updatedArborescence = fullyDynamic.removeEdge(secondEdgeToRemove);

        System.out.println("\nUpdated arborescence after second removal:");
        for (Edge e : updatedArborescence) {
            System.out.println("  " + e);
        }

        // // System.out.println("##################### End of Fully Dynamic Arborescence Test ###########");
    }

    public static Graph createGraph() {
        return new Graph(edges);
    }
}
