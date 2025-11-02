package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.OnlineAlgorithm;
import optimalarborescence.inference.TarjanArborescence;
import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.exception.NotImplementedException;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
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

    public Graph getGraph() {
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
        Node source = edge.getSource();
        Node destination = edge.getDestination();

        if (!source.getNeighbors().containsKey(destination)) {
            throw new IllegalArgumentException("Edge to update does not exist in the graph.");
        }

        int oldEdgeWeight = source.getNeighbors().get(destination);
        Edge oldEdge = new Edge(source, destination, oldEdgeWeight);

        this.removeEdge(oldEdge);
        source.getNeighbors().put(destination, edge.getWeight());

        return this.addEdge(edge);
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

    /**
     * Expands a partial arborescence by adding the cycle edges from all ATree contractions.
     * 
     * When DynamicTarjanArborescence runs on the partially contracted graph, it returns
     * an arborescence with edges between contracted vertices (ATree nodes). To get the
     * full arborescence on the original graph, we need to add back all the internal
     * cycle edges that were contracted and stored in the ATree nodes.
     * 
     * @param partialArborescence The arborescence edges from the partially contracted graph
     * @return The full arborescence including all cycle edges from contractions
     */
    private List<Edge> expandArborescence(List<Edge> partialArborescence) {
        List<Edge> fullArborescence = new ArrayList<>(partialArborescence);
        
        // Traverse all ATree nodes and collect their contracted edges
        Queue<ATreeNode> queue = new LinkedList<>(this.getRoots());
        
        while (!queue.isEmpty()) {
            ATreeNode current = queue.poll();
            
            // If this is a contraction node (not simple), add its cycle edges
            if (!current.isSimpleNode() && current.getContractedEdges() != null) {
                fullArborescence.addAll(current.getContractedEdges());
            }
            
            // Add children to queue for traversal
            queue.addAll(current.children);
        }
        
        return fullArborescence;
    }

    public void rebuildContractedDigraph() {
        throw new NotImplementedException("The rebuildContractedDigraph method is not implemented.");
    }

    @Override
    public List<Edge> removeEdge(Edge edge) {
        if (edge == null || !this.getGraph().getEdges().contains(edge)) {
            return this.getCurrentArborescence();
        }

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
            // Check if ATree structure exists
            if (this.getRoots().isEmpty()) {
                // No ATree structure - fallback to running full Tarjan
                TarjanArborescence freshTarjan = new TarjanArborescence(this.getGraph());
                Graph updatedArborescence = freshTarjan.inferPhylogeny(this.getGraph());
                this.currentArborescence = updatedArborescence.getEdges();
            } else {
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
                
                // Expand the partial arborescence to include all cycle edges from contractions
                List<Edge> partialEdges = updatedArborescence.getEdges();
                List<Edge> fullEdges = expandArborescence(partialEdges);
                
                // Update the current arborescence
                this.currentArborescence = fullEdges;
            }
        }
        
        return this.getCurrentArborescence();
    }

    @Override
    public List<Edge> addEdge(Edge edge) {
        getGraph().addEdge(edge);

        Node source = edge.getSource();
        Node destination = edge.getDestination();

        TarjanForestNode[] leaves = this.tarjan.getLeaves();
        TarjanForestNode sourceLeaf = leaves[source.getId()];
        TarjanForestNode destLeaf = leaves[destination.getId()];

        TarjanForestNode candidateForRemoval = this.findCandidateForRemoval(destLeaf, edge);
        if (candidateForRemoval != null) {
            // Found a candidate node N which should replace its edge with e_in
            // Determine whether e_in should belong in the "in" cut-set of N
            // by checking if N(source) is in the subtree rooted at N
            
            boolean sourceInSubtree = isNodeInSubtree(sourceLeaf, candidateForRemoval);
            
            if (sourceInSubtree) {
                // N(source) is in the subtree rooted at N
                // e_in should NOT belong in the "in" cut-set of N
                // Simply insert edge in contracted-edges list of LCA
                TarjanForestNode lca = destLeaf.LCA(sourceLeaf);
                if (lca instanceof ATreeNode) {
                    ATreeNode lcaATreeNode = (ATreeNode) lca;
                    lcaATreeNode.addContractedEdge(edge);
                }
            }
            else {
                // N(source) is NOT in the subtree rooted at N
                // e_in should replace the edge of N
                // Engage virtual deletion of N's edge and run Edmonds' algorithm
                
                Edge removedEdge = candidateForRemoval.getEdge();
                
                // Check if ATree structure exists
                if (this.getRoots().isEmpty()) {
                    // No ATree structure - fallback to running full Tarjan
                    // Note: Must create a new Tarjan instance since it maintains internal state
                    TarjanArborescence freshTarjan = new TarjanArborescence(this.getGraph());
                    Graph updatedArborescence = freshTarjan.inferPhylogeny(this.getGraph());
                    this.currentArborescence = updatedArborescence.getEdges();
                } else {
                    // Virtual deletion: recognize G(V', E') without actually removing the edge
                    List<ATreeNode> V = new LinkedList<>(this.getRoots());
                    List<ATreeNode> removedContractions = decompose(removedEdge);
                    List<Edge> contractedEdges = removedContractions.stream()
                            .flatMap(c -> c.getContractedEdges().stream())
                            .toList();
                    
                    // Add e_in to the edge set
                    List<Edge> edgesWithNewEdge = new ArrayList<>(contractedEdges);
                    edgesWithNewEdge.add(edge);
                    
                    // Compute reduction quantities and apply them
                    Map<Integer, Integer> reductions = computeReductionQuantities();
                    Map<Edge, Integer> reducedCosts = applyReductionQuantities(edgesWithNewEdge, reductions);
                    
                    // Run Edmonds' algorithm over G(V', E' ∪ {e_in})
                    DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
                        V,
                        edgesWithNewEdge,
                        reducedCosts,
                        this.getGraph()
                    );
                    
                    Graph updatedArborescence = dynamicTarjan.inferPhylogeny(this.getGraph());
                    
                    // Expand the partial arborescence to include all cycle edges from contractions
                    List<Edge> partialEdges = updatedArborescence.getEdges();
                    List<Edge> fullEdges = expandArborescence(partialEdges);
                    
                    this.currentArborescence = fullEdges;
                }
            }
        }
        else {
            // No candidate for replacement found
            // Insert edge in the contracted-edges list of the LCA
            TarjanForestNode lca = destLeaf.LCA(sourceLeaf);
            if (lca instanceof ATreeNode) {
                ATreeNode lcaATreeNode = (ATreeNode) lca;
                lcaATreeNode.addContractedEdge(edge);
            }
        }

        return this.getCurrentArborescence();
    }

    /**
     * Finds a candidate node for removal when adding a new edge e_in to the arborescence.
     * 
     * Starting from the destination leaf node N(e_in), we follow the path towards the ATree root.
     * For each visited node N, we check whether N.edge.weight > e_in.weight.
     * If this is the case, we have found a candidate node N which should have e_in as its selected edge,
     * because e_in is of lower cost.
     * 
     * @param destLeaf The leaf node representing the destination of the new edge e_in
     * @param edge The new edge e_in being added
     * @return The candidate node for removal if found, null if e_in cannot replace any edge
     */
    private TarjanForestNode findCandidateForRemoval(TarjanForestNode destLeaf, Edge edge) {
        if (destLeaf == null) return null;

        TarjanForestNode current = destLeaf;
        
        // Follow the path from N(e_in) towards the ATree root
        while (current != null) {
            Edge currentEdge = current.getEdge();
            
            // Check if current node's edge weight is greater than the new edge weight
            if (currentEdge != null && currentEdge.getWeight() > edge.getWeight()) {
                // Found a candidate: e_in can replace this edge because it has lower cost
                return current;
            }
            
            // Proceed to parent node
            current = current.getParent();
        }
        
        // Root node reached: e_in cannot replace any edge of the arborescence
        return null;
    }

    /**
     * Checks whether a target node is in the subtree rooted at a given root node using BFS.
     * 
     * This is used to determine if N(source) is hanged in the subtree rooted at the candidate node N.
     * 
     * @param targetNode The node to search for
     * @param rootNode The root of the subtree to search in
     * @return true if targetNode is found in the subtree rooted at rootNode, false otherwise
     */
    private boolean isNodeInSubtree(TarjanForestNode targetNode, TarjanForestNode rootNode) {
        if (targetNode == null || rootNode == null) return false;
        if (targetNode.equals(rootNode)) return true;
        
        // BFS traversal of the subtree
        Queue<TarjanForestNode> queue = new LinkedList<>();
        Set<TarjanForestNode> visited = new HashSet<>();
        queue.add(rootNode);
        visited.add(rootNode);
        
        int iterations = 0;
        while (!queue.isEmpty()) {
            iterations++;
            if (iterations > 10000) {
                System.err.println("WARNING: isNodeInSubtree exceeded 10000 iterations - likely infinite loop!");
                System.err.println("Target: " + targetNode);
                System.err.println("Root: " + rootNode);
                return false;
            }
            
            TarjanForestNode current = queue.poll();
            
            if (current.equals(targetNode)) {
                return true;
            }
            
            // Add all children to the queue
            if (current.getChildren() != null) {
                for (TarjanForestNode child : current.getChildren()) {
                    if (!visited.contains(child)) {
                        queue.add(child);
                        visited.add(child);
                    }
                }
            }
        }
        
        return false;
    }
    
}
