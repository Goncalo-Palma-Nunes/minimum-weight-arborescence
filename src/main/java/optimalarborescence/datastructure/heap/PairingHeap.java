package optimalarborescence.datastructure.heap;

import optimalarborescence.exception.NotImplementedException;
import java.util.List;
import java.util.ArrayList;



// TODO - a interface deve ser do tipo <HeapNode> ou outra coisa?
public class PairingHeap implements MergeableHeapInterface<HeapNode> {

    HeapNode root;

    @Override
    public boolean isEmpty() {
        throw new NotImplementedException("IsEmpty operation is not implemented yet.");
    }

    @Override
    public MergeableHeapInterface<HeapNode> merge(MergeableHeapInterface<HeapNode> other) {
        if (!(other instanceof PairingHeap)) {
            throw new IllegalArgumentException("Expected an argument of type PairingHeap");
        }

        PairingHeap otherHeap = (PairingHeap) other;

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
    public void insert(HeapNode value) {
        throw new NotImplementedException("insert operation is not implemented yet.");
    }

    private void link(HeapNode parent, HeapNode child) {
        if (parent.child == null) {
            child.brother = parent;
        } else {
            child.brother = parent.child;
            child.val = -child.val;
        }
        parent.child = child;
    }


    // Acho que este meld está mal. Devia receber heaps e não heap nodes (ver especificação de aava)
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

    public HeapNode decreaseKey(HeapNode root, HeapNode node, int newValue) {
        if (root == node) {
            node.val = -newValue;
            return root;
        } else {
            extractNode(node);
            node.val = -newValue;
            return meld(root, node);
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
