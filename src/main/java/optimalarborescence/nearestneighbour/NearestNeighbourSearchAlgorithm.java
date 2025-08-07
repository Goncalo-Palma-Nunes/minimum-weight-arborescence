package optimalarborescence.nearestneighbour;

import java.util.List;

public interface NearestNeighbourSearchAlgorithm {
    /**
     * Finds the numNeighbours nearest neighbours of a given point in a dataset.
     *
     * @param point the point for which to find the nearest neighbours
     * @param numNeighbours the number of neighbours to search for
     * @return a list of the point's nearest neighbours
     */
    List<Point> neighbourSearch(Point point, int numNeighbours);
}
