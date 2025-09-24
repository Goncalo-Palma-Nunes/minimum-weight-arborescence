package optimalarborescence;

import optimalarborescence.datastructure.heap.*;
import optimalarborescence.graph.Edge;
import java.util.ArrayList;
import java.util.List;

public class HeapTest {

    private final static int NODES_PER_HEAP = 3;
    private final static int NUM_HEAPS = 3;

    public static void main(String[] args) {
        System.out.println("##################### Heap Test #####################");

        List<List<HeapNode>> heap_nodes = new ArrayList<>();

        for (int i = 0; i < NUM_HEAPS; i++) {
            heap_nodes.add(new ArrayList<>());
        }

        List<Edge> edges = new ArrayList<>();
        int[] weights = {55, 33, 88, 22, 77, 66, 44, 11, 99};
        for (int weight : weights) {
            edges.add(new Edge(null, null, weight));
        }

        for (int i = 0; i < NUM_HEAPS; i++) {
            for (int j = 0; j < NODES_PER_HEAP; j++) {
                heap_nodes.get(i).add(new HeapNode(edges.get(i * NODES_PER_HEAP + j), null, null));
            }
        }

        List<MergeableHeapInterface<HeapNode>> heaps = new ArrayList<>();
        for (int i = 0; i < NUM_HEAPS; i++) {
            heaps.add(new PairingHeap());
        }

        for (int i = 0; i < NUM_HEAPS; i++) {
            MergeableHeapInterface<HeapNode> heap = heaps.get(i);
            List<HeapNode> nodes = heap_nodes.get(i);

            for (HeapNode node : nodes) {
                heap.insert(node);
            }
        }

        System.out.println("Heap 1 Min: " + heaps.get(0).findMin().getVal());
        System.out.println("Heap 2 Min: " + heaps.get(1).findMin().getVal());
        System.out.println("Heap 3 Min: " + heaps.get(2).findMin().getVal());

        MergeableHeapInterface<HeapNode> mergedHeap = heaps.get(0);

        for (int i = 1; i < NUM_HEAPS; i++) {
            mergedHeap = mergedHeap.merge(heaps.get(i));
        }
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
