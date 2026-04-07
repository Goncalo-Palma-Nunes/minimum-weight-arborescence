package optimalarborescence.inference;

import optimalarborescence.graph.Edge;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class TarjanForestNode implements Serializable, Comparable<TarjanForestNode> {
    protected Edge edge;
    protected TarjanForestNode parent;
    protected List<TarjanForestNode> children;
    protected boolean remove; // Flag to mark nodes that should be removed during expansion phase

    /** Auxiliary data structure for Tarjan's algorithm. TarjanForestNode is used to build the
     * forest F described in Camerini's correction of Tarjan's optimum branching algorithm.
     */
    public TarjanForestNode(Edge edge) {
        this.edge = edge;
        this.children = new ArrayList<>();
        this.parent = null;
        this.remove = false;
    }

    public Edge getEdge() {
        return edge;
    }

    /** Checks if the node is a leaf node. */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public List<TarjanForestNode> getChildren() {
        return children;
    }

    public TarjanForestNode getParent() {
        return parent;
    }

    public void clearChildren() {
        this.children.clear();
    }

    public TarjanForestNode setParent(TarjanForestNode parent) {
        if (this.parent != null) {
            this.parent.children.remove(this);
        }
        this.parent = parent;
        if (this.parent != null) {
            this.parent.addChild(this);
        }
        return this.parent;
    }

    public void addChild(TarjanForestNode child) {
        children.add(child);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("( ").append(edge.getSource().getId()).append(" -> ").append(edge.getDestination().getId()).append(" )");
        sb.append("\t-> Weight: ").append(edge.getWeight());
        sb.append("\tRemove: ").append(remove);
        sb.append("\n\t Parent: ").append(parent != null ? parent.edge.getSource().getId() + "->" + parent.edge.getDestination().getId() : "null");
        sb.append("\n\t Children: ");
        for (TarjanForestNode child : children) {
            sb.append("\n\t\t");
            sb.append(child.edge.getSource().getId()).append("->").append(child.edge.getDestination().getId()).append(" ");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TarjanForestNode other = (TarjanForestNode) obj;
        return edge.equals(other.edge);
    }

    @Override
    public int compareTo(TarjanForestNode other) {
        return Integer.compare(this.edge.getWeight(), other.edge.getWeight());
    }

    /**
     * Finds the Lowest Common Ancestor (LCA) of this node and another node in the tree.
     * 
     * @param other The other node to find the LCA with
     * @return The lowest common ancestor node, or null if no common ancestor exists
     */
    public TarjanForestNode LCA(TarjanForestNode other) {
        if (other == null) return null;
        
        // Collect all ancestors of this node
        List<TarjanForestNode> ancestors = new ArrayList<>();
        TarjanForestNode current = this;
        while (current != null) {
            ancestors.add(current);
            current = current.parent;
        }
        
        // Traverse ancestors of other node and find first common ancestor
        current = other;
        while (current != null) {
            if (ancestors.contains(current)) {
                return current;
            }
            current = current.parent;
        }
        
        return null;
    }

    public boolean isRemove() {
        return remove;
    }

    public void setRemove(boolean remove) {
        this.remove = remove;
    }
}
