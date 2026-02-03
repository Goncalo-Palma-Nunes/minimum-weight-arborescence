package optimalarborescence.inference;

import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.graph.DistanceMatrix;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;

import java.util.List;
import java.util.ArrayList;

public class NeighbourJoining implements InferenceAlgorithm {

    protected class NJEdge {
        int source;
        int destination;
        double weight;

        protected NJEdge(int source, int destination, double weight) {
            this.source = source;
            this.destination = destination;
            this.weight = weight;
        }
    }

    private DistanceMatrix distanceMatrix;
    private List<Node> activeNodes;
    private double[][] Q;
    private List<NJEdge> tree;
    private int nextNodeId; // To create new internal nodes
    private DistanceFunction distanceFunction;

    public NeighbourJoining(DistanceMatrix distanceMatrix, DistanceFunction distanceFunction) {
        this.distanceMatrix = distanceMatrix;
        this.activeNodes = new ArrayList<>(distanceMatrix.getNodes());
        this.Q = new double[activeNodes.size()][activeNodes.size()];
        this.tree = new ArrayList<>();
        this.distanceFunction = distanceFunction;
        
        // Find the maximum node ID to start creating new internal nodes
        this.nextNodeId = activeNodes.stream()
            .mapToInt(Node::getId)
            .max()
            .orElse(0) + 1;
    }
    
    // /**
    //  * constructor for backwards compatibility.
    //  * @deprecated Use NeighbourJoining(DistanceMatrix) instead
    //  */
    // @Deprecated
    public NeighbourJoining(List<List<Double>> distanceMatrix) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < distanceMatrix.size(); i++) {
            nodes.add(new Node(i));
        }
        
        // Create DistanceMatrix and populate it
        this.distanceMatrix = new DistanceMatrix(nodes, distanceFunction);
        for (int i = 0; i < distanceMatrix.size(); i++) {
            for (int j = i + 1; j < distanceMatrix.size(); j++) {
                this.distanceMatrix.setDistance(nodes.get(i), nodes.get(j), distanceMatrix.get(i).get(j));
            }
        }
        
        this.activeNodes = new ArrayList<>(nodes);
        this.Q = new double[activeNodes.size()][activeNodes.size()];
        this.tree = new ArrayList<>();
        this.nextNodeId = distanceMatrix.size();
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        List<NJEdge> njEdges = inferTree();
        Graph resultGraph = new Graph();

        // Add all nodes to the graph
        for (Node node : distanceMatrix.getNodes()) {
            resultGraph.addNode(new Node(node.getId()));
        }

        // Add all edges to the graph
        for (NJEdge njEdge : njEdges) {
            Node source = new Node(njEdge.source);
            Node destination = new Node(njEdge.destination);
            resultGraph.addEdge(new Edge(source, destination, (int) njEdge.weight));
        }

        return resultGraph;
    }

    public List<NJEdge> inferTree() {
        while (activeNodes.size() > 2) {
            computeQ();
            int[] minPair = findMinQ();
            int i = minPair[0];
            int j = minPair[1];

            Node nodeI = activeNodes.get(i);
            Node nodeJ = activeNodes.get(j);
            Node newNode = new Node(nextNodeId++);
            
            newNodeDistance(i, j, nodeI, nodeJ, newNode);
            updateDistanceMatrix(i, j, nodeI, nodeJ, newNode);
        }

        // Add the last two edges
        Node node0 = activeNodes.get(0);
        Node node1 = activeNodes.get(1);
        addTreeEdge(node0.getId(), node1.getId(), distanceMatrix.getDistance(node0, node1));

        return tree;
    }

    private void computeQ() {
        int n = activeNodes.size();
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double sumRowI = 0;
                    double sumRowJ = 0;
                    Node nodeI = activeNodes.get(i);
                    Node nodeJ = activeNodes.get(j);
                    
                    for (int k = 0; k < n; k++) {
                        Node nodeK = activeNodes.get(k);
                        sumRowI += distanceMatrix.getDistance(nodeI, nodeK);
                        sumRowJ += distanceMatrix.getDistance(nodeJ, nodeK);
                    }
                    Q[i][j] = (n - 2) * distanceMatrix.getDistance(nodeI, nodeJ) - sumRowI - sumRowJ;
                } else {
                    Q[i][j] = Double.MAX_VALUE;
                }
            }
        }
    }

    private int[] findMinQ() {
        int minI = -1;
        int minJ = -1;
        double minValue = Double.MAX_VALUE;
        int n = activeNodes.size();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (Q[i][j] < minValue) {
                    minValue = Q[i][j];
                    minI = i;
                    minJ = j;
                }
            }
        }

        return new int[]{minI, minJ};
    }

    private void addTreeEdge(int source, int destination, double weight) {
        tree.add(new NJEdge(source, destination, weight));
    }

    // compute the distance from the pair (i, j) to a new node u
    private void newNodeDistance(int i, int j, Node nodeI, Node nodeJ, Node newNode) {
        int n = activeNodes.size();
        double distanceIJ = distanceMatrix.getDistance(nodeI, nodeJ);
        
        double delta_i_u = 0.5 * distanceIJ + (1.0 / (2.0 * (n - 2))) *
                (sumDistances(i) - sumDistances(j));

        double delta_j_u = distanceIJ - delta_i_u;

        addTreeEdge(nodeI.getId(), newNode.getId(), delta_i_u);
        addTreeEdge(nodeJ.getId(), newNode.getId(), delta_j_u);
    }

    private double sumDistances(int index) {
        double sum = 0;
        Node node = activeNodes.get(index);
        
        for (int k = 0; k < activeNodes.size(); k++) {
            if (k != index) {
                sum += distanceMatrix.getDistance(node, activeNodes.get(k));
            }
        }
        return sum;
    }

    private void updateDistanceMatrix(int i, int j, Node nodeI, Node nodeJ, Node newNode) {
        // Create a new list of active nodes with merged nodes removed and new node added
        List<Node> newActiveNodes = new ArrayList<>();
        for (int k = 0; k < activeNodes.size(); k++) {
            if (k != i && k != j) {
                newActiveNodes.add(activeNodes.get(k));
            }
        }
        newActiveNodes.add(newNode);
        
        // Create new distance matrix with the updated node set
        DistanceMatrix newDistanceMatrix = new DistanceMatrix(newActiveNodes, distanceFunction);
        
        // Copy distances between existing nodes (excluding i and j)
        for (int m = 0; m < newActiveNodes.size() - 1; m++) {
            for (int n = m + 1; n < newActiveNodes.size() - 1; n++) {
                Node nodeM = newActiveNodes.get(m);
                Node nodeN = newActiveNodes.get(n);
                double distance = distanceMatrix.getDistance(nodeM, nodeN);
                newDistanceMatrix.setDistance(nodeM, nodeN, distance);
            }
        }
        
        // Calculate distances from merged node to all other nodes
        double distanceIJ = distanceMatrix.getDistance(nodeI, nodeJ);
        for (int k = 0; k < newActiveNodes.size() - 1; k++) {
            Node nodeK = newActiveNodes.get(k);
            double newDistance = 0.5 * (distanceMatrix.getDistance(nodeI, nodeK) + 
                                       distanceMatrix.getDistance(nodeJ, nodeK) - distanceIJ);
            newDistanceMatrix.setDistance(newNode, nodeK, newDistance);
        }
        
        // Update state
        this.distanceMatrix = newDistanceMatrix;
        this.activeNodes = newActiveNodes;
        this.Q = new double[activeNodes.size()][activeNodes.size()];
    }
}
