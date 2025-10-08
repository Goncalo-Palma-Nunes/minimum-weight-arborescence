package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.OnlineAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.graph.Graph;
import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.graph.Edge;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;

public class FullyDynamicArborescence extends OnlineAlgorithm {

    /* Note: A Digraph is another term for a directed graph */

    List<ATreeNode> roots;
    TarjanArborescence tarjan;
    List<Edge> currentArborescence; // TODO - formar eficiente de indexar as arestas para não percorrer linearmente durante o DELETE

    /**
     * Constructor for FullyDynamicArborescence.
     * 
     * @param graph The original graph
     * @param roots The list of ATree root nodes representing the partially contracted vertices
     * @param tarjan An instance of Tarjan's algorithm to be used for inference
     */
    public FullyDynamicArborescence(Graph graph, List<ATreeNode> roots, TarjanArborescence tarjan) {
        super(graph);
        this.roots = roots;
        this.tarjan = tarjan;
    }

    public List<ATreeNode> getRoots() {
        return roots;
    }

    private TarjanArborescence getInferenceAlgorithm() {
        return tarjan;
    }

    public List<Edge> getCurrentArborescence() {
        return currentArborescence;
    }

    protected Graph getGraph() {
        return graph;
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        Graph g = this.getInferenceAlgorithm().inferPhylogeny(graph);
        this.currentArborescence = g.getEdges();
        return g;
    }

    @Override
    public List<Edge> updateEdge(Edge edge) {
        throw new NotImplementedException("The updateEdge method is not implemented.");
    }

    private List<ATreeNode> decompose(Edge e) {
        ATreeNode N = null;
        for (ATreeNode root : this.getRoots()) {
            N = root.findATreeNodeByEdge(e, root);
            if (N != null) break;
        }

        List<ATreeNode> removedContractions = new LinkedList<>();
        while (N != null && !N.isRoot()) {

            if (!N.isSimpleNode()) {
                List<ATreeNode> children = N.children;
                for (ATreeNode child : children) {
                    child.setParent(null); // Child becomes a root of its own ATree
                    this.roots.add(child);
                }
                children.clear();
                removedContractions.add(N);
            }
            N.parent.children.remove(N);
            ATreeNode parentN = N.parent;
            N.setParent(null);
            N = parentN;
        }
        return removedContractions;
    }

    /**
     * Computes the reduction quantities r_i for all simple nodes in the ATrees using BFS.
     * 
     * For each simple node N_i, r_i is the sum of costs (y values) along the path from N_i to its root.
     * This runs in O(|V|) time by performing a single BFS on each ATree.
     * 
     * @return A map from vertex ID to reduction quantity r_i
     */
    private Map<Integer, Integer> computeReductionQuantities() {
        Map<Integer, Integer> reductions = new HashMap<>();
        
        // Process each ATree root
        for (ATreeNode root : roots) {
            computeReductionQuantitiesBFS(root, reductions);
        }
        
        return reductions;
    }

    /**
     * BFS traversal to compute reduction quantities for all nodes in a single ATree.
     * Each node accumulates the cost from its parent, so we compute r_i = sum of costs from root to N_i.
     */
    private void computeReductionQuantitiesBFS(ATreeNode root, Map<Integer, Integer> reductions) {
        if (root == null) return;
        
        Queue<ATreeNode> queue = new LinkedList<>();
        Map<ATreeNode, Integer> accumulatedCost = new HashMap<>();
        
        // Start with root: accumulated cost is its own cost (or 0 if it's the root with no edge)
        queue.add(root);
        accumulatedCost.put(root, root.isRoot() ? 0 : root.getCost());
        
        while (!queue.isEmpty()) {
            ATreeNode current = queue.poll();
            int currentAccumulated = accumulatedCost.get(current);
            
            // If this is a simple node with an edge, record its reduction quantity
            if (current.isSimpleNode() && current.getEdge() != null) {
                int vertexId = current.getEdge().getDestination().getId();
                reductions.put(vertexId, currentAccumulated);
            }
            
            // Process children: their accumulated cost is current + their own cost
            for (ATreeNode child : current.children) {
                int childAccumulated = currentAccumulated + child.getCost();
                accumulatedCost.put(child, childAccumulated);
                queue.add(child);
            }
        }
    }

    /**
     * Applies reduction quantities to compute the proper reduced costs for edges in E'.
     * 
     * For each edge e = (u, v_i) in edges, computes: w_current(e) = w_original(e) - r_i
     * where r_i is the reduction quantity for vertex v_i.
     * 
     * @param edges The list of edges in E' (edges from the partially contracted graph)
     * @param reductions The map of reduction quantities r_i for each vertex
     * @return A map from each edge to its reduced cost
     */
    private Map<Edge, Integer> applyReductionQuantities(List<Edge> edges, Map<Integer, Integer> reductions) {
        Map<Edge, Integer> reducedCosts = new HashMap<>();
        
        for (Edge edge : edges) {
            int vertexId = edge.getDestination().getId();
            int originalWeight = edge.getWeight();
            int reductionQuantity = reductions.getOrDefault(vertexId, 0);
            int reducedCost = originalWeight - reductionQuantity;
            
            reducedCosts.put(edge, reducedCost);
        }
        
        return reducedCosts;
    }

    public void rebuildContractedDigraph() {
        throw new NotImplementedException("The rebuildContractedDigraph method is not implemented.");
    }

    @Override
    public List<Edge> removeEdge(Edge edge) {
        getGraph().removeEdge(edge);
        if (!this.getCurrentArborescence().contains(edge)) {

            ATreeNode contraction = null;
            for (ATreeNode root : this.getRoots()) {
                contraction = root.findContractionByEdge(edge, root);
                if (contraction != null) break;
            }

            if (contraction != null) {
                // Handle the contraction
                contraction.getContractedEdges().remove(edge);
            }
        }
        else {
            List<ATreeNode> V = new LinkedList<>(this.getRoots()); // V' in Joaquim's thesis
            List<ATreeNode> removedContractions = decompose(edge); // set R in Joaquim's thesis
            List<Edge> edges = removedContractions.stream()
                    .flatMap(c -> c.getContractedEdges().stream())
                    .toList(); // Union E' of all contracted edges from decomposed non-simple nodes

            // Compute reduction quantities for all vertices in O(|V|) time
            Map<Integer, Integer> reductions = computeReductionQuantities();
            
            // Apply reduction quantities to edges in E'
            Map<Edge, Integer> reducedCosts = applyReductionQuantities(edges, reductions);
            
            // Initialize DynamicTarjanArborescence with the partially contracted graph
            DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
                V,                  // ATree roots representing V'
                edges,              // Edges from E'
                reducedCosts,       // Reduced costs for edges
                this.getGraph()     // Original graph
            );
            
            // Run Tarjan's algorithm on the partially contracted graph
            Graph updatedArborescence = dynamicTarjan.inferPhylogeny(this.getGraph());
            
            // Update the current arborescence
            this.currentArborescence = updatedArborescence.getEdges();
        }
        
        return this.getCurrentArborescence();
    }

    @Override
    public List<Edge> addEdge(Edge edge) {
        throw new NotImplementedException("The addEdge method is not implemented.");
    }
    
}
