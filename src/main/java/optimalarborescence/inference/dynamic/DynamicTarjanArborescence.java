package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Dynamic adaptation of Tarjan's algorithm that works with partially contracted graphs.
 * 
 * This class is initialized with:
 * - V': The list of ATree root nodes representing the partially contracted vertices
 * - E': The edges from decomposed contractions with proper reduced costs
 * - The original graph structure
 * 
 * It leverages the existing contraction state to avoid redundant work by creating
 * a modified graph with edges having reduced costs and then running Tarjan's algorithm.
 */
public class DynamicTarjanArborescence extends CameriniForest {

    private List<ATreeNode> aTreeRoots;
    private Map<Edge, Integer> reducedCosts;
    private Graph modifiedGraph;

    /**
     * Constructor for DynamicTarjanArborescence.
     * 
     * @param aTreeRoots The list of ATree root nodes (V' in the partially contracted graph)
     * @param contractedEdges The edges from decomposed contractions (E')
     * @param reducedCosts Map from edges to their reduced costs w_current(e) = w_original(e) - r_i
     * @param originalGraph The original graph structure
     */
    public DynamicTarjanArborescence(List<ATreeNode> aTreeRoots, 
                                     List<Edge> contractedEdges, 
                                     Map<Edge, Integer> reducedCosts,
                                     Graph originalGraph, Comparator<Edge> edgeComparator) {
        // Create a modified graph with reduced costs
        super(createModifiedGraph(contractedEdges, reducedCosts, originalGraph), edgeComparator);
        
        this.aTreeRoots = aTreeRoots;
        this.reducedCosts = reducedCosts;
        this.modifiedGraph = this.graph;
    }

    /**
     * Creates a modified graph with edges that have reduced costs applied.
     * 
     * This graph represents G' = (V, E') where E' contains edges with costs
     * w_current(e) = w_original(e) - r_i.
     * 
     * @param contractedEdges The edges from E'
     * @param reducedCosts The map of reduced costs
     * @param originalGraph The original graph
     * @return A new graph with reduced-cost edges
     */
    private static Graph createModifiedGraph(List<Edge> contractedEdges, 
                                            Map<Edge, Integer> reducedCosts,
                                            Graph originalGraph) {
        List<Edge> modifiedEdges = new ArrayList<>();
        
        // include all edges from the original graph to preserve node order
        // This ensures the Graph constructor adds nodes in the correct order
        for (Edge edge : originalGraph.getEdges()) {
            // Check if this edge should have a reduced cost
            if (contractedEdges.contains(edge)) {
                int reducedCost = reducedCosts.getOrDefault(edge, edge.getWeight());
                Edge reducedEdge = new Edge(edge.getSource(), edge.getDestination(), reducedCost);
                modifiedEdges.add(reducedEdge);
            } else {
                modifiedEdges.add(edge);
            }
        }
        
        return new Graph(modifiedEdges);
    }

    /**
     * Runs the Tarjan algorithm on the partially contracted graph with reduced costs.
     */
    @Override
    public Graph inferPhylogeny(Graph graph) {
        // Simply run the parent class's algorithm on the modified graph
        Graph result = super.inferPhylogeny(modifiedGraph);
        
        System.out.println("DynamicTarjanArborescence: completed inference on partially contracted graph");
        
        return result;
    }

    /**
     * Returns the ATree roots representing the partially contracted vertices.
     */
    public List<ATreeNode> getATreeRoots() {
        return aTreeRoots;
    }

    /**
     * Returns the map of reduced costs for edges.
     */
    public Map<Edge, Integer> getReducedCosts() {
        return reducedCosts;
    }

    /**
     * Returns the modified graph with reduced costs.
     */
    public Graph getModifiedGraph() {
        return modifiedGraph;
    }

    /**
     * Converts the TarjanForestNode forest from CameriniForest to an ATreeNode forest.
     * 
     * This method extracts the forest structure built by CameriniForest during the
     * contraction phase and converts each TarjanForestNode to an ATreeNode with:
     * - The same edge
     * - Cost (y) set to the edge's weight at selection time
     * - All nodes initially marked as simple nodes (no contractions yet)
     * - Parent-child relationships preserved
     * 
     * The converted ATree roots are stored in this.aTreeRoots for use in dynamic operations.
     */
    public List<ATreeNode> augmentTarjanForestToATree() {
        // Get the TarjanForestNode roots from CameriniForest
        List<TarjanForestNode> tarjanRoots = this.getRoots();
        
        // Initialize roots if null, otherwise clear existing ATree roots
        if (this.aTreeRoots == null) {
            this.aTreeRoots = new ArrayList<>();
        } else {
            this.aTreeRoots.clear();
        }
        
        // Map to track already converted nodes (to handle shared references)
        Map<TarjanForestNode, ATreeNode> conversionMap = new HashMap<>();
        
        // Convert each root and its subtree
        for (TarjanForestNode tarjanRoot : tarjanRoots) {
            if (tarjanRoot != null) {
                ATreeNode aTreeRoot = convertTarjanNodeToATreeNode(tarjanRoot, conversionMap);
                this.aTreeRoots.add(aTreeRoot);
            }
        }
        return this.aTreeRoots;
    }
    
    /**
     * Recursively converts a TarjanForestNode and its entire subtree to ATreeNodes.
     * 
     * @param tarjanNode The TarjanForestNode to convert
     * @param conversionMap Map tracking already converted nodes
     * @return The corresponding ATreeNode
     */
    private ATreeNode convertTarjanNodeToATreeNode(TarjanForestNode tarjanNode, 
                                                     Map<TarjanForestNode, ATreeNode> conversionMap) {
        // Check if already converted
        if (conversionMap.containsKey(tarjanNode)) {
            return conversionMap.get(tarjanNode);
        }
        
        Edge edge = tarjanNode.getEdge();
        int cost = (edge != null) ? edge.getWeight() : 0;
        
        // Create new ATreeNode as a simple node (no contractions initially)
        // Parent will be set later during recursion
        ATreeNode aTreeNode = new ATreeNode(edge, cost, true, null);
        
        // Register in map before processing children (to handle cycles/shared references)
        conversionMap.put(tarjanNode, aTreeNode);
        
        // Convert all children recursively
        List<ATreeNode> aTreeChildren = new ArrayList<>();
        if (tarjanNode.getChildren() != null) {
            for (TarjanForestNode child : tarjanNode.getChildren()) {
                ATreeNode aTreeChild = convertTarjanNodeToATreeNode(child, conversionMap);
                aTreeChild.setParent(aTreeNode);  // Set parent relationship
                aTreeChildren.add(aTreeChild);
            }
        }
        
        // Set children list
        aTreeNode.setChildren(aTreeChildren);
        
        return aTreeNode;
    }
}
