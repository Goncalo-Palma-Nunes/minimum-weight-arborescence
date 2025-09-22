package optimalarborescence.datastructure.heap;

import optimalarborescence.graph.Edge;

public class HeapNode {
    private static final long id = 1L; // for debugging purposes
    int val;
    Edge e;
    HeapNode child;
    HeapNode brother; // brother (or father if at the end of the list)

    public HeapNode(Edge e, HeapNode c, HeapNode b) {
        this.val = -e.getWeight();
        this.child = c;
        this.brother = b;
        this.e = e;
    }

    public void setVal(int v) {
        if (this.child != null || this.brother != null) {
            /* If it is not a singleton heap, we should not change its value
            with this operation. Use decreaseKey */
            return;
        }

        this.val = -v;

    }

    public int getVal() {
        return Math.abs(this.val);
    }

    public Edge getEdge() {
        return this.e;
    }
        
    @Override
    public String toString() {
        return "HeapNode {" +
            //"id=" + HeapNode.id +
            ", value='" + getVal() + '\'' +
            ", edge=" + (this.e != null ? getEdge() : "null") +
            ", child=" + (this.child != null ? this.child.getVal() : "null") +
            ", brother=" + (this.brother != null ? this.brother.getVal() : "null") +
            " }";
    }
}
