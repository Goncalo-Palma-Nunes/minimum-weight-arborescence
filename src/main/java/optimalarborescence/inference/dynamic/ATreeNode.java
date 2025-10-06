package optimalarborescence.inference.dynamic;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

import optimalarborescence.graph.Edge;

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
public class ATreeNode implements Serializable {

    /** The edge selected by the algorithm for the represented vertex. 
     * If no edge was selected then this.edge = null and this is a root node.
     * */
    private Edge edge;

    /** Cost of this.edge at the time it was selected for this ATreeNode */
    private int y; 

    /** The parent of this ATreeNode in the ATree. 
     * <p>
     * this.parent = null, if this is the root node (if this.edge == null) */
    private ATreeNode parent;

    /** The children of this ATreeNode in the ATree. */
    private List<ATreeNode> children;

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
        this.edge = edge;
        this.y = y;
        this.parent = parent;
        this.children = children;
        this.simpleNode = simpleNode;
        this.contractedEdges = contractedEdges;
    }

    public ATreeNode(Edge edge, int y, ATreeNode parent, boolean simpleNode, List<Edge> contractedEdges) {
        this(edge, y, parent, new ArrayList<>(), simpleNode, contractedEdges);
    }

    public Edge getEdge() {
        return edge;
    }

    public void setEdge(Edge edge) {
        this.edge = edge;
    }

    public int getCost() {
        return y;
    }

    public void setCost(int y) {
        this.y = y;
    }

    public ATreeNode getParent() {
        return parent;
    }

    public void setParent(ATreeNode parent) {
        this.parent = parent;
    }

    public List<ATreeNode> getChildren() {
        return children;
    }

    public void addChild(ATreeNode child) {
        this.children.add(child);
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

    /** Searches the ATree for a contraction node (c-node) that contains the specified edge.
     * 
     * @param edge The edge to search for.
     * @param node The current node in the recursive search (initially, the root of the ATree).
     * @return The c-node whose contracted edges contain the specified edge, or null if no such c-node exists.
     */
    public ATreeNode findContractionByEdge(Edge edge, ATreeNode node) {
        if (node == null) return null;

        if (!node.isSimpleNode() && node.getContractedEdges().contains(edge)) return node;

        for (ATreeNode child : node.getChildren()) {
            ATreeNode result = findContractionByEdge(edge, child);
            if (result != null) return result;
        }
        return null;
    }

    public ATreeNode findATreeNodeByEdge(Edge edge, ATreeNode node) {
        if (node == null) return null;

        if (node.getEdge() != null && node.getEdge().equals(edge)) return node;

        for (ATreeNode child : node.getChildren()) {
            ATreeNode result = findATreeNodeByEdge(edge, child);
            if (result != null) return result;
        }
        return null;
    }
}
