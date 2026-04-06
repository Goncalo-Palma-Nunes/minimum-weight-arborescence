package optimalarborescence.inference.dynamic;

import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Backward-compatible constructor for cases with no prior ATree state (e.g., tests, first inference).
     * Delegates to the full constructor with null pre-computed structures.
     */
    public DynamicTarjanArborescence(List<ATreeNode> aTreeRoots,
                                     List<Edge> contractedEdges,
                                     Map<Integer, Integer> reducedCosts,
                                     Graph originalGraph,
                                     Comparator<Edge> edgeComparator) {
        this(aTreeRoots, contractedEdges, reducedCosts, originalGraph, edgeComparator,
             null, null, null, null);
    }

    /**
     * Constructor for DynamicTarjanArborescence.
     *
     * @param aTreeRoots The list of ATree root nodes (V' in the partially contracted graph)
     * @param contractedEdges The edges from decomposed contractions (E')
     * @param reducedCosts Map of edges to their reduced costs (w_current(e) = w_original(e) - r_i)
     * @param originalGraph The original graph structure
     * @param edgeComparator Comparator for edge ordering
     * @param wccUf Pre-computed WCC union-find (may be null for first inference)
     * @param sccUf Pre-computed SCC union-find (may be null for first inference)
     * @param precomputedInEdgeNode Pre-computed inEdgeNode list from ATree BFS (may be null)
     * @param precomputedLeaves Pre-computed leaves indexed by vertex ID (may be null)
     */
    public DynamicTarjanArborescence(List<ATreeNode> aTreeRoots,
                                     List<Edge> contractedEdges,
                                     Map<Integer, Integer> reducedCosts,
                                     Graph originalGraph,
                                     Comparator<Edge> edgeComparator,
                                     UnionFind wccUf,
                                     UnionFindStronglyConnected sccUf,
                                     List<TarjanForestNode> precomputedInEdgeNode,
                                     List<TarjanForestNode> precomputedLeaves) {
        super(originalGraph, edgeComparator);

        this.aTreeRoots = aTreeRoots;
        this.reductions = reducedCosts;
        this.modifiedGraph = this.graph;

        // Replace parent's UF structures with pre-computed ones
        if (sccUf != null) {
            this.ufSCC = sccUf;
        }
        if (wccUf != null) {
            this.ufWCC = wccUf;
        }

        // Replace parent's inEdgeNode and leaves with pre-computed structures
        replaceInEdgeNode(precomputedInEdgeNode);
        replaceLeaves(precomputedLeaves);

        // Filter roots to SCC representatives to avoid duplicate processing
        // ATreeRootsToRoots();
        filterRootsToSCCRepresentatives();

        System.out.println("Roots = " + this.roots.stream().map(Node::getId).toList());
        System.out.println("ATreeRoots = " + this.aTreeRoots.stream().map(n -> n.getEdge() != null ? n.getEdge().getDestination().getId() : null).toList());
        printInEdgeNode();
        printLeaves();

        // Pre-populate cycleEdgeNodes from existing ATree state
        populateCycleEdgeNodesFromATree();
        printCycleEdgeNodes();

        // Rebuild max entries from ATree c-node children
        rebuildMaxFromATreeRoots();
        printMax();

        // Pre-merge queues for contracted supernodes so contractionPhase sees all
        // edges entering any cycle member, not just those entering the rep directly.
        preMergeQueuesForSCCs();
    }

    /**
     * Pre-merges priority queues for all vertices that share an SCC representative.
     * After SCC unions from the ATree state, each contracted supernode is represented
     * by a single vertex (the SCC rep). contractionPhase retrieves getQueue(sccFind(root)),
     * so the rep's queue must contain edges entering ALL cycle members, not just the rep.
     *
     * This mirrors the queue-merge step that contractionPhase performs when it discovers
     * a new cycle during static inference.
     */
    private void preMergeQueuesForSCCs() {
        if (aTreeRoots == null) return;

        for (ATreeNode root : aTreeRoots) {
            if (root.isSimpleNode() || root.getEdge() == null) continue;
            if (root.getContractedVertices() == null) continue;

            int rep = ufSCC.find(root.getEdge().getDestination().getId());
            Node repNode = null;
            for (Node n : getNodes()) {
                if (n.getId() == rep) {
                    repNode = n;
                    break;
                }
            }
            if (repNode == null) continue;

            for (Integer memberId : root.getContractedVertices().keySet()) {
                if (ufSCC.find(memberId) == rep && memberId != rep) {
                    Node memberNode = null;
                    for (Node n : getNodes()) {
                        if (n.getId() == memberId) {
                            memberNode = n;
                            break;
                        }
                    }
                    if (memberNode != null) {
                        getQueue(repNode).merge(getQueue(memberNode));
                    }
                }
            }
        }
    }

    /**
     * Replaces entries in the parent's inEdgeNode list with pre-computed ATreeNode references.
     */
    private void replaceInEdgeNode(List<TarjanForestNode> precomputedInEdgeNode) {
        if (precomputedInEdgeNode == null) return;

        for (int i = 0; i < precomputedInEdgeNode.size() && i < this.inEdgeNode.size(); i++) {
            TarjanForestNode node = precomputedInEdgeNode.get(i);
            if (node != null) {
                this.inEdgeNode.set(i, node);
            }
        }
    }

    /**
     * Replaces entries in the parent's leaves array with pre-computed ATreeNode references.
     */
    private void replaceLeaves(List<TarjanForestNode> precomputedLeaves) {
        if (precomputedLeaves == null) return;

        for (int i = 0; i < precomputedLeaves.size(); i++) {
            TarjanForestNode leaf = precomputedLeaves.get(i);
            if (leaf != null && i < this.leaves.length) {
                this.leaves[i] = leaf;
            }
        }
    }

    private void ATreeRootsToRoots() {
        if (aTreeRoots == null) return;

        this.roots = new ArrayList<>();
        System.out.println("ATreeRoots = " + aTreeRoots.stream().map(n -> n.getEdge() != null ? n.getEdge().getDestination().getId() : null).toList());
        for (ATreeNode aTreeRoot : aTreeRoots) {
            Edge e = aTreeRoot.getEdge();
            if (e != null) {
                Node dst = e.getDestination();
                this.roots.add(dst);
            }
        }
    }

    /**
     * Filters this.roots (set by CameriniForest constructor) to contain only unique SCC
     * representatives, avoiding duplicate processing of vertices that share a contraction.
     */
    private void filterRootsToSCCRepresentatives() {
        Set<Integer> seenRepresentatives = new HashSet<>();
        List<Node> filteredRoots = new ArrayList<>();

        for (Node root : this.roots) {
            int rep = ufSCC.find(root.getId());
            if (!seenRepresentatives.contains(rep)) {
                seenRepresentatives.add(rep);
                // Find the node object whose id matches the representative
                Node repNode = null;
                for (Node n : getNodes()) {
                    if (n.getId() == rep) {
                        repNode = n;
                        break;
                    }
                }
                if (repNode != null) {
                    filteredRoots.add(repNode);
                }
            }
        }
        this.roots = filteredRoots;
    }

    /**
     * Pre-populates cycleEdgeNodes from the existing ATree state so that createMinNode
     * correctly links new TarjanForestNodes as parents of pre-existing ATreeNodes.
     *
     * For simple ATree roots: put the node itself as the single cycle edge entry so
     * createMinNode adopts it as a child instead of creating a fresh leaf.
     * For c-node ATree roots: put the c-node's children (the prior cycle edges) as the
     * cycle edge list so they are re-linked under the new contraction node.
     */
    private void populateCycleEdgeNodesFromATree() {
        if (aTreeRoots == null) return;

        for (ATreeNode root : aTreeRoots) {
            if (root.getEdge() == null || root.isVirtuallyDeleted()) continue;

            int rep = ufSCC.find(root.getEdge().getDestination().getId());
            if (rep >= cycleEdgeNodes.size()) continue;

            if (root.isSimpleNode()) {
                // Simple leaf: put the ATreeNode itself as the single "cycle edge"
                List<TarjanForestNode> singletonList = new ArrayList<>();
                singletonList.add(root);
                cycleEdgeNodes.set(rep, singletonList);
            } else {
                // C-node: put its ATree children (the previously contracted cycle edges)
                List<TarjanForestNode> cycleChildren = new ArrayList<>(root.getATreeChildren());
                cycleEdgeNodes.set(rep, cycleChildren);
            }
        }
    }

    /**
     * Rebuilds max entries from ATree c-node children.
     * For each c-node root, max[rep] is set to the destination of the highest-cost child edge,
     * matching what expansionPhase uses to identify which leaf to start tracing from.
     */
    private void rebuildMaxFromATreeRoots() {
        if (aTreeRoots == null) return;

        for (ATreeNode root : aTreeRoots) {
            if (root.isSimpleNode() || root.getEdge() == null) continue;

            List<ATreeNode> cycleChildren = root.getATreeChildren();
            if (cycleChildren.isEmpty()) continue;

            ATreeNode maxChild = cycleChildren.stream()
                .filter(c -> c.getEdge() != null)
                .max(Comparator.comparingInt(ATreeNode::getCost))
                .orElse(null);

            if (maxChild != null) {
                Node maxDest = maxChild.getEdge().getDestination();
                int rep = ufSCC.find(root.getEdge().getDestination().getId());
                if (rep < max.size()) {
                    max.set(rep, maxDest);
                }
            }
        }
    }

    /**
     * Runs the Tarjan algorithm on the partially contracted graph with reduced costs.
     */
    @Override
    public Graph inferPhylogeny(Graph graph) {
        // Simply run the parent class's algorithm on the modified graph
        System.out.println("\u001B[35m Graph before inference: " + modifiedGraph + "\u001B[0m");
        System.out.println("Printing ATree roots before inference:");
        if (aTreeRoots != null) {
            for (ATreeNode root : aTreeRoots) {
                System.out.println("  " + root);
            }
        } else {
            System.out.println("  No ATree roots provided.");
        }
        Graph result = super.inferPhylogeny(modifiedGraph);
        this.augmentTarjanForestToATree();
        
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

        // Patch leaves[] so it references the new ATreeNode objects instead of the original
        // TarjanForestNodes. This is critical for addEdge(), which walks leaves[] to find
        // candidates for virtual deletion via instanceof ATreeNode checks.
        for (int i = 0; i < this.leaves.length; i++) {
            TarjanForestNode oldLeaf = this.leaves[i];
            if (oldLeaf != null && conversionMap.containsKey(oldLeaf)) {
                this.leaves[i] = conversionMap.get(oldLeaf);
            }
        }

        return this.aTreeRoots;
    }
    
    /**
     * Recursively converts a TarjanForestNode and its entire subtree to ATreeNodes.
     *
     * If the node is already an ATreeNode (pre-existing from a prior iteration that was
     * linked as a child during contractionPhase), it is reused to preserve metadata
     * (y-value, contractedVertices, simpleNode flag) instead of creating a duplicate.
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

        // If this node is already an ATreeNode, reuse it to preserve metadata
        if (tarjanNode instanceof ATreeNode) {
            ATreeNode existing = (ATreeNode) tarjanNode;
            // Patch contractedVertices if missing for c-nodes (guards against pre-fix ATreeNodes)
            if (!existing.isSimpleNode() && existing.getContractedVertices() == null
                    && existing.getChildren() != null && !existing.getChildren().isEmpty()) {
                Map<Integer, Integer> cv = new HashMap<>();
                for (TarjanForestNode child : existing.getChildren()) {
                    if (child.getEdge() != null) {
                        int srcId = child.getEdge().getSource().getId();
                        int dstId = child.getEdge().getDestination().getId();
                        cv.put(srcId, srcId);
                        cv.put(dstId, dstId);
                    }
                }
                existing.setContractedVertices(cv);
            }
            conversionMap.put(tarjanNode, existing);
            return existing;
        }

        Edge edge = tarjanNode.getEdge();
        int cost = (edge != null) ? edge.getWeight() : 0;

        boolean isSimpleNode;
        if (tarjanNode.getChildren() != null) {
            isSimpleNode = tarjanNode.getChildren().isEmpty();
        } else {
            isSimpleNode = true; // No children means it's a simple node
        }

        // Build contractedVertices for c-nodes from children's cycle/path edge endpoints
        Map<Integer, Integer> contractedVertices = null;
        if (!isSimpleNode) {
            contractedVertices = new HashMap<>();
            for (TarjanForestNode child : tarjanNode.getChildren()) {
                if (child.getEdge() != null) {
                    int srcId = child.getEdge().getSource().getId();
                    int dstId = child.getEdge().getDestination().getId();
                    contractedVertices.put(srcId, srcId);
                    contractedVertices.put(dstId, dstId);
                }
            }
        }

        ATreeNode aTreeNode = new ATreeNode(edge, cost, isSimpleNode, contractedVertices);

        // Register in map before processing children (to handle cycles/shared references)
        conversionMap.put(tarjanNode, aTreeNode);

        // Convert all children recursively
        List<ATreeNode> aTreeChildren = new ArrayList<>();
        if (tarjanNode.getChildren() != null) {
            // Defensive copy: ATreeNode.setParent() removes from old parent's children list,
            // which may be tarjanNode.getChildren() itself, causing ConcurrentModificationException.
            List<TarjanForestNode> childrenSnapshot = new ArrayList<>(tarjanNode.getChildren());
            for (TarjanForestNode child : childrenSnapshot) {
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
