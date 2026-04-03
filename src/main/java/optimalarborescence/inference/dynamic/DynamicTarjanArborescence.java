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
    private Map<Integer, Integer> reductions; // Map from node ID to reduction value r_i
    private Graph modifiedGraph;
    protected Map<Integer, Boolean> removedNodes = new HashMap<>(); // Track removed nodes for edge weight adjustments

    /**
     * Constructor for DynamicTarjanArborescence.
     * 
     * @param aTreeRoots The list of ATree root nodes (V' in the partially contracted graph)
     * @param contractedEdges The edges from decomposed contractions (E')
     * @param reducedCosts Map of edges to their reduced costs (w_current(e) = w_original(e) - r_i)
     * @param originalGraph The original graph structure
     */
    public DynamicTarjanArborescence(List<ATreeNode> aTreeRoots, 
                                     List<Edge> contractedEdges, 
                                     Map<Integer, Integer> reducedCosts,
                                     Graph originalGraph, Comparator<Edge> edgeComparator) {
        super(originalGraph, edgeComparator);
        
        this.aTreeRoots = aTreeRoots;
        this.reductions = reducedCosts; // Store reductions for use in getAdjustedWeight method
        this.modifiedGraph = this.graph;
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
     * Returns the ATree roots representing the partially contracted cycles.
     */
    public List<ATreeNode> getATreeRoots() {
        return aTreeRoots;
    }

    public Map<Integer, Integer> getReductions() {
        return reductions;
    }

    /**
     * Marks a node as virtually removed from the graph. Edges to or from this node will have
     * their weights effectively set to infinity by getAdjustedWeight, without modifying the graph structure.
     * @param nodeId The ID of the node to remove
     */
    public void virtuallyRemoveNode(int nodeId) {
        removedNodes.put(nodeId, true);
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
        
        
        boolean isSimpleNode;
        if (tarjanNode.getChildren() != null) {
            isSimpleNode = tarjanNode.getChildren().isEmpty();
        } else {
            isSimpleNode = true; // No children means it's a simple node
        }

        ATreeNode aTreeNode = new ATreeNode(edge, cost, isSimpleNode, null);
        
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

    @Override
    protected Number getAdjustedWeight(Edge e) {
        if (removedNodes.getOrDefault(e.getSource(), false) || removedNodes.getOrDefault(e.getDestination(), false)) {
            return Integer.MAX_VALUE; // Effectively remove edges to/from virtually removed nodes
        }

        // TODO - handle union find weight adjustments here as well
        return e.getWeight() - reductions.getOrDefault(e.getDestination(), 0);
    }
}
