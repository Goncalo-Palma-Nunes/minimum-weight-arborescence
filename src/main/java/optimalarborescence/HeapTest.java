package optimalarborescence;

import optimalarborescence.datastructure.heap.*;
import java.util.ArrayList;
import java.util.List;

public class HeapTest {

    public static void main(String[] args) {
        System.out.println("##################### Heap Test #####################");

        List<HeapNode> h1_nodes = new ArrayList<>();
        List<HeapNode> h2_nodes = new ArrayList<>();
        List<HeapNode> h3_nodes = new ArrayList<>();

        h1_nodes.add(new HeapNode(55, null, null));
        h1_nodes.add(new HeapNode(33, null, null));
        h1_nodes.add(new HeapNode(88, null, null));

        h2_nodes.add(new HeapNode(22, null, null));
        h2_nodes.add(new HeapNode(77, null, null));
        h2_nodes.add(new HeapNode(66, null, null));

        h3_nodes.add(new HeapNode(44, null, null));
        h3_nodes.add(new HeapNode(11, null, null));
        h3_nodes.add(new HeapNode(99, null, null));

        MergeableHeapInterface<HeapNode> h1 = new PairingHeap();
        MergeableHeapInterface<HeapNode> h2 = new PairingHeap();
        MergeableHeapInterface<HeapNode> h3 = new PairingHeap();

        for (HeapNode node : h1_nodes) {
            h1.insert(node);
        }

        for (HeapNode node : h2_nodes) {
            h2.insert(node);
        }

        for (HeapNode node : h3_nodes) {
            h3.insert(node);
        }

        System.out.println("Heap 1 Min: " + h1.findMin().getVal());
        System.out.println("Heap 2 Min: " + h2.findMin().getVal());
        System.out.println("Heap 3 Min: " + h3.findMin().getVal());


        MergeableHeapInterface<HeapNode> mergedHeap = h1.merge(h2).merge(h3);
        System.out.println("Merged Heap Min: " + mergedHeap.findMin().getVal());

        while (!mergedHeap.isEmpty()) {
            System.out.println("\n <-----------> Removing Min Node <-----------> ");
            System.out.println("Current Min/Root Node: " + (mergedHeap.isEmpty() ? "Heap is empty" : mergedHeap.findMin()));
            HeapNode minNode = mergedHeap.extractMin();
            System.out.println("Extracted Min: " + minNode);
            System.out.println("New Min/Root Node: " + (mergedHeap.isEmpty() ? "Heap is empty" : mergedHeap.findMin()));
            System.out.println("<-------------------------------------------------->");
        }

        System.out.println("##################### End of Heap Test #####################");

    }
    
}
