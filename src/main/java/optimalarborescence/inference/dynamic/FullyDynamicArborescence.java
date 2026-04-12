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
import java.util.Collections;
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

    /**  Array that for each i stores a node from the forest which is associated with the minimum weight edge
     *  incident in node i */
    List<ATreeNode> inEdgeNode;


    public FullyDynamicArborescence() {
        super();
        this.roots = new LinkedList<>();
        this.camerini = null;
        this.edgeComparator = (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());
        this.currentArborescence = new ArrayList<>();
        this.inEdgeNode = new ArrayList<>();
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
        this.numVertices = graph.getNodes().size();
        this.inEdgeNode = new ArrayList<>(numVertices);
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

    /**
     * Factory method for creating the algorithm instance used for inference on the
     * partially contracted graph. Override in subclasses to provide memory-efficient
     * variants (e.g., disk-based edge access).
     */
    protected DynamicTarjanArborescence createDynamicTarjan(
            List<ATreeNode> aTreeRoots,
            List<Edge> contractedEdges,
            Map<Integer, Integer> reducedCosts,
            Graph originalGraph,
            Comparator<Edge> edgeComparator,
            UnionFind wccUf,
            UnionFindStronglyConnected sccUf,
            List<TarjanForestNode> precomputedInEdgeNode,
            List<TarjanForestNode> precomputedLeaves) {
        return new DynamicTarjanArborescence(
            aTreeRoots, contractedEdges, reducedCosts, originalGraph,
            edgeComparator, wccUf, sccUf, precomputedInEdgeNode, precomputedLeaves);
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

        // this.removeEdge(oldEdge);
        this.removeEdgeWithoutInferenceStep(oldEdge);
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
        if (this.wccUf != null && this.wccUf.getSize() > this.numVertices) {
            this.wccUf.clear();
        }
        else {
            this.wccUf = new UnionFind(this.numVertices);
        }

        if (this.sccUf != null && this.sccUf.getSize() > this.numVertices) {
            this.sccUf.clear();
        }
        else {
            this.sccUf = new UnionFindStronglyConnected(this.numVertices);
        }

        // Reset inEdgeNode and leaves so they are fully repopulated by the next BFS
        this.inEdgeNode.clear();
        this.leaves.clear();
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
            ATreeNode parentN = N.getATreeParent();
            
            // Remove N from parent's children list, or from this.roots if N is a top-level ATree root
            if (parentN != null && parentN.getChildren() != null) {
                parentN.getChildren().remove(N);
            } else if (parentN == null) {
                this.roots.remove(N);
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

    private void updateInEdgeNode(ATreeNode node) {
        if (node.getEdge() != null) {
            Edge edge = node.getEdge();
            Node dest = edge.getDestination();
            if (inEdgeNode.size() <= dest.getId()) {
                // Ensure the inEdgeNode list is large enough to hold the index
                for (int i = inEdgeNode.size(); i <= dest.getId(); i++) {
                    inEdgeNode.add(null);
                }
            }
            if ((inEdgeNode.get(dest.getId()) == null || node.getCost() < inEdgeNode.get(dest.getId()).getCost()) 
                && !isVirtuallyDeleted(node)) {
                inEdgeNode.set(dest.getId(), node);
            }
        }
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
                    // Force destination to be the SCC representative for consistent representative semantics.
                    // NOTE: edge.getSource() is the EXTERNAL vertex entering the supernode and must NOT be merged.
                    int destId = edge.getDestination().getId();

                    // Perform a union for each contracted vertex in the cycle
                    if (current.getContractedVertices() != null) {
                        for (Integer contractedVertexId : current.getContractedVertices().keySet()) {
                            sccUf.unionForceRep(destId, contractedVertexId);
                        }
                    }
                }
                updateInEdgeNode(current);
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

    private boolean isVirtuallyDeleted(TarjanForestNode node) {
        if (node instanceof ATreeNode) {
            return ((ATreeNode) node).isVirtuallyDeleted();
        }
        return false;
    }

    public void rebuildContractedDigraph() {
        throw new NotImplementedException("The rebuildContractedDigraph method is not implemented.");
    }

    /**
     * Converts this.leaves (a flat list of TarjanForestNodes for simple ATree nodes) into
     * a list indexed by vertex ID, suitable for passing to DynamicTarjanArborescence.
     */
    private List<TarjanForestNode> getIndexedLeaves() {
        int maxId = 0;
        for (TarjanForestNode leaf : this.leaves) {
            if (leaf.getEdge() != null) {
                maxId = Math.max(maxId, leaf.getEdge().getDestination().getId());
            }
        }
        List<TarjanForestNode> indexed = new ArrayList<>(Collections.nCopies(maxId + 1, null));
        for (TarjanForestNode leaf : this.leaves) {
            if (leaf.getEdge() != null && !isVirtuallyDeleted(leaf)) {
                int id = leaf.getEdge().getDestination().getId();
                indexed.set(id, leaf);
            }
        }
        return indexed;
    }

    private List<Edge> firstStaticInference(Graph g) {
        DynamicTarjanArborescence dynamicTarjan = createDynamicTarjan(
            this.getRoots(),
            g.getEdges(),
            new HashMap<>(),
            g,
            this.edgeComparator,
            null,
            null,
            null,
            null
        );
        this.camerini = dynamicTarjan; // Update camerini reference
        Graph updatedArborescence = dynamicTarjan.inferPhylogeny(g);
        this.roots = dynamicTarjan.augmentTarjanForestToATree();
        return updatedArborescence.getEdges();
    }

    private DynamicTarjanArborescence removeEdgeWithoutInferenceStep(Edge edge) {
        if (edge == null || !this.getGraph().getEdges().contains(edge)) {
            return null;
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
                DynamicTarjanArborescence dynamicTarjan = createDynamicTarjan(
                    V,                  // ATree roots
                    edges,              // Edges from E'
                    reductions,         // Reduced costs for edges
                    this.getGraph(),    // Original graph
                    this.edgeComparator,
                    null,               // wccUf must be null: BFS pre-merges all WCC components,
                                        // which would cause false cycle detection in contractionPhase.
                                        // DTA must rebuild WCC from scratch.
                    this.sccUf,
                    new ArrayList<>(this.inEdgeNode),
                    this.getIndexedLeaves()
                );
                this.camerini = dynamicTarjan; // Update camerini reference

                return dynamicTarjan; // Return the initialized DynamicTarjanArborescence for the caller to run inference on
            }
        }
        
        return null;
    }

    @Override
    public List<Edge> removeEdge(Edge e) {
        DynamicTarjanArborescence dynamicTarjan = removeEdgeWithoutInferenceStep(e);
        if (dynamicTarjan != null) {
            Graph updatedArborescence = dynamicTarjan.inferPhylogeny(this.getGraph());
            this.roots = dynamicTarjan.augmentTarjanForestToATree();
            this.currentArborescence = updatedArborescence.getEdges();
            return this.currentArborescence;
        }
        else {
            // Edge was not found or no ATree structure exists, so we have already performed the necessary updates in the original graph and can simply return the current arborescence.
            return this.getCurrentArborescence();
        }
    }

    private DynamicTarjanArborescence addEdgeWithoutInferenceStep(Edge edge) {
        getGraph().addEdge(edge);

        Node source = edge.getSource();
        Node destination = edge.getDestination();

        TarjanForestNode[] leaves = this.camerini.getLeaves();
        
        // Check if leaves are initialized and accessible
        if (leaves == null || source.getId() >= leaves.length || destination.getId() >= leaves.length) {
            // Leaves not initialized - need to run full algorithm
            this.currentArborescence = firstStaticInference(this.getGraph());
            return null;
        }
        
        TarjanForestNode sourceLeaf = leaves[source.getId()];
        TarjanForestNode destLeaf = leaves[destination.getId()];
        
        if (destLeaf == null) {
            ATreeNode dst = new ATreeNode(edge, edge.getWeight(), true, null);
            this.roots.add(dst);
            resetUnionFinds();
            this.computeReductionQuantities();
            // this.leaves.set(edge.getDestination().getId(), dst);
            DynamicTarjanArborescence dynamicTarjan = createDynamicTarjan(
                new LinkedList<>(this.getRoots()),
                null,
                reductions,
                graph,
                edgeComparator,
                null,
                this.sccUf,
                new ArrayList<>(this.inEdgeNode),
                this.getIndexedLeaves());
            this.camerini = dynamicTarjan; // Update camerini reference
            return dynamicTarjan;
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
                
                TarjanForestNode N = candidateForRemoval;
                Edge removedEdge = N.getEdge();
                
                // Check if ATree structure exists
                if (this.getRoots().isEmpty()) {
                    // No ATree structure - fallback to running full Tarjan
                    // Note: Must create a new Tarjan instance since it maintains internal state
                    this.currentArborescence = firstStaticInference(this.getGraph());
                } else {
                    if (N instanceof ATreeNode) {
                        ((ATreeNode) N).setVirtuallyDeleted(true);
                    }

                    // Virtual deletion: recognize G(V', E') without actually removing the edge
                    List<ATreeNode> V = new LinkedList<>(this.getRoots());
                    for (ATreeNode root : V) {
                        if (root.getEdge() == removedEdge) {
                            ((ATreeNode) root).setVirtuallyDeleted(true);
                        }
                    }
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
                    DynamicTarjanArborescence dynamicTarjan = createDynamicTarjan(
                        V,
                        edgesWithNewEdge,
                        reductions,
                        this.getGraph(),
                        this.edgeComparator,
                        null,               // wccUf must be null: BFS pre-merges all WCC components.
                        this.sccUf,
                        new ArrayList<>(this.inEdgeNode),
                        this.getIndexedLeaves()
                    );
                    this.camerini = dynamicTarjan; // Update camerini reference
                    
                    return this.camerini;
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

        return null;
    }

    @Override
    public List<Edge> addEdge(Edge edge) {
        DynamicTarjanArborescence result = addEdgeWithoutInferenceStep(edge);
        if (result != null) {
            Graph updatedArborescence = result.inferPhylogeny(this.getGraph());
            this.roots = result.augmentTarjanForestToATree();
            this.currentArborescence = updatedArborescence.getEdges();
        }
        return this.getCurrentArborescence();
    }

    @Override
    public List<Edge> removeNode(Node v, List<Edge> edges) {
        if (this.camerini == null || this.getRoots().isEmpty() || this.getCurrentArborescence().isEmpty() || !this.getGraph().getNodes().contains(v)) {
            throw new IllegalArgumentException("Cannot remove node: invalid state or node not in graph.");
        }

        boolean needsInference = false;
        for (Edge edge : edges) {
            DynamicTarjanArborescence result = removeEdgeWithoutInferenceStep(edge);
            if (result != null) {
                this.camerini = result;
                needsInference = true;
            }
        }

        if (needsInference) {
            this.camerini.virtuallyRemoveNode(v.getId());
            Graph updatedArborescence = this.camerini.inferPhylogeny(this.getGraph());
            getGraph().removeNode(v);
            this.roots = this.camerini.augmentTarjanForestToATree();
            this.currentArborescence = updatedArborescence.getEdges().stream()
                .filter(e -> e.getSource().getId() != v.getId() && e.getDestination().getId() != v.getId())
                .collect(Collectors.toList());
        } else {
            getGraph().removeNode(v);
            this.currentArborescence = firstStaticInference(this.getGraph());
        }

        return this.currentArborescence;
    }

    @Override
    public List<Edge> addNode(Node u, List<Edge> edges) {
        if (this.camerini == null || this.getRoots().isEmpty() || this.getCurrentArborescence().isEmpty()) {
            throw new IllegalArgumentException("Cannot add node: invalid state.");
        }

        getGraph().addNode(u);
        this.numVertices = Math.max(this.numVertices, u.getId() + 1); // Update numVertices to accommodate new node ID
        resizeStructs();

        boolean needsInference = false;
        for (Edge edge : edges) {
            DynamicTarjanArborescence result = addEdgeWithoutInferenceStep(edge);
            if (result != null) {
                this.camerini = result;
                needsInference = true;
            }
        }

        if (needsInference) {
            // A fresh DTA was created (destLeaf==null or decompose path) — run inference.
            Graph updatedArborescence = this.camerini.inferPhylogeny(this.getGraph());
            this.roots = this.camerini.augmentTarjanForestToATree();
            this.currentArborescence = updatedArborescence.getEdges();
        } else {
            this.currentArborescence = firstStaticInference(this.getGraph());
        }

        return this.currentArborescence;
    }

    private void resizeStructs() {
        // Resize inEdgeNode list to accommodate new vertex ID
        if (inEdgeNode.size() < numVertices) {
            for (int i = inEdgeNode.size(); i < numVertices; i++) {
                inEdgeNode.add(null);
            }
        }

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
