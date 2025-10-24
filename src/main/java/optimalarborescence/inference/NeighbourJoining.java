package optimalarborescence.inference;

import optimalarborescence.exception.*;
import optimalarborescence.graph.Graph;

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

    private List<List<Double>> distanceMatrix;
    private List<List<Double>> Q;
    private List<NJEdge> tree;

    public NeighbourJoining(List<List<Double>> distanceMatrix) {
        this.distanceMatrix = distanceMatrix;
        this.Q = new ArrayList<>();
        for (int i = 0; i < distanceMatrix.size(); i++) {
            Q.add(new ArrayList<>());
            for (int j = 0; j < distanceMatrix.size(); j++) {
                Q.get(i).add(0.0);
            }
        }

        this.tree = new ArrayList<>();
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        throw new NotImplementedException("Neighbour Joining algorithm is not yet implemented.");
    }

    public List<NJEdge> inferTree() {
        while (distanceMatrix.size() > 2) {
            computeQ();
            int[] minPair = findMinQ();
            int i = minPair[0];
            int j = minPair[1];

            int newNodeIndex = distanceMatrix.size(); // index for the new node
            newNodeDistance(i, j, newNodeIndex);
            updateDistanceMatrix(i, j);
        }

        // Add the last two edges
        addTreeEdge(0, 1, getDistance(0, 1));

        return tree;
    }

    private void computeQ() {

        for (int i = 0; i < distanceMatrix.size(); i++) {
            for (int j = 0; j < distanceMatrix.size(); j++) {
                if (i != j) {
                    double sumRowI = 0;
                    double sumRowJ = 0;
                    for (int k = 0; k < distanceMatrix.size(); k++) {
                        // if (k != i) sumRowI += distanceMatrix[i][k];
                        // if (k != j) sumRowJ += distanceMatrix[j][k]
                        sumRowI += getDistance(i, k);
                        sumRowJ += getDistance(j, k);
                    }
                    Q.get(i).set(j, (distanceMatrix.size() - 2) * getDistance(i, j) - sumRowI - sumRowJ);
                } else {
                    Q.get(i).set(j, Double.MAX_VALUE); // or some large value
                }
            }
        }
    }

    private int[] findMinQ() {
        int minI = -1;
        int minJ = -1;
        double minValue = Double.MAX_VALUE;

        for (int i = 0; i < Q.size(); i++) {
            for (int j = 0; j < Q.get(i).size(); j++) {
                if (Q.get(i).get(j) < minValue) {
                    minValue = Q.get(i).get(j);
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
    private void newNodeDistance(int i, int j, int u) {
        double delta_i_u = 0.5 * getDistance(i, j) + (1 / (2 * (distanceMatrix.size() - 2))) *
                (sumDistances(i) - sumDistances(j));

        double delta_j_u = getDistance(i, j) - delta_i_u;

        addTreeEdge(i, u, delta_i_u);
        addTreeEdge(j, u, delta_j_u);
    }

    private double sumDistances(int index) {
        double sum = 0;
        for (int k = 0; k < distanceMatrix.size(); k++) {
            if (k != index) {
                sum += getDistance(index, k);
            }
        }
        return sum;
    }

    private void updateDistanceMatrix(int i, int j) {
        List<Double> newRow = new ArrayList<>();
        for (int k = 0; k < distanceMatrix.size(); k++) {
            if (k != i && k != j) {
                double newDistance = 0.5 * (getDistance(i, k) + getDistance(j, k) - getDistance(i, j));
                newRow.add(newDistance);
            }
        }

        // Remove rows and columns for i and j
        List<List<Double>> newDistanceMatrix = new ArrayList<>();
        for (int m = 0; m < distanceMatrix.size(); m++) {
            if (m != i && m != j) {
                List<Double> newCol = new ArrayList<>();
                for (int n = 0; n < distanceMatrix.size(); n++) {
                    if (n != i && n != j) {
                        newCol.add(distanceMatrix.get(m).get(n));
                    }
                }
                newDistanceMatrix.add(newCol);
            }
        }

        // Add the new row and column
        newDistanceMatrix.add(newRow);
        for (int m = 0; m < newDistanceMatrix.size() - 1; m++) {
            newDistanceMatrix.get(m).add(newRow.get(m));
        }
        newDistanceMatrix.get(newDistanceMatrix.size() - 1).add(0.0); // distance to itself

        distanceMatrix = newDistanceMatrix;
    }

    private Double getDistance(int i, int j) {
        return distanceMatrix.get(i).get(j);
    }
}
