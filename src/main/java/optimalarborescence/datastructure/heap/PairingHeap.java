package optimalarborescence.datastructure.heap;

import optimalarborescence.exception.NotImplementedException;
import java.util.List;
import java.util.ArrayList;



// TODO - a interface deve ser do tipo <HeapNode> ou outra coisa?
public class PairingHeap implements MergeableHeapInterface<HeapNode> {

    HeapNode root;

    public PairingHeap() {
        this.root = null;
    }

    @Override
    public boolean isEmpty() {
        return root == null;
    }

    @Override
    public MergeableHeapInterface<HeapNode> merge(MergeableHeapInterface<HeapNode> other) {
        if (!(other instanceof PairingHeap)) {
            throw new IllegalArgumentException("Expected an argument of type PairingHeap");
        }

        PairingHeap otherHeap = (PairingHeap) other;
        if (this.isEmpty() || otherHeap.isEmpty()) {
            throw new IllegalArgumentException("Neither heap should be empty")
        }

        HeapNode rootA = this.root;
        HeapNode rootB = otherHeap.root;

        this.root = meld(rootA, rootB);

        return this;
    }

    @Override
    public HeapNode findMin() {
        throw new NotImplementedException("findMin operation is not implemented yet.");
    }

    @Override
    public void insert(HeapNode node) {
        if (this.isEmpty()) {
            this.root = node;
        } else {
            this.root = meld(this.root, node);
        }
    }

    private void link(HeapNode parent, HeapNode child) {
        // TODO - passar este método para o HeapNode
        if (parent.child == null) {
            child.brother = parent;
        } else {
            child.brother = parent.child;
            child.val = -child.val;
        }
        parent.child = child;
    }


    // Deveria receber HeapNodes ou PairingHeaps?
    private HeapNode meld(HeapNode p, HeapNode q) {
        if (p.val <= q.val) {
            link(p, q);
            return p;
        } else {
            link(q, p);
            return q;
        }
    }

    private HeapNode getHook(HeapNode node) {
        HeapNode curr = node;
        HeapNode next = node.brother;
        HeapNode prev;

        while (next != node) {
            prev = curr;
            curr = next;

            if (prev.val < 0) {
                if (curr.child == node) break;
                curr = curr.child;
                next = curr.brother;
            } else {
                next = curr.brother;
            }
        }

        return curr;
    }

    private void extractNode(HeapNode node) {
        HeapNode prev = getHook(node);

        if (prev.child == node && node.val < 0) {
            prev.child = null;
        } else if (prev.child == node) {
            prev.child = node.brother;
            node.val = -node.val;
        } else if (node.val < 0) {
            prev.brother = node.brother;
            prev.val = -prev.val;
        } else {
            prev.brother = node.brother;
            node.val = -node.val;
        }

        node.brother = null;
    }

    @Override
    public HeapNode decreaseKey(HeapNode node, int newValue) {
        if (this.root == node) {
            node.val = -newValue;
            return this.root;
        } else {
            extractNode(node);
            node.val = -newValue;
            return meld(this.root, node);
        }
    }

    public HeapNode extractMin(HeapNode root) {
        throw new NotImplementedException("ExtractMin operation is not implemented yet.");
    }

    @Override
    public String toString() {
        return "PairingHeap {" +
                " }";
    }
}
