package optimalarborescence;

import optimalarborescence.nearestneighbour.LSH;
import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.distance.*;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class LSHTest {

    static List<Point> points = new ArrayList<>();
    static List<String> sequences = List.of("ATGC", "ATGA", "ATTT", "ATGT",
    "ATGG", "ACTC", "TGGC", "TTTT", "CCGG");
    //static List<String> sequences = List.of("AT", "AC", "AG",
    //"TA", "CA", "CG", "TT", "GT");

    public static void main(String[] args) {
        // Create a new LSH instance

        System.out.println("Starting LSH Test with sequences: " + sequences);

        LSH lsh = new LSH(2, 4, 0, 3,
                        new HammingDistance(), 3);
        
        System.out.println("Storing points in LSH...");
        for (String seq : sequences) {
            System.out.println("Storing sequence: " + seq);
            Point point = new Point(points.size(), seq);
            points.add(point);
            lsh.storePoint(point);
            System.out.println("Point Stored");
        }

        // print all concatenatedHashes in LSH
        for (Set<LSH.Hash> hashes : lsh.concatenatedHashes) {
            System.out.println("Concatenated Hash:");
            for (LSH.Hash hash : hashes) {
                System.out.println(" - " + hash);
            }
        }

        // System.out.println(lsh);

        System.out.println("Finished storing points."); System.out.println("");

        Point newPoint = new Point(points.size(), "AAAA");
        //Point newPoint = new Point(points.size(), "AA");
        System.out.println("Searching for neighbours of new point: " + newPoint.getSequence());
        List<Point> neighbours = lsh.neighbourSearch(newPoint, points.size());
        System.out.println("Neighbours of " + newPoint.getSequence() + ":");
        for (Point neighbour : neighbours) {
            System.out.println(" - " + neighbour.getSequence());
        }
    }
}
