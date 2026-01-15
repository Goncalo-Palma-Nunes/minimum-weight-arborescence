package optimalarborescence.unittests.sequences;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

import optimalarborescence.sequences.SequenceTypingData;

public class SequenceTypingDataTest {

    private SequenceTypingData createSequenceTypingData(Integer... values) {
        return new SequenceTypingData(values, values.length);
    }

    @Test
    public void testGetElementAt() {
        SequenceTypingData seq = createSequenceTypingData(1, 2, 3, 4, 5);
        
        assertEquals(Integer.valueOf(1), seq.getElementAt(0));
        assertEquals(Integer.valueOf(3), seq.getElementAt(2));
        assertEquals(Integer.valueOf(5), seq.getElementAt(4));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetElementAtNegativeIndex() {
        SequenceTypingData seq = createSequenceTypingData(1, 2, 3);
        seq.getElementAt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetElementAtOutOfBounds() {
        SequenceTypingData seq = createSequenceTypingData(1, 2, 3);
        seq.getElementAt(3);
    }

    @Test
    public void testIsMissingDataAtWithMissingValues() {
        SequenceTypingData seq = createSequenceTypingData(1, -1, 3, 0, 5);
        
        assertFalse("Position 0 should not be missing data", seq.isMissingDataAt(0));
        assertTrue("Position 1 should be missing data (-1)", seq.isMissingDataAt(1));
        assertFalse("Position 2 should not be missing data", seq.isMissingDataAt(2));
        assertTrue("Position 3 should be missing data (0)", seq.isMissingDataAt(3));
        assertFalse("Position 4 should not be missing data", seq.isMissingDataAt(4));
    }

    @Test
    public void testIsMissingDataAtWithNoMissingValues() {
        SequenceTypingData seq = createSequenceTypingData(1, 2, 3, 4, 5);
        
        for (int i = 0; i < seq.getLength(); i++) {
            assertFalse("Position " + i + " should not be missing data", seq.isMissingDataAt(i));
        }
    }

    @Test
    public void testIsMissingDataAtWithAllMissingValues() {
        SequenceTypingData seq = createSequenceTypingData(-1, 0, -1, 0);
        
        for (int i = 0; i < seq.getLength(); i++) {
            assertTrue("Position " + i + " should be missing data", seq.isMissingDataAt(i));
        }
    }

    @Test
    public void testGetPositionsWithMissingDataNoMissing() {
        SequenceTypingData seq = createSequenceTypingData(1, 2, 3, 4, 5);
        
        List<Integer> missingPositions = seq.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertTrue("Missing positions should be empty", missingPositions.isEmpty());
    }

    @Test
    public void testGetPositionsWithMissingDataSomeMissing() {
        SequenceTypingData seq = createSequenceTypingData(1, -1, 3, 0, 5, -1);
        
        List<Integer> missingPositions = seq.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertEquals("Should have 3 missing positions", 3, missingPositions.size());
        assertTrue("Should contain position 1", missingPositions.contains(1));
        assertTrue("Should contain position 3", missingPositions.contains(3));
        assertTrue("Should contain position 5", missingPositions.contains(5));
    }

    @Test
    public void testGetPositionsWithMissingDataAllMissing() {
        SequenceTypingData seq = createSequenceTypingData(-1, 0, -1, 0);
        
        List<Integer> missingPositions = seq.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertEquals("Should have 4 missing positions", 4, missingPositions.size());
        for (int i = 0; i < seq.getLength(); i++) {
            assertTrue("Should contain position " + i, missingPositions.contains(i));
        }
    }

    @Test
    public void testGetPositionsWithMissingDataCaching() {
        SequenceTypingData seq = createSequenceTypingData(1, -1, 3, 0, 5);
        
        List<Integer> firstCall = seq.getPositionsWithMissingData();
        List<Integer> secondCall = seq.getPositionsWithMissingData();
        
        assertSame("Should return the same cached instance", firstCall, secondCall);
    }

    @Test
    public void testGetPositionsWithMissingDataEmptySequence() {
        SequenceTypingData seq = createSequenceTypingData();
        
        List<Integer> missingPositions = seq.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertTrue("Missing positions should be empty", missingPositions.isEmpty());
    }

    @Test
    public void testCompareAt() {
        SequenceTypingData seq1 = createSequenceTypingData(1, 2, 3, 4, 5);
        SequenceTypingData seq2 = createSequenceTypingData(1, 3, 3, 2, 6);
        
        assertEquals("Values at position 0 should be equal", 0, seq1.compareAt(0, seq2));
        assertTrue("Value at position 1 in seq1 should be less than seq2", seq1.compareAt(1, seq2) < 0);
        assertEquals("Values at position 2 should be equal", 0, seq1.compareAt(2, seq2));
        assertTrue("Value at position 3 in seq1 should be greater than seq2", seq1.compareAt(3, seq2) > 0);
        assertTrue("Value at position 4 in seq1 should be less than seq2", seq1.compareAt(4, seq2) < 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCompareAtWithDifferentType() {
        SequenceTypingData seq = createSequenceTypingData(1, 2, 3);
        // Create a mock sequence of different type
        optimalarborescence.sequences.AllelicProfile otherSeq = 
            new optimalarborescence.sequences.AllelicProfile(new Character[]{'A', 'B', 'C'}, 3);
        
        seq.compareAt(0, otherSeq);
    }

    @Test
    public void testGetLength() {
        SequenceTypingData seq1 = createSequenceTypingData(1, 2, 3);
        SequenceTypingData seq2 = createSequenceTypingData(1, 2, 3, 4, 5, 6, 7);
        SequenceTypingData seq3 = createSequenceTypingData();
        
        assertEquals("Length should be 3", 3, seq1.getLength());
        assertEquals("Length should be 7", 7, seq2.getLength());
        assertEquals("Length should be 0", 0, seq3.getLength());
    }

    @Test
    public void testGetData() {
        Integer[] data = {1, 2, 3, 4, 5};
        SequenceTypingData seq = new SequenceTypingData(data, data.length);
        
        Integer[] retrievedData = seq.getData();
        assertArrayEquals("Data arrays should be equal", data, retrievedData);
    }

    @Test
    public void testEquals() {
        SequenceTypingData seq1 = createSequenceTypingData(1, 2, 3, 4, 5);
        SequenceTypingData seq2 = createSequenceTypingData(1, 2, 3, 4, 5);
        SequenceTypingData seq3 = createSequenceTypingData(1, 2, 3, 4, 6);
        SequenceTypingData seq4 = createSequenceTypingData(1, 2, 3);
        
        assertTrue("Identical sequences should be equal", seq1.equals(seq2));
        assertFalse("Sequences with different values should not be equal", seq1.equals(seq3));
        assertFalse("Sequences with different lengths should not be equal", seq1.equals(seq4));
        assertTrue("Sequence should equal itself", seq1.equals(seq1));
        assertFalse("Sequence should not equal null", seq1.equals(null));
    }

    @Test
    public void testEqualsWithMissingData() {
        SequenceTypingData seq1 = createSequenceTypingData(1, -1, 3, 0, 5);
        SequenceTypingData seq2 = createSequenceTypingData(1, -1, 3, 0, 5);
        SequenceTypingData seq3 = createSequenceTypingData(1, 0, 3, -1, 5);
        
        assertTrue("Sequences with same missing data should be equal", seq1.equals(seq2));
        assertFalse("Sequences with different missing data positions should not be equal", seq1.equals(seq3));
    }

    @Test
    public void testToString() {
        SequenceTypingData seq = createSequenceTypingData(1, 2, 3, 4, 5);
        String str = seq.toString();
        
        assertTrue("toString should contain class name", str.contains("SequenceTypingData"));
        assertTrue("toString should contain data", str.contains("1"));
        assertTrue("toString should contain data", str.contains("2"));
        assertTrue("toString should contain data", str.contains("5"));
    }

    @Test
    public void testToStringWithMissingData() {
        SequenceTypingData seq = createSequenceTypingData(1, -1, 3, 0, 5);
        String str = seq.toString();
        
        assertTrue("toString should contain missing data marker -1", str.contains("-1"));
        assertTrue("toString should contain missing data marker 0", str.contains("0"));
    }
}
