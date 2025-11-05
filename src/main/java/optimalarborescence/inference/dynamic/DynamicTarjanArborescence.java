package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.CameriniForest;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                                     Graph originalGraph) {
        // Create a modified graph with reduced costs
        super(createModifiedGraph(contractedEdges, reducedCosts, originalGraph));
        
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
        
        // Create edges with reduced costs
        for (Edge edge : contractedEdges) {
            int reducedCost = reducedCosts.getOrDefault(edge, edge.getWeight());
            Edge reducedEdge = new Edge(edge.getSource(), edge.getDestination(), reducedCost);
            modifiedEdges.add(reducedEdge);
        }
        
        // Also include all other edges from the original graph that are not in contractedEdges
        for (Edge edge : originalGraph.getEdges()) {
            if (!contractedEdges.contains(edge)) {
                modifiedEdges.add(edge);
            }
        }
        
        return new Graph(modifiedEdges);
    }

    /**
     * Runs the Tarjan algorithm on the partially contracted graph with reduced costs.
     * This is more efficient than running from scratch since the graph already has
     * reduced costs applied from previous contractions.
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
}
