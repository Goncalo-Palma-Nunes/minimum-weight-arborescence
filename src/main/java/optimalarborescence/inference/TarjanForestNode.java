package optimalarborescence.inference;

import optimalarborescence.graph.Edge;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class TarjanForestNode implements Serializable {
    Edge edge;
    TarjanForestNode parent;
    List<TarjanForestNode> children; // TODO - passar a uma left child right sibling representation

        /** Auxiliary data structure for Tarjan's algorithm. TarjanForestNode is used to build the
         * forest F described in Camerini's correction of Tarjan's optimum branching algorithm.
         */
    public TarjanForestNode(Edge edge) {
        this.edge = edge;
        this.children = new ArrayList<>();
        this.parent = null;
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
            // if (children == null) {
            //     children = new ArrayList<>();
            // }
        children.add(child);
    }

    @Override
    public String toString() {
        // return "TarjanForestNode(\nedge=" + edge + 
        // "\nparent=" + (parent != null ? parent.edge : null) + 
        // "\nchildren=" + children + "\n)";
        return "(" + edge.getSource().getId() + ", " + edge.getDestination().getId() + ")";
    }
}
