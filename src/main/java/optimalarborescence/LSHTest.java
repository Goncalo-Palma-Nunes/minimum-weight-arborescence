package optimalarborescence;

import optimalarborescence.nearestneighbour.LSH;
import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.distance.*;

import java.util.List;
import java.util.ArrayList;

public class LSHTest {

    static List<Point> points = new ArrayList<>();
    static List<String> sequences = List.of("ATGC", "ATGA", "ATTT", "ATGT",
    "ATGG", "ACTC", "TGGC", "TTTT", "CCGG");

    public static void main(String[] args) {
        // Create a new LSH instance

        System.out.println("Starting LSH Test with sequences: " + sequences);

        LSH lsh = new LSH(4, 10, 0, 3,
                        new HammingDistance(), 2);
        
        System.out.println("LSH instance created with parameters: "
                + "numHashFunctions=4, numBuckets=10, minHash=0, maxHash=3, "
                + "distanceFunction=HammingDistance, numNeighbours=2");

        System.out.println("Storing points in LSH...");
        for (String seq : sequences) {
            System.out.println("Storing sequence: " + seq);
            Point point = new Point(points.size(), seq);
            points.add(point);
            lsh.storePoint(point);
            System.out.println("Point Stored");
        }

        System.out.println("Finished storing points."); System.out.println("");
        System.out.println(lsh);

        Point newPoint = new Point(points.size(), "AAAA");
        System.out.println("Searching for neighbours of new point: " + newPoint.getSequence());
        List<Point> neighbours = lsh.neighbourSearch(newPoint, points.size());
        System.out.println("Neighbours of " + newPoint.getSequence() + ":");
        for (Point neighbour : neighbours) {
            System.out.println(" - " + neighbour.getSequence());
        }
    }
}
