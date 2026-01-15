package optimalarborescence.unittests.sequences;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

import optimalarborescence.sequences.AllelicProfile;

public class AllelicProfileTest {

    private AllelicProfile createAllelicProfile(String sequence) {
        Character[] data = new Character[sequence.length()];
        for (int i = 0; i < sequence.length(); i++) {
            data[i] = sequence.charAt(i);
        }
        return new AllelicProfile(data, sequence.length());
    }

    @Test
    public void testGetElementAt() {
        AllelicProfile profile = createAllelicProfile("ACGT");
        
        assertEquals(Character.valueOf('A'), profile.getElementAt(0));
        assertEquals(Character.valueOf('C'), profile.getElementAt(1));
        assertEquals(Character.valueOf('G'), profile.getElementAt(2));
        assertEquals(Character.valueOf('T'), profile.getElementAt(3));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetElementAtNegativeIndex() {
        AllelicProfile profile = createAllelicProfile("ACGT");
        profile.getElementAt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetElementAtOutOfBounds() {
        AllelicProfile profile = createAllelicProfile("ACGT");
        profile.getElementAt(4);
    }

    @Test
    public void testIsMissingDataAtWithQuestionMark() {
        AllelicProfile profile = createAllelicProfile("A?GT");
        
        assertFalse("Position 0 should not be missing data", profile.isMissingDataAt(0));
        assertTrue("Position 1 should be missing data (?)", profile.isMissingDataAt(1));
        assertFalse("Position 2 should not be missing data", profile.isMissingDataAt(2));
        assertFalse("Position 3 should not be missing data", profile.isMissingDataAt(3));
    }

    @Test
    public void testIsMissingDataAtWithHyphen() {
        AllelicProfile profile = createAllelicProfile("AC-T");
        
        assertFalse("Position 0 should not be missing data", profile.isMissingDataAt(0));
        assertFalse("Position 1 should not be missing data", profile.isMissingDataAt(1));
        assertTrue("Position 2 should be missing data (-)", profile.isMissingDataAt(2));
        assertFalse("Position 3 should not be missing data", profile.isMissingDataAt(3));
    }

    @Test
    public void testIsMissingDataAtWithSpace() {
        AllelicProfile profile = createAllelicProfile("ACG ");
        
        assertFalse("Position 0 should not be missing data", profile.isMissingDataAt(0));
        assertFalse("Position 1 should not be missing data", profile.isMissingDataAt(1));
        assertFalse("Position 2 should not be missing data", profile.isMissingDataAt(2));
        assertTrue("Position 3 should be missing data (space)", profile.isMissingDataAt(3));
    }

    @Test
    public void testIsMissingDataAtWithMultipleMissingSymbols() {
        AllelicProfile profile = createAllelicProfile("A?-T ");
        
        assertFalse("Position 0 should not be missing data", profile.isMissingDataAt(0));
        assertTrue("Position 1 should be missing data (?)", profile.isMissingDataAt(1));
        assertTrue("Position 2 should be missing data (-)", profile.isMissingDataAt(2));
        assertFalse("Position 3 should not be missing data", profile.isMissingDataAt(3));
        assertTrue("Position 4 should be missing data (space)", profile.isMissingDataAt(4));
    }

    @Test
    public void testIsMissingDataAtWithNoMissingValues() {
        AllelicProfile profile = createAllelicProfile("ACGTACGT");
        
        for (int i = 0; i < profile.getLength(); i++) {
            assertFalse("Position " + i + " should not be missing data", profile.isMissingDataAt(i));
        }
    }

    @Test
    public void testIsMissingDataAtWithAllMissingValues() {
        AllelicProfile profile = createAllelicProfile("?-? -");
        
        for (int i = 0; i < profile.getLength(); i++) {
            assertTrue("Position " + i + " should be missing data", profile.isMissingDataAt(i));
        }
    }

    @Test
    public void testGetPositionsWithMissingDataNoMissing() {
        AllelicProfile profile = createAllelicProfile("ACGTACGT");
        
        List<Integer> missingPositions = profile.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertTrue("Missing positions should be empty", missingPositions.isEmpty());
    }

    @Test
    public void testGetPositionsWithMissingDataSomeMissing() {
        AllelicProfile profile = createAllelicProfile("A?C-T G");
        
        List<Integer> missingPositions = profile.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertEquals("Should have 3 missing positions", 3, missingPositions.size());
        assertTrue("Should contain position 1 (?)", missingPositions.contains(1));
        assertTrue("Should contain position 3 (-)", missingPositions.contains(3));
        assertTrue("Should contain position 5 (space)", missingPositions.contains(5));
    }

    @Test
    public void testGetPositionsWithMissingDataAllMissing() {
        AllelicProfile profile = createAllelicProfile("?- ?");
        
        List<Integer> missingPositions = profile.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertEquals("Should have 4 missing positions", 4, missingPositions.size());
        for (int i = 0; i < profile.getLength(); i++) {
            assertTrue("Should contain position " + i, missingPositions.contains(i));
        }
    }

    @Test
    public void testGetPositionsWithMissingDataCaching() {
        AllelicProfile profile = createAllelicProfile("A?C-T");
        
        List<Integer> firstCall = profile.getPositionsWithMissingData();
        List<Integer> secondCall = profile.getPositionsWithMissingData();
        
        assertSame("Should return the same cached instance", firstCall, secondCall);
    }

    @Test
    public void testGetPositionsWithMissingDataEmptySequence() {
        AllelicProfile profile = createAllelicProfile("");
        
        List<Integer> missingPositions = profile.getPositionsWithMissingData();
        
        assertNotNull("Missing positions list should not be null", missingPositions);
        assertTrue("Missing positions should be empty", missingPositions.isEmpty());
    }

    @Test
    public void testCompareAt() {
        AllelicProfile profile1 = createAllelicProfile("ACGTX");
        AllelicProfile profile2 = createAllelicProfile("ACGTZ");
        
        assertEquals("Values at position 0 should be equal", 0, profile1.compareAt(0, profile2));
        assertEquals("Values at position 1 should be equal", 0, profile1.compareAt(1, profile2));
        assertEquals("Values at position 2 should be equal", 0, profile1.compareAt(2, profile2));
        assertEquals("Values at position 3 should be equal", 0, profile1.compareAt(3, profile2));
        assertTrue("Value X should be less than Z", profile1.compareAt(4, profile2) < 0);
    }

    @Test
    public void testCompareAtWithDifferentCharacters() {
        AllelicProfile profile1 = createAllelicProfile("ACGT");
        AllelicProfile profile2 = createAllelicProfile("BCDE");
        
        assertTrue("A should be less than B at position 0", profile1.compareAt(0, profile2) < 0);
        assertEquals("C should be equal to C at position 1", 0, profile1.compareAt(1, profile2));
        assertTrue("G should be greater than D at position 2", profile1.compareAt(2, profile2) > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCompareAtWithDifferentType() {
        AllelicProfile profile = createAllelicProfile("ACGT");
        optimalarborescence.sequences.SequenceTypingData otherSeq = 
            new optimalarborescence.sequences.SequenceTypingData(new Integer[]{1, 2, 3, 4}, 4);
        
        profile.compareAt(0, otherSeq);
    }

    @Test
    public void testGetLength() {
        AllelicProfile profile1 = createAllelicProfile("ACG");
        AllelicProfile profile2 = createAllelicProfile("ACGTACGT");
        AllelicProfile profile3 = createAllelicProfile("");
        
        assertEquals("Length should be 3", 3, profile1.getLength());
        assertEquals("Length should be 8", 8, profile2.getLength());
        assertEquals("Length should be 0", 0, profile3.getLength());
    }

    @Test
    public void testGetData() {
        Character[] data = {'A', 'C', 'G', 'T'};
        AllelicProfile profile = new AllelicProfile(data, data.length);
        
        Character[] retrievedData = profile.getData();
        assertArrayEquals("Data arrays should be equal", data, retrievedData);
    }

    @Test
    public void testEquals() {
        AllelicProfile profile1 = createAllelicProfile("ACGT");
        AllelicProfile profile2 = createAllelicProfile("ACGT");
        AllelicProfile profile3 = createAllelicProfile("ACGG");
        
        assertTrue("Identical profiles should be equal", profile1.equals(profile2));
        assertFalse("Profiles with different values should not be equal", profile1.equals(profile3));
        assertTrue("Profile should equal itself", profile1.equals(profile1));
        assertFalse("Profile should not equal null", profile1.equals(null));
    }

    @Test
    public void testEqualsWithMissingData() {
        AllelicProfile profile1 = createAllelicProfile("A?G-T");
        AllelicProfile profile2 = createAllelicProfile("A?G-T");
        AllelicProfile profile3 = createAllelicProfile("A-G?T");
        
        assertTrue("Profiles with same missing data should be equal", profile1.equals(profile2));
        assertFalse("Profiles with different missing data positions should not be equal", profile1.equals(profile3));
    }

    @Test
    public void testToString() {
        AllelicProfile profile = createAllelicProfile("ACGT");
        String str = profile.toString();
        
        assertTrue("toString should contain class name", str.contains("AllelicProfile"));
        assertTrue("toString should contain data reference", str.contains("data"));
    }

    @Test
    public void testToStringWithMissingData() {
        AllelicProfile profile = createAllelicProfile("A?G-T");
        String str = profile.toString();
        
        assertTrue("toString should contain class name", str.contains("AllelicProfile"));
    }

    @Test
    public void testSingleCharacterProfile() {
        AllelicProfile profile = createAllelicProfile("A");
        
        assertEquals("Length should be 1", 1, profile.getLength());
        assertEquals(Character.valueOf('A'), profile.getElementAt(0));
        assertFalse("Single valid character should not be missing", profile.isMissingDataAt(0));
    }

    @Test
    public void testSingleMissingCharacterProfile() {
        AllelicProfile profile = createAllelicProfile("?");
        
        assertEquals("Length should be 1", 1, profile.getLength());
        assertEquals(Character.valueOf('?'), profile.getElementAt(0));
        assertTrue("Single missing character should be missing", profile.isMissingDataAt(0));
        
        List<Integer> missingPositions = profile.getPositionsWithMissingData();
        assertEquals("Should have 1 missing position", 1, missingPositions.size());
        assertTrue("Should contain position 0", missingPositions.contains(0));
    }

    @Test
    public void testMixedCaseCharacters() {
        AllelicProfile profile = createAllelicProfile("AaBbCc");
        
        assertEquals(Character.valueOf('A'), profile.getElementAt(0));
        assertEquals(Character.valueOf('a'), profile.getElementAt(1));
        
        for (int i = 0; i < profile.getLength(); i++) {
            assertFalse("None should be missing data", profile.isMissingDataAt(i));
        }
    }
}
