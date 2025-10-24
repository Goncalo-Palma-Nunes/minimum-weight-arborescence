package optimalarborescence.nearestneighbour;

import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.distance.*;

import java.io.Serializable;
import java.util.List;

public abstract class NearestNeighbourSearchAlgorithm implements Serializable {

    private DistanceFunction distanceFunction;
    private static final long serialVersionUID = 129348938L; // TODO - UID para serialização. Pesquisar mais sobre isto

    public NearestNeighbourSearchAlgorithm(DistanceFunction distanceFunction) {
        this.distanceFunction = distanceFunction;
    }

    /**
     * Finds the numNeighbours nearest neighbours of a given point in a dataset.
     *
     * @param point the point for which to find the nearest neighbours
     * @param numNeighbours the number of neighbours to search for
     * @return a list of the point's nearest neighbours
     */
    public List<Point> neighbourSearch(Point point, int numNeighbours) {
        throw new NotImplementedException();
    }

    /**
     * Gets the distance function used by this algorithm.
     * @return the distance function
     */
    public DistanceFunction getDistanceFunction() {
        return distanceFunction;
    }

    /**
     * Stores a point in the dataset.
     *
     * @param point the point to be stored
     */
    public void storePoint(Point point) {
        throw new NotImplementedException();
    }
}
