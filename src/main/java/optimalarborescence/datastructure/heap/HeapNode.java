package optimalarborescence.datastructure.heap;

public class HeapNode {
    private static final long id = 1L; // for debugging purposes
    int val;
    HeapNode child;
    HeapNode brother; // brother (or father if at the end of the list)

    public HeapNode(HeapNode c, HeapNode b) {
        this.val = 1;
        this.child = c;
        this.brother = b;
    }

    public void setVal(int v) {
        if (this.child != null || this.brother != null) {
            /* If it is not a singleton heap, we should not change its value
            with this operation. Use decreaseKey */
            return;
        }

        this.val = -v;
    }
        
    @Override
    public String toString() {
        return "HeapNode {" +
            "id=" + HeapNode.id +
            ", value='" + this.val + '\'' +
             ", child=" + this.child +
            ", brother=" + this.brother +
                " }";
    }
}
