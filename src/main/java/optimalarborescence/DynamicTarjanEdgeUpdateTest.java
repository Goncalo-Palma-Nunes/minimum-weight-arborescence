package optimalarborescence;

import optimalarborescence.graph.*;
import optimalarborescence.inference.InferenceAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.inference.dynamic.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.distance.*;

import java.util.List;
import java.util.ArrayList;

public class DynamicTarjanEdgeUpdateTest {

    /* Graph Parameters */
    private static final List<Node> NODES = new ArrayList<>() {{
        add(new Node("A", 0));
        add(new Node("T", 1));
        add(new Node("C", 2));
        add(new Node("G", 3));
        add(new Node("G", 4)); // Virtual Node
    }};
    private static final int NUM_NODES = NODES.size();
    private static final List<Edge> edges = new ArrayList<>() {{
        add(new Edge(NODES.get(0), NODES.get(1), 2));
        add(new Edge(NODES.get(0), NODES.get(3), 3));
        add(new Edge(NODES.get(1), NODES.get(0), 2));
        add(new Edge(NODES.get(1), NODES.get(3), 10));
        add(new Edge(NODES.get(2), NODES.get(1), 3));
        add(new Edge(NODES.get(2), NODES.get(3), 2));
        add(new Edge(NODES.get(3), NODES.get(2), 2));
        add(new Edge(NODES.get(4), NODES.get(0), 999));
        add(new Edge(NODES.get(4), NODES.get(1), 999));
        add(new Edge(NODES.get(4), NODES.get(2), 999));
        add(new Edge(NODES.get(4), NODES.get(3), 999));
    }};

    public static void main(String[] args) {
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

        // printNeighbours(graph);

        // // // Test edge update with new optimal edge
        Edge edgeToInsert = new Edge(NODES.get(0), NODES.get(3), 1);
        System.out.println("\n--- Testing edge update ---");
        System.out.println("Updating with edge: " + edgeToInsert);

        List<Edge> updatedArborescence = fullyDynamic.updateEdge(edgeToInsert);
        System.out.println("\nUpdated arborescence after insertion:" + new Graph(updatedArborescence));


        // // // Test edge update with new optimal edge
        edgeToInsert = new Edge(NODES.get(3), NODES.get(2), 1);
        System.out.println("\n--- Testing edge update ---");
        System.out.println("Updating with edge: " + edgeToInsert);

        updatedArborescence = fullyDynamic.updateEdge(edgeToInsert);
        System.out.println("\nUpdated arborescence after insertion:" + new Graph(updatedArborescence));

        // // // Test edge update ---> fica preso
        // edgeToInsert = new Edge(NODES.get(2), NODES.get(3), 1);
        // System.out.println("\n--- Testing edge update ---");
        // System.out.println("Updating with edge: " + edgeToInsert);

        // updatedArborescence = fullyDynamic.updateEdge(edgeToInsert);
        // System.out.println("\nUpdated arborescence after insertion:" + new Graph(updatedArborescence));


    }

    public static Graph createGraph() {
        return new Graph(edges);
    }
    
    public static void printNeighbours(Graph graph) {
        for (Node node : graph.getNodes()) {
            System.out.print("Node " + node.getId() + " neighbors: ");
            for (Edge edge : graph.getEdges()) {
                if (edge.getSource().equals(node)) {
                    System.out.print(edge.getDestination().getId() + "(" + edge.getWeight() + ") ");
                }
            }
            System.out.println();
            // System.out.println("\t\tnode.getNeighbors(): " + node.getNeighbors());
        }
    }
}
