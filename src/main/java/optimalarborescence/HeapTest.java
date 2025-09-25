package optimalarborescence;

import optimalarborescence.datastructure.heap.*;
import optimalarborescence.graph.Edge;
import java.util.ArrayList;
import java.util.List;

public class HeapTest {

    private final static int NODES_PER_HEAP = 3;
    private final static int NUM_HEAPS = 3;
    private final static int[] WEIGHTS = {55, 33, 88, 22, 77, 66, 44, 11, 99};
    private final static boolean VERBOSE = false;

    public static void main(String[] args) {
        System.out.println("##################### Heap Test ############################");

        List<MergeableHeapInterface<HeapNode>> heaps = createHeaps();

        if (VERBOSE) {
            for (int i = 0; i < NUM_HEAPS; i++) {
                System.out.print("Heap " + (i + 1) + " Min:");
                System.out.println(heaps.get(i).findMin().getVal());
            }
        }

        MergeableHeapInterface<HeapNode> mergedHeap = mergeHeaps(heaps);

        if (VERBOSE) {
            System.out.println("Merged Heap Min: " + mergedHeap.findMin().getVal());
        }

        while (!mergedHeap.isEmpty()) {
            extractMin(mergedHeap);
        }
        System.out.println("##################### End of Heap Test #####################");
    }

    private static void extractMin(MergeableHeapInterface<HeapNode> mergedHeap) {
        
        if (VERBOSE) {
            System.out.println("\n <-----------> Removing Min Node <-----------> ");
            System.out.println("Current Min/Root Node: " + (mergedHeap.isEmpty() ? "Heap is empty" : mergedHeap.findMin()));
        }
        HeapNode minNode = mergedHeap.extractMin();
            System.out.println("Extracted Min: " + minNode);
        if (VERBOSE) {
            System.out.println("New Min/Root Node: " + (mergedHeap.isEmpty() ? "Heap is empty" : mergedHeap.findMin()));
            System.out.println("<-------------------------------------------------->");
        }
    }

    private static List<MergeableHeapInterface<HeapNode>> createHeaps() {
        List<List<HeapNode>> heap_nodes = new ArrayList<>();

        for (int i = 0; i < NUM_HEAPS; i++) {
            heap_nodes.add(new ArrayList<>());
        }

        List<Edge> edges = new ArrayList<>();
        for (int weight : WEIGHTS) {
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
        return heaps;
    }

    private static final MergeableHeapInterface<HeapNode> mergeHeaps(List<MergeableHeapInterface<HeapNode>> heaps) {
        MergeableHeapInterface<HeapNode> mergedHeap = heaps.get(0);

        for (int i = 1; i < NUM_HEAPS; i++) {
            mergedHeap = mergedHeap.merge(heaps.get(i));
        }
        return mergedHeap;
    }
}
