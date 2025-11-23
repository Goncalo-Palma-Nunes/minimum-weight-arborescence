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

    /** The edge selected by the algorithm for the represented vertex. 
     * If no edge was selected then this.edge = null and this is a root node.
     * */
    // private Edge edge;

    /** Cost of this.edge at the time it was selected for this ATreeNode */
    private int y; 

    /** The parent of this ATreeNode in the ATree. 
     * <p>
     * this.parent = null, if this is the root node (if this.edge == null) */
    // protected ATreeNode parent;

    /** The children of this ATreeNode in the ATree. 
     * NOTE: This shadows the parent class field intentionally since the parent field
     * is package-private and not accessible. */
    // protected List<ATreeNode> children;

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


    public ATreeNode(Edge edge, int y, ATreeNode parent, List<ATreeNode> children, boolean simpleNode, List<Edge> contractedEdges) {
        super(edge);
        // this.edge = edge; // Set our shadowed field too
        this.y = y;
        // this.parent = parent;
        // this.children = children;
        this.simpleNode = simpleNode;
        this.contractedEdges = contractedEdges;
    }

    public ATreeNode(Edge edge, int y, ATreeNode parent, boolean simpleNode, List<Edge> contractedEdges) {
        this(edge, y, parent, new ArrayList<>(), simpleNode, contractedEdges);
    }

    public ATreeNode(Edge edge, int y, boolean simpleNode, List<Edge> contractedEdges) {
        this(edge, y, null, new ArrayList<>(), simpleNode, contractedEdges);
    }

    // public Edge getEdge() {
    //     return edge;
    // }

    // public void setEdge(Edge edge) {
    //     this.edge = edge;
    // }

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
        return this.children.stream()
                    .map(this::downCast)
                    .toList();
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
