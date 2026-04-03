package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.OnlineAlgorithm;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.exception.NotImplementedException;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.Comparator;
import java.util.stream.Collectors;

public class FullyDynamicArborescence extends OnlineAlgorithm {

    List<ATreeNode> roots;
    Comparator<Edge> edgeComparator;
    DynamicTarjanArborescence camerini;
    List<Edge> currentArborescence;
    Map<Integer, Integer> reductions = new HashMap<>(); // Map from vertex ID to reduction quantity r_i
    UnionFind wccUf = null; // Union-Find for maintaining weakly connected components in the contracted graph
    UnionFindStronglyConnected sccUf = null; // Union-Find for maintaining strongly connected components in the contracted graph
    int numVertices = 0; // Track number of vertices in the graph for Union-Find initialization
    List<TarjanForestNode> leaves = new ArrayList<>();

    /**  Array that for each i stores a node from the forest which is associated with the minimum weight edge incident in node i */
    List<TarjanForestNode> inEdgeNode = new ArrayList<>();


    public FullyDynamicArborescence() {
        super();
        this.roots = new LinkedList<>();
        this.camerini = null;
        this.edgeComparator = (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());
        this.currentArborescence = new ArrayList<>();
    }

    /**
     * Constructor for FullyDynamicArborescence.
     * 
     * @param graph The original graph
     * @param roots The list of ATree root nodes representing the partially contracted vertices
     * @param camerini An instance of camerini's algorithm to be used for inference
     */
    public FullyDynamicArborescence(Graph graph, List<ATreeNode> roots, DynamicTarjanArborescence camerini) {
        super(graph);
        this.roots = roots;
        this.camerini = camerini;
        this.edgeComparator = (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());
        this.currentArborescence = new ArrayList<>();
    }

    public List<ATreeNode> getRoots() {
        return roots;
    }

    private CameriniForest getInferenceAlgorithm() {
        return camerini;
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
        this.roots = camerini.augmentTarjanForestToATree();
        
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

    /**
     * Resets the union-find structures for weakly and strongly connected components. 
     * This should be called after any decomposition step that modifies the ATree structure, as any previous 
     * union-find state may no longer be valid due to changes in the contracted graph's structure. 
     * <p>
     * The partial union-find state can be recomputed from the decomposed state through a BFS traversal of the ATree roots.
     */
    private void resetUnionFinds() {
        if (this.wccUf != null) {
            this.wccUf.clear();
        }
        else {
            this.wccUf = new UnionFind(this.numVertices);
        }

        if (this.sccUf != null) {
            this.sccUf.clear();
        }
        else {
            this.sccUf = new UnionFindStronglyConnected(this.numVertices);
        }
    }

    /**
     * Performs a union operation on the weakly connected components union-find structure for the given nodes u and v.
     * @param u The first node
     * @param v The second node
     */
    private void wccUnion(Node u, Node v) {
        wccUf.union(u.getId(), v.getId());
    }

    /**
     * Performs a union operation on the strongly connected components union-find structure for the given nodes u and v.
     * @param u The first node
     * @param v The second node
     */
    private void sccUnion(Node u, Node v) {
        sccUf.union(u.getId(), v.getId());
    }

    private List<ATreeNode> decompose(Edge e) {
        ATreeNode N = null;
        for (ATreeNode root : this.getRoots()) {
            N = root.findATreeNodeByEdge(e, root);
            if (N != null) break;
        }

        List<ATreeNode> removedContractions = new LinkedList<>();
        while (N != null && !N.isRoot()) { // deveria incluir a raíz também?

            if (!N.isSimpleNode()) {
                List<ATreeNode> children = N.getATreeChildren();
                for (ATreeNode child : children) {
                    child.setParent(null); // Child becomes a root of its own ATree
                    this.roots.add(child);
                }
                N.clearChildren();
                removedContractions.add(N);
            }
            
            // Save parent before modifying relationships
            ATreeNode parentN = N.getParent();
            
            // Remove N from its parent's children list (parent is guaranteed non-null here since !N.isRoot())
            if (parentN != null && parentN.getChildren() != null) {
                parentN.getChildren().remove(N);
            }
            
            // Detach N from parent
            N.setParent(null);
            N = parentN;
        }
        return removedContractions;
    }

    /**
     * Computes the reduction quantities r_i for all simple nodes in the ATrees using BFS. Also performs
     * union operations on the union-find structures to obtain the correct disjoint sets for the partially contracted graph 
     * to be run with Edmonds' algorithm.
     * <p>
     * For each simple node N_i, r_i is the sum of costs (y values) along the path from N_i to its root.
     * This runs in O(|V|) time by performing a single BFS on each ATree.
     * 
     * @return A map from vertex ID to reduction quantity r_i
     */
    private Map<Integer, Integer> computeReductionQuantities() {
        // Process each ATree root
        for (ATreeNode root : roots) {
            computeReductionQuantitiesBFS(root);
        }
        
        return reductions;
    }

    /**
     * BFS traversal to compute reduction quantities for all nodes in a single ATree. Also performs
     * union operations on the union-find structures to obtain the correct disjoint sets for the partially contracted graph 
     * to be run with Edmonds' algorithm.
     * <p>
     * Each node accumulates the cost from its parent, so we compute r_i = sum of costs from root to N_i.
     */
    private void computeReductionQuantitiesBFS(ATreeNode root) {
        if (root == null) return;
        
        Queue<ATreeNode> queue = new LinkedList<>();
        Queue<Integer> accumulatedCosts = new LinkedList<>(); // Parallel queue to track accumulated costs
        Map<ATreeNode, Integer> accumulatedCost = new HashMap<>();
        
        // accumulated cost is its own cost (or 0 if it's the root with no edge)
        queue.add(root);
        accumulatedCost.put(root, root.isRoot() ? 0 : root.getCost());
        accumulatedCosts.add(accumulatedCost.get(root));
        
        while (!queue.isEmpty()) {
            ATreeNode current = queue.poll();
            int currentAccumulated = accumulatedCosts.poll();
            // int currentAccumulated = accumulatedCost.get(current);
            
            // If this is a simple node with an edge, record its reduction quantity
            if (current.getEdge() != null) {
                Edge edge = current.getEdge();
                wccUnion(edge.getSource(), edge.getDestination());
                if (current.isSimpleNode()) {
                    int vertexId = current.getEdge().getDestination().getId();
                    reductions.put(vertexId, currentAccumulated);
                    
                    // simple nodes are leaves of the ATree
                    leaves.add(current);
                }
                else {
                    // perform the strongly connected union of all vertices in the cycle represented by this non-simple node
                    sccUnion(edge.getSource(), edge.getDestination());

                    // Perform a union for each contracted vertex in the cycle
                    if (current.getContractedVertices() != null) {
                        for (Integer contractedVertexId : current.getContractedVertices().keySet()) {
                            sccUnion(edge.getSource(), new Node(contractedVertexId));
                            sccUnion(edge.getDestination(), new Node(contractedVertexId));
                        }
                    }
                }
            }
            
            // Process children: their accumulated cost is current + their own cost
            for (ATreeNode child : current.getATreeChildren()) {
                int childAccumulated = currentAccumulated + child.getCost();
                accumulatedCost.put(child, childAccumulated);
                accumulatedCosts.add(childAccumulated);
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

    private List<Edge> firstStaticInference(Graph g) {
        DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
            this.getRoots(),
            g.getEdges(),
            new HashMap<>(),
            g,
            this.edgeComparator
        );
        this.camerini = dynamicTarjan; // Update camerini reference
        Graph updatedArborescence = dynamicTarjan.inferPhylogeny(g);
        this.roots = dynamicTarjan.augmentTarjanForestToATree();
        return updatedArborescence.getEdges();
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
                // DO NOTHING HERE - the edge will be effectively removed from the contracted graph by the getAdjustedWeight method
            }
        }
        else {
            // Check if ATree structure exists
            if (this.getRoots().isEmpty()) {
                // No ATree structure - fallback to running full Tarjan
                this.currentArborescence = firstStaticInference(this.getGraph());
            } else {
                List<ATreeNode> removedContractions = decompose(edge); // set R in Joaquim's thesis
                resetUnionFinds(); // Reset Union-Find structures before recomputing reductions and running Edmonds

                List<ATreeNode> V = new LinkedList<>(this.getRoots()); // V' in Joaquim's thesis
                
                // TODO - placeholder until I implement serializable version
                List<Edge> edges = new ArrayList<>(); // E' in Joaquim's thesis


                // Compute reduction quantities for all vertices in O(|V|) time
                Map<Integer, Integer> reductions = computeReductionQuantities();
                
                // Initialize DynamicTarjanArborescence with the partially contracted graph
                DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
                    V,                  // ATree roots
                    edges,              // Edges from E'
                    reductions,       // Reduced costs for edges
                    this.getGraph(),    // Original graph
                    this.edgeComparator // Edge comparator
                );
                this.camerini = dynamicTarjan; // Update camerini reference
                
                // Run Tarjan's algorithm on the partially contracted graph
                Graph updatedArborescence = dynamicTarjan.inferPhylogeny(this.getGraph());
                this.roots = dynamicTarjan.augmentTarjanForestToATree();
                
                // Update the current arborescence
                this.currentArborescence = updatedArborescence.getEdges();
            }
        }
        
        return this.getCurrentArborescence();
    }

    @Override
    public List<Edge> addEdge(Edge edge) {
        getGraph().addEdge(edge);

        Node source = edge.getSource();
        Node destination = edge.getDestination();

        TarjanForestNode[] leaves = this.camerini.getLeaves();
        
        // Check if leaves are initialized and accessible
        if (leaves == null || source.getId() >= leaves.length || destination.getId() >= leaves.length) {
            // Leaves not initialized - need to run full algorithm
            this.currentArborescence = firstStaticInference(this.getGraph());
            return this.getCurrentArborescence();
        }
        
        TarjanForestNode sourceLeaf = leaves[source.getId()];
        TarjanForestNode destLeaf = leaves[destination.getId()];
        
        // Check if the specific leaves for these nodes are null
        if (sourceLeaf == null || destLeaf == null) {
            // Leaves not properly initialized for these nodes - run full algorithm
            this.currentArborescence = firstStaticInference(this.getGraph());
            return this.getCurrentArborescence();
        }

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
                if (lca instanceof ATreeNode) {  // TODO - substituir por um downcast
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
                    this.currentArborescence = firstStaticInference(this.getGraph());
                } else {
                    // Virtual deletion: recognize G(V', E') without actually removing the edge
                    List<ATreeNode> V = new LinkedList<>(this.getRoots());
                    List<ATreeNode> removedContractions = decompose(removedEdge);
                    resetUnionFinds(); // Reset Union-Find structures before recomputing reductions and running Edmonds
                    
                    // Placeholder
                    List<Edge> contractedEdges = new ArrayList<>();
                    
                    // Add e_in to the edge set
                    List<Edge> edgesWithNewEdge = new ArrayList<>(contractedEdges);
                    edgesWithNewEdge.add(edge);
                    
                    // Compute reduction quantities and apply them ONLY to edges already in G' (contractedEdges)
                    // The new edge e_in should use its original weight, not a reduced cost
                    Map<Integer, Integer> reductions = computeReductionQuantities();
                    
                    // Run Edmonds' algorithm over G(V', E' ∪ {e_in})
                    DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
                        V,
                        edgesWithNewEdge,
                        reductions,
                        this.getGraph(),
                        this.edgeComparator
                    );
                    this.camerini = dynamicTarjan; // Update camerini reference
                    
                    Graph updatedArborescence = dynamicTarjan.inferPhylogeny(this.getGraph());
                    this.roots = dynamicTarjan.augmentTarjanForestToATree();

                    List<Edge> fullEdges = updatedArborescence.getEdges();
                    
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

    @Override
    public List<Edge> removeEdges(List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            return this.getCurrentArborescence();
        }

        // Remove all edges from the graph upfront
        for (Edge edge : edges) {
            if (edge != null && this.getGraph().getEdges().contains(edge)) {
                getGraph().removeEdge(edge);
            }
        }

        List<Edge> currentArb = this.getCurrentArborescence();

        // Partition: arborescence edges vs non-arborescence edges
        // Use List.contains (equals-based) since Edge.hashCode() is not overridden
        List<Edge> arborescenceEdges = new ArrayList<>();
        List<Edge> nonArborescenceEdges = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge == null) continue;
            if (currentArb.contains(edge)) {
                arborescenceEdges.add(edge);
            } else {
                nonArborescenceEdges.add(edge);
            }
        }

        // Cheap path: remove non-arborescence edges from their c-nodes' contracted edge lists
        for (Edge edge : nonArborescenceEdges) {
            for (ATreeNode root : this.getRoots()) {
                ATreeNode contraction = root.findContractionByEdge(edge, root);
                if (contraction != null) {
                    contraction.getContractedEdges().remove(edge);
                    break;
                }
            }
        }

        // Expensive path: decompose all arborescence edges, then run inference once
        if (!arborescenceEdges.isEmpty()) {
            if (this.getRoots().isEmpty()) {
                this.currentArborescence = firstStaticInference(this.getGraph());
            } else {
                List<ATreeNode> allRemovedContractions = new ArrayList<>();
                for (Edge edge : arborescenceEdges) {
                    List<ATreeNode> removed = decompose(edge);
                    allRemovedContractions.addAll(removed);
                }
                resetUnionFinds(); // Reset Union-Find structures before recomputing reductions and running Edmonds

                // Collect contracted edges from all decomposed c-nodes,
                // filtering out any edges that are in the removal batch
                // Use List.contains (equals-based) since Edge.hashCode() is not overridden
                List<Edge> contractedEdges = allRemovedContractions.stream()
                        .flatMap(c -> c.getContractedEdges().stream())
                        .filter(e -> !edges.contains(e))
                        .collect(Collectors.toList());

                // V' is captured after all decompositions (this.roots has been updated)
                List<ATreeNode> V = new LinkedList<>(this.getRoots());

                Map<Integer, Integer> reductions = computeReductionQuantities();
                Map<Edge, Integer> reducedCosts = applyReductionQuantities(contractedEdges, reductions);

                DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
                    V,
                    contractedEdges,
                    reducedCosts,
                    this.getGraph(),
                    this.edgeComparator
                );
                this.camerini = dynamicTarjan;

                Graph updatedArborescence = dynamicTarjan.inferPhylogeny(this.getGraph());
                this.roots = dynamicTarjan.augmentTarjanForestToATree();

                List<Edge> fullEdges = expandArborescence(updatedArborescence.getEdges());
                this.currentArborescence = fullEdges;
            }
        }

        return this.getCurrentArborescence();
    }

    @Override
    public List<Edge> addEdges(List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            return this.getCurrentArborescence();
        }

        // Add all edges to the graph upfront
        for (Edge edge : edges) {
            getGraph().addEdge(edge);
        }

        // Validate leaves — if any source/dest has no leaf, fall back to full static inference
        TarjanForestNode[] leaves = this.camerini.getLeaves();
        if (leaves == null) {
            this.currentArborescence = firstStaticInference(this.getGraph());
            return this.getCurrentArborescence();
        }
        for (Edge edge : edges) {
            Node src = edge.getSource();
            Node dst = edge.getDestination();
            if (src.getId() >= leaves.length || dst.getId() >= leaves.length
                    || leaves[src.getId()] == null || leaves[dst.getId()] == null) {
                this.currentArborescence = firstStaticInference(this.getGraph());
                return this.getCurrentArborescence();
            }
        }

        List<ATreeNode> allRemovedContractions = new ArrayList<>();
        List<Edge> triggeringEdges = new ArrayList<>();
        boolean needsInference = false;

        for (Edge edge : edges) {
            Node source = edge.getSource();
            Node destination = edge.getDestination();

            TarjanForestNode sourceLeaf = leaves[source.getId()];
            TarjanForestNode destLeaf = leaves[destination.getId()];

            TarjanForestNode candidateForRemoval = this.findCandidateForRemoval(destLeaf, edge);

            if (candidateForRemoval != null) {
                boolean sourceInSubtree = isNodeInSubtree(sourceLeaf, candidateForRemoval);

                if (sourceInSubtree) {
                    // Case 1: source is inside the candidate's subtree — add to LCA's contractedEdges
                    TarjanForestNode lca = destLeaf.LCA(sourceLeaf);
                    if (lca instanceof ATreeNode) {
                        ((ATreeNode) lca).addContractedEdge(edge);
                    }
                } else {
                    // Case 2: source is outside — virtual deletion, defer inference
                    if (this.getRoots().isEmpty()) {
                        this.currentArborescence = firstStaticInference(this.getGraph());
                        return this.getCurrentArborescence();
                    }
                    Edge removedEdge = candidateForRemoval.getEdge();
                    List<ATreeNode> removed = decompose(removedEdge);
                    resetUnionFinds(); // Reset Union-Find structures before recomputing reductions and running Edmonds
                    allRemovedContractions.addAll(removed);
                    triggeringEdges.add(edge);
                    needsInference = true;
                }
            } else {
                // Case 3: no candidate — add to LCA's contractedEdges
                TarjanForestNode lca = destLeaf.LCA(sourceLeaf);
                if (lca instanceof ATreeNode) {
                    ((ATreeNode) lca).addContractedEdge(edge);
                }
            }
        }

        if (needsInference) {
            // Collect contracted edges from all decomposed c-nodes
            List<Edge> contractedEdges = allRemovedContractions.stream()
                    .flatMap(c -> c.getContractedEdges().stream())
                    .collect(Collectors.toList());

            // Triggering edges are added to the edge set but NOT to reducedCosts (keep original weight)
            List<Edge> edgesForInference = new ArrayList<>(contractedEdges);
            edgesForInference.addAll(triggeringEdges);

            // V' captured after all decompositions
            List<ATreeNode> V = new LinkedList<>(this.getRoots());

            Map<Integer, Integer> reductions = computeReductionQuantities();
            Map<Edge, Integer> reducedCosts = applyReductionQuantities(contractedEdges, reductions);

            DynamicTarjanArborescence dynamicTarjan = new DynamicTarjanArborescence(
                V,
                edgesForInference,
                reducedCosts,
                this.getGraph(),
                this.edgeComparator
            );
            this.camerini = dynamicTarjan;

            Graph updatedArborescence = dynamicTarjan.inferPhylogeny(this.getGraph());
            this.roots = dynamicTarjan.augmentTarjanForestToATree();

            List<Edge> fullEdges = expandArborescence(updatedArborescence.getEdges());
            this.currentArborescence = fullEdges;
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
            if (currentEdge != null && currentEdge.getWeight() > edge.getWeight()) { // TODO - será que devia ser com os adjusted weights?
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
        
        while (!queue.isEmpty()) {
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
