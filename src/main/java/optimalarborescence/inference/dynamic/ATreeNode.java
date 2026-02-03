package optimalarborescence.inference.dynamic;

import java.util.List;
import java.util.ArrayList;

import optimalarborescence.graph.Edge;
import optimalarborescence.inference.TarjanForestNode;

/** Implementation of an Augmented Tree Node (used to maintain fully dynamic minimum weight arborescences) as specified in:
 * <p>
 * "G. G. Pollatos, O. A. Telelis, and V. Zissimopoulos, “Updating directed minimum cost spanning
 * trees,” in Experimental Algorithms, C. Àlvarez and M. Serna, Eds. Berlin, Heidelberg: Springer
 * Berlin Heidelberg, 2006, pp. 291–302
 * 
 * A brief summary of the Augmented Tree is presented in:
 * <p>
 * J. Espada's Master's Thesis: “Large scale phylogenetic inference from noisy data based on minimum weight spanning arborescences,” 2014.
 */
public class ATreeNode extends TarjanForestNode {

    /** Cost of this.edge at the time it was selected for this ATreeNode */
    private int y; 

    /** Whether this node is a simple node or a c-node.
     * <p>
     * See:
     * <p>
     * G. G. Pollatos, O. A. Telelis, and V. Zissimopoulos, “Updating directed minimum cost spanning
     * trees,” in Experimental Algorithms, C. Àlvarez and M. Serna, Eds. Berlin, Heidelberg: Springer
     * Berlin Heidelberg, 2006, pp. 291–302
     * */
    private boolean simpleNode;

    /** The edges that were contracted to form this c-node (edges having both
     * their vertices on the contracted cycle).
     * <p>
     * If this is a simple node, this.contractedEdges = null
     * */
    private List<Edge> contractedEdges; // TODO - removal of edge can be achieved in O(1) time, if we use an endogenous list implementation
                                        // each edge has associated pointers in the digraph representation, pointing to the next and previous
                                        // elements in the list. See Pollatos (Sec. 4.2) and
                                        // Gabow et al. "Efficient algorithms for finding minimum spanning trees in undirected and directed graphs."

    // Lazy loading support
    /** Whether children have been loaded from disk (true for in-memory nodes, false for lazy-loaded nodes before first access) */
    private boolean childrenLoaded = true;
    
    /** File offset for this node in the memory-mapped file (null for in-memory nodes) */
    private Long nodeOffset;
    
    /** Base name for memory-mapped files (null for in-memory nodes) */
    private String baseName;
    
    /** Graph nodes map for reconstructing edges during lazy loading (null for in-memory nodes) */
    private java.util.Map<Integer, optimalarborescence.graph.Node> graphNodes;

    public ATreeNode(Edge edge, int y, ATreeNode parent, List<ATreeNode> children, boolean simpleNode, List<Edge> contractedEdges) {
        super(edge);
        this.y = y;
        this.simpleNode = simpleNode;
        this.contractedEdges = contractedEdges;
    }

    public ATreeNode(Edge edge, int y, ATreeNode parent, boolean simpleNode, List<Edge> contractedEdges) {
        this(edge, y, parent, new ArrayList<>(), simpleNode, contractedEdges);
    }

    public ATreeNode(Edge edge, int y, boolean simpleNode, List<Edge> contractedEdges) {
        this(edge, y, null, new ArrayList<>(), simpleNode, contractedEdges);
    }

    /**
     * Constructor for lazy-loaded ATreeNodes.
     * Children will be loaded from disk on first access to getATreeChildren().
     * 
     * @param edge The edge for this node
     * @param y Cost of the edge when selected
     * @param simpleNode Whether this is a simple node or c-node
     * @param contractedEdges List of contracted edges (for c-nodes)
     * @param nodeOffset File offset for this node
     * @param baseName Base name for memory-mapped files
     * @param graphNodes Map of node IDs to Node objects for edge reconstruction
     */
    public ATreeNode(Edge edge, int y, boolean simpleNode, List<Edge> contractedEdges,
                    long nodeOffset, String baseName, java.util.Map<Integer, optimalarborescence.graph.Node> graphNodes) {
        this(edge, y, null, new ArrayList<>(), simpleNode, contractedEdges);
        this.childrenLoaded = false;
        this.nodeOffset = nodeOffset;
        this.baseName = baseName;
        this.graphNodes = graphNodes;
    }

    public int getCost() {
        return y;
    }

    public void setCost(int y) {
        this.y = y;
    }

    public ATreeNode getParent() {
        if (parent instanceof ATreeNode) {
            return (ATreeNode) parent;
        } else {
            return null;
        }
    }

    public void setParent(ATreeNode p) {
        if (this.parent != null) {
            this.parent.getChildren().remove(this);
        }
        this.parent = p;
    }

    public void setChildren(List<ATreeNode> children) {
        List<TarjanForestNode> castedChildren = children.stream()
                                                .map(child -> (TarjanForestNode) child)
                                                .toList();
        this.children = castedChildren;
    }

    public boolean isSimpleNode() {
        return simpleNode;
    }

    public boolean isRoot() {
        return this.edge == null;
    }

    public List<Edge> getContractedEdges() {
        return contractedEdges;
    }

    public void addContractedEdge(Edge edge) {
        if (this.contractedEdges == null) {
            this.contractedEdges = new ArrayList<>();
        }
        this.contractedEdges.add(edge);
    }

    private ATreeNode downCast(TarjanForestNode node) {
        if (node instanceof ATreeNode) {
            return (ATreeNode) node;
        } else {
            throw new ClassCastException("The provided TarjanForestNode is not an instance of ATreeNode.");
        }
    }

    public List<ATreeNode> getATreeChildren() {
        // Lazy load children if not yet loaded
        if (!childrenLoaded && baseName != null && nodeOffset != null && graphNodes != null) {
            try {
                optimalarborescence.memorymapper.ATreeMapper.loadChildren(this, baseName, nodeOffset, graphNodes);
                childrenLoaded = true;
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to lazy-load children for node at offset " + nodeOffset, e);
            }
        }
        
        return this.children.stream()
                    .map(this::downCast)
                    .toList();
    }
    
    /**
     * Check if this node supports lazy loading.
     * @return true if this node was created for lazy loading
     */
    public boolean isLazyLoadable() {
        return nodeOffset != null && baseName != null;
    }
    
    /**
     * Check if children have been loaded (for lazy-loaded nodes).
     * @return true if children are loaded or this is not a lazy-loaded node
     */
    public boolean areChildrenLoaded() {
        return childrenLoaded;
    }

    /** Searches the ATree for a contraction node (c-node) that contains the specified edge.
     * 
     * @param edge The edge to search for.
     * @param node The current node in the recursive search (initially, the root of the ATree).
     * @return The c-node whose contracted edges contain the specified edge, or null if no such c-node exists.
     */
    public ATreeNode findContractionByEdge(Edge edge, ATreeNode node) {
        if (node == null) return null;

        if (!node.isSimpleNode() && node.getContractedEdges().contains(edge)) return node;

        List<ATreeNode> children = node.children.stream()
                                        .map(this::downCast)
                                        .toList();
        for (ATreeNode child : children) {
            ATreeNode result = findContractionByEdge(edge, child);
            if (result != null) return result;
        }
        return null;
    }

    /** Searches the ATree for the node associated with the specified edge.
     * 
     * @param edge The edge to search for.
     * @param node The current node in the recursive search (initially, the root of the ATree).
     * @return The ATreeNode whose edge is the specified edge, or null if no such node exists.
     */
    public ATreeNode findATreeNodeByEdge(Edge edge, ATreeNode node) {
        if (node == null) return null;

        if (node.getEdge() != null && node.getEdge().equals(edge)) return node;

        List<ATreeNode> children = node.children.stream()
                                        .map(this::downCast)
                                        .toList();
        for (ATreeNode child : children) {
            ATreeNode result = findATreeNodeByEdge(edge, child);
            if (result != null) return result;
        }
        return null;
    }

}
