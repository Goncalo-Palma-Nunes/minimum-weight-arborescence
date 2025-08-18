package optimalarborescence;

import optimalarborescence.distance.*;
import optimalarborescence.nearestneighbour.Point;

public class HammingTest {

    static String SEQUENCE1 = "ATGC";
    static String SEQUENCE2 = "ATGA";
    static String A = "A";
    static String T = "T";
    static String C = "C";
    static String G = "G";
    static HammingDistance hd = new HammingDistance();

    public static void main(String[] args) {

        System.out.println("######## String distances #########");
        System.out.println(A + " in binary: " + Integer.toBinaryString(A.getBytes()[0]));
        System.out.println(T + " in binary: " + Integer.toBinaryString(T.getBytes()[0]));
        System.out.println(C + " in binary: " + Integer.toBinaryString(C.getBytes()[0]));
        System.out.println(G + " in binary: " + Integer.toBinaryString(G.getBytes()[0]));
        System.out.println(SEQUENCE1 + " in binary: " + Integer.toBinaryString(SEQUENCE1.getBytes()[0]));
        System.out.println(SEQUENCE2 + " in binary: " + Integer.toBinaryString(SEQUENCE2.getBytes()[0]));

        double distance = hd.calculate(SEQUENCE1, SEQUENCE2);
        System.out.println("Hamming Distance between " + SEQUENCE1 + " and " + SEQUENCE2 + ": " + distance);

        System.out.println("Hamming Distance between " + A + " and " + A + ": " + hd.calculate(A, A));
        System.out.println("Hamming Distance between " + A + " and " + T + ": " + hd.calculate(A, T));
        System.out.println("Hamming Distance between " + A + " and " + C + ": " + hd.calculate(A, C));
        System.out.println("Hamming Distance between " + A + " and " + G + ": " + hd.calculate(A, G));


        System.out.println("######## Byte array distances #########");
        Point p1 = new Point(0, A);
        Point p2 = new Point(1, T);
        Point p3 = new Point(2, C);
        Point p4 = new Point(3, G);
        Point p5 = new Point(4, SEQUENCE1);
        Point p6 = new Point(5, SEQUENCE2);

        System.out.println("Hamming (Byte) Distance between " + p1.getSequence() + " and " + p1.getSequence() + ": " + hd.calculate(p1.getBitArray(), p1.getBitArray()));
        System.out.println("Hamming (Byte) Distance between " + p1.getSequence() + " and " + p2.getSequence() + ": " + hd.calculate(p1.getBitArray(), p2.getBitArray()));
        System.out.println("Hamming (Byte) Distance between " + p1.getSequence() + " and " + p3.getSequence() + ": " + hd.calculate(p1.getBitArray(), p3.getBitArray()));
        System.out.println("Hamming (Byte) Distance between " + p1.getSequence() + " and " + p4.getSequence() + ": " + hd.calculate(p1.getBitArray(), p4.getBitArray()));
        System.out.println("Hamming (Byte) Distance between " + p5.getSequence() + " and " + p6.getSequence() + ": " + hd.calculate(p5.getBitArray(), p6.getBitArray()));
    }
}
