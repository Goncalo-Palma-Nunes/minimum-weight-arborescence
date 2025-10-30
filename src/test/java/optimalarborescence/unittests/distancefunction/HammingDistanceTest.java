package optimalarborescence.unittests.distancefunction;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import optimalarborescence.distance.HammingDistance;

public class HammingDistanceTest {

    private HammingDistance hammingDistance = new HammingDistance();
    private List<String> testStrings = new ArrayList<>();

    @Before
    public void setUp() {
        testStrings.add("AACGT"); // index 0
        testStrings.add("AAGCT"); // index 1
        testStrings.add("TTGCA"); // index 2
        testStrings.add("EEEEE"); // index 3
        testStrings.add("");      // index 4
        testStrings.add("A");     // index 5
    }

    @Test
    public void testCalculateString() {
        Map<String[], Double> expectedResults = new HashMap<>();
        expectedResults.put(new String[]{testStrings.get(0), testStrings.get(1)}, 2.0);
        expectedResults.put(new String[]{testStrings.get(0), testStrings.get(2)}, 5.0);
        expectedResults.put(new String[]{testStrings.get(3), testStrings.get(3)}, 0.0);
        expectedResults.put(new String[]{testStrings.get(4), testStrings.get(4)}, 0.0);
        expectedResults.put(new String[]{testStrings.get(4), testStrings.get(5)}, -1.0);
        expectedResults.put(new String[]{testStrings.get(5), testStrings.get(5)}, 0.0);
        expectedResults.put(new String[]{testStrings.get(5), "T"}, 1.0);

        for (Map.Entry<String[], Double> entry : expectedResults.entrySet()) {
            String[] pair = entry.getKey();
            double expected = entry.getValue();
            double result;
            try {
                result = hammingDistance.calculate(pair[0], pair[1]);
            } catch (Exception e) {
                result = -1.0;
            }
            assertEquals("Hamming distance between \"" + pair[0] + "\" and \"" + pair[1] + "\"", expected, result, 0.0);
        }
    }


    @Test
    public void testCalculateByteArray() {
        Map<String[], Double> expectedResults = new HashMap<>();
        expectedResults.put(new String[]{testStrings.get(0), testStrings.get(1)}, 2.0);
        expectedResults.put(new String[]{testStrings.get(0), testStrings.get(2)}, 5.0);
        expectedResults.put(new String[]{testStrings.get(4), testStrings.get(4)}, 0.0);
        expectedResults.put(new String[]{testStrings.get(4), testStrings.get(5)}, -1.0);
        expectedResults.put(new String[]{testStrings.get(5), testStrings.get(5)}, 0.0);
        expectedResults.put(new String[]{testStrings.get(5), "T"}, 1.0);

        for (Map.Entry<String[], Double> entry : expectedResults.entrySet()) {
            String[] pair = entry.getKey();
            double expected = entry.getValue();
            double result;
            try {
                byte[] byteArray1 = encodeStringToBytes(pair[0]);
                byte[] byteArray2 = encodeStringToBytes(pair[1]);
                result = hammingDistance.calculate(byteArray1, byteArray2);
            } catch (Exception e) {
                result = -1.0;
            }
            assertEquals("Hamming distance between byte arrays of \"" + pair[0] + "\" and \"" + pair[1] + "\"", expected, result, 0.0);
        }
    }

    private byte[] encodeStringToBytes(String s) {
        int numBases = s.length();
        int numBytes = (numBases * 2 + 7) / 8; // 2 bits per base
        byte[] byteArray = new byte[numBytes];
        for (int i = 0; i < numBases; i++) {
            int baseBits;
            switch (s.charAt(i)) {
                case 'A': baseBits = 0b00; break;
                case 'C': baseBits = 0b01; break;
                case 'G': baseBits = 0b10; break;
                case 'T': baseBits = 0b11; break;
                default: baseBits = 0b00; // Default to 'A' for unknown bases
            }
            int byteIndex = (i * 2) / 8;
            int bitOffset = (i * 2) % 8;
            byteArray[byteIndex] |= (baseBits << (6 - bitOffset));
        }
        return byteArray;
    }
}
