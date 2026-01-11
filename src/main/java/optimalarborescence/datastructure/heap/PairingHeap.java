package optimalarborescence.datastructure.heap;

import java.util.Queue;
import java.util.Comparator;
import java.util.LinkedList;

// TODO - a interface deve ser do tipo <HeapNode> ou outra coisa?
public class PairingHeap implements MergeableHeapInterface<HeapNode> {

    HeapNode root;
    private Comparator<HeapNode> comparator;

    public PairingHeap(Comparator<HeapNode> comparator) {
        this.root = null;
        this.comparator = comparator;
    }

    public PairingHeap(HeapNode node, Comparator<HeapNode> comparator) {
        this.root = node;
        this.comparator = comparator;
    }

    @Override
    public boolean isEmpty() {
        return root == null;
    }

    @Override
    public MergeableHeapInterface<HeapNode> merge(MergeableHeapInterface<HeapNode> other) {
        // TODO - acho que posso substituir o merge e usar só o meld
        if (!(other instanceof PairingHeap)) {
            throw new IllegalArgumentException("Expected an argument of type PairingHeap");
        }

        PairingHeap otherHeap = (PairingHeap) other;
        // if (this.isEmpty() || otherHeap.isEmpty()) {
        //     throw new IllegalArgumentException("Neither heap should be empty");
        // }
        if (this.isEmpty()) {
            this.root = otherHeap.root;
            return this;
        }
        if (otherHeap.isEmpty()) {
            return this;
        }


        HeapNode rootA = this.root;
        HeapNode rootB = otherHeap.root;

        this.root = meld(rootA, rootB);

        return this;
    }

    @Override
    public HeapNode findMin() {
        return this.root;
    }

    @Override
    // public HeapNode insert(HeapNode node, int key) {
    //     return meld(node, new HeapNode(key, null, null));
    // }
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
        if (comparator.compare(p, q) <= 0) {
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
            this.root = meld(this.root, node);
            return this.root;
        }
    }

    public void decreaseAllKeys(int delta) {
        if (this.root != null) {
            decreaseAllKeysIterative(this.root, delta);
        }
    }

    private void decreaseAllKeysIterative(HeapNode root, int delta) {
        if (root == null) return;

        // Use a queue for level-order traversal to avoid stack overflow
        Queue<HeapNode> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            HeapNode current = queue.poll();
            // Since val = -weight, to decrease weight by delta, we increase val by delta
            current.val += delta;

            // Process all children in the circular sibling list
            if (current.child != null) {
                HeapNode firstChild = current.child;
                HeapNode child = firstChild;
                
                // Traverse the circular brother list until we get back to the parent
                do {
                    queue.add(child);
                    child = child.brother;
                } while (child != null && child != current);
            }
        }
    }

    private HeapNode extractMin(HeapNode r) {
        if (r.child == null) return r;

        HeapNode prev = null, n1, n2, next;
        HeapNode head = r.child;
        n1 = head;

        // Reset the node
        r.child = null;
        r.brother = null;
        r.val = -1;

        // Turn values negative (each child should now be a head of a heap)
        // Use a HashSet to detect cycles in the brother chain
        java.util.HashSet<HeapNode> visited = new java.util.HashSet<>();
        while (n1 != null && n1 != r && !visited.contains(n1)) {
            visited.add(n1);
            n1.val = -Math.abs(n1.val);
            n1 = n1.brother;
            // System.out.println("11111111111111111111111111111111");
        }

        // First pass left to right, joining two by two
        n1 = head;
        visited.clear();
        n2 = head.brother;
        if (n2 != null && n2 != r && !visited.contains(n2)) {
            next = n2.brother;
            head = prev = meld(head, n2);
            n1 = next;
            visited.add(head);
        }
        while (n1 != null && n1 != r && !visited.contains(n1)) {
            visited.add(n1);
            // System.out.println("222222222222222222222222222");
            n2 = n1.brother;
            if (n2 != null && n2 != r && !visited.contains(n2)) {
                next = n2.brother;
                n1 = meld(n1, n2);
                if (prev != null) prev.brother = n1;
                prev = n1;
                n1 = next;
            } else {
                break;
            }
        }
        if (prev != null) prev.brother = n1;

        // Second pass left to right (foldl)
        n1 = head;
        next = n1.brother;
        visited.clear();
        visited.add(n1);
        while (next != null && next != r && !visited.contains(next)) {
            n2 = next;
            visited.add(n2);
            // System.out.println("33333333333333333333333333333");
            next = n2.brother;
            n1 = meld(n1, n2);
            n1.brother = null;
            n1.val = -Math.abs(n1.val);
        }
        n1.brother = null;

        // System.out.println("(extract min) new root: " + (n1 != null ? n1 : "null"));
        return n1;
    }

    @Override
    public HeapNode extractMin() {
        if (this.root == null) {
            return null;
        }
        else if (this.root.child == null) {
            HeapNode oldRoot = this.root;
            this.root = null;
            return oldRoot;
        }
        HeapNode oldRoot = this.root;
        this.root = extractMin(this.root);

        // if (this.root == oldRoot) { // Last node in the heap
        //     // otherwise it would loop forever
        //     this.root = null;
        // }
        // System.out.println("(extract min) final root: " + (this.root != null ? this.root : "null"));
        // System.out.println("(extract min) extracted root: " + (oldRoot != null ? oldRoot : "null"));

        return oldRoot;
    }

    @Override
    public void clear() {
        this.root = null;
    }

    @Override
    public String toString() 
    {
        return "PairingHeap {" +
            "root=" + root +
            " }";
    }
}
