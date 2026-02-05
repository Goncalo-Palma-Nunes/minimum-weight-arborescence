package optimalarborescence.unittests.parser;

import optimalarborescence.sequences.AllelicProfile;
import optimalarborescence.sequences.Parser;
import optimalarborescence.sequences.SequenceTypingData;

import java.util.List;
import java.util.ArrayList;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParserTest {

    String fastaFilepath = System.getProperty("user.dir") + "/src/test/java/optimalarborescence/unittests/parser/arcC_partial_fasta.fas";
    String csvFilepath = System.getProperty("user.dir") + "/src/test/java/optimalarborescence/unittests/parser/staphylococcus_aureus_partial_mlst";
    List<AllelicProfile> allelicProfiles = new ArrayList<>();
    List<String> allelicSequences = new ArrayList<>();
    List<SequenceTypingData> typingDataList = new ArrayList<>() {
        {
            add(new SequenceTypingData(new Long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L}, 7));
            add(new SequenceTypingData(new Long[]{2L, 2L, 2L, 2L, 2L, 2L, 26L}, 7));
            add(new SequenceTypingData(new Long[]{1L, 1L, 1L, 9L, 1L, 1L, 12L}, 7));
            add(new SequenceTypingData(new Long[]{10L, 10L, 8L, 6L, 10L, 3L, 2L}, 7));
            add(new SequenceTypingData(new Long[]{1L, 4L, 1L, 4L, 12L, 1L, 10L}, 7));
        }
    };


    @Test
    public void testFastaToAllelicProfiles() {
        initializeAllelicProfiles();
        List<AllelicProfile> parsedAllelicProfiles = Parser.fastaToAllelicProfiles(fastaFilepath);
        assertEquals(allelicProfiles.size(), parsedAllelicProfiles.size());
        for (int i = 0; i < allelicProfiles.size(); i++) {
            assertArrayEquals(allelicProfiles.get(i).getData(), parsedAllelicProfiles.get(i).getData());
        }
    }

    @Test
    public void testCsvToTypingData() {
        List<SequenceTypingData> parsedTypingDataList = Parser.processedCSVToTypingData(
            Parser.readCSVLines(csvFilepath)
        );
        assertEquals(typingDataList.size(), parsedTypingDataList.size());
        for (int i = 0; i < typingDataList.size(); i++) {
            assertArrayEquals(typingDataList.get(i).getData(), parsedTypingDataList.get(i).getData());
        }
    }

    @Test
    public void testParseCSVWithMissingData() {
        String testFilepath = System.getProperty("user.dir") + "/src/test/java/optimalarborescence/unittests/parser/test_missing_data.csv";
        List<SequenceTypingData> parsedTypingDataList = Parser.parseCSVWithMissingData(testFilepath);
        
        // Verify we have 1 sequence
        assertEquals(1, parsedTypingDataList.size());
        
        SequenceTypingData seq = parsedTypingDataList.get(0);
        
        // Verify the sequence has 6 alleles (ST column is excluded)
        assertEquals(6, seq.getLength());
        
        // Expected values: first allele is missing (represented as -1), others are the parsed long integers
        Long[] expectedValues = new Long[]{
            -1L,  // ? should be -1
            55555555555555555L,
            2222222222222222L,
            7777777777777777L,
            33333333333333333L,
            11111111111111111L
        };
        
        // Verify each allele value
        for (int i = 0; i < expectedValues.length; i++) {
            assertEquals("Allele at position " + i + " does not match", 
                expectedValues[i], 
                seq.getElementAt(i));
        }
        
        // Verify that the first position is correctly identified as missing data
        assertTrue("First allele should be missing data", seq.isMissingDataAt(0));
        
        // Verify that the other positions are not missing data
        for (int i = 1; i < seq.getLength(); i++) {
            assertFalse("Allele at position " + i + " should not be missing data", seq.isMissingDataAt(i));
        }
    }

    String arcC_1 = "TTATTAATCCAACAAGCTAAATCGAACAGTGACACAACGCCGGCAATGCCATTGGATACT" + //
                "TGTGGTGCAATGTCACAGGGTATGATAGGCTATTGGTTGGAAACTGAAATCAATCGCATT" + //
                "TTAACTGAAATGAATAGTGATAGAACTGTAGGCACAATCGTTACACGTGTGGAAGTAGAT" + //
                "AAAGATGATCCACGATTCAATAACCCAACCAAACCAATTGGTCCTTTTTATACGAAAGAA" + //
                "GAAGTTGAAGAATTACAAAAAGAACAGCCAGACTCAGTCTTTAAAGAAGATGCAGGACGT" + //
                "GGTTATAGAAAAGTAGTTGCGTCACCACTACCTCAATCTATACTAGAACACCAGTTAATT" + //
                "CGAACTTTAGCAGACGGTAAAAATATTGTCATTGCATGCGGTGGTGGCGGTATTCCAGTT" + //
                "ATAAAAAAAGAAAATACCTATGAAGGTGTTGAAGCG";

    String arcC_2 = "TTATTAATCCAACAAGCTAAATCGAACAGTGACACAACGCCGGCAATGCCATTGGATACT" + //
                "TGTGGTGCAATGTCACAAGGTATGATAGGCTATTGGTTGGAAACTGAAATCAATCGCATT" + //
                "TTAACTGAAATGAATAGTGATAGAACTGTAGGCACAATCGTAACACGTGTGGAAGTAGAT" + //
                "AAAGATGATCCACGATTTGATAACCCAACTAAACCAATTGGTCCTTTTTATACGAAAGAA" + //
                "GAAGTTGAAGAATTACAAAAAGAACAGCCAGGCTCAGTCTTTAAAGAAGATGCAGGACGT" + //
                "GGTTATAGAAAAGTAGTTGCGTCACCACTACCTCAATCTATACTAGAACACCAGTTAATT" + //
                "CGAACTTTAGCAGACGGTAAAAATATTGTCATTGCATGCGGTGGTGGCGGTATTCCAGTT" + //
                "ATAAAAAAAGAAAATACCTATGAAGGTGTTGAAGCG";

    String arcC_3 = "TTATTAATCCAACAAGCTAAATCGAACAGTGACACAACGCCGGCAATGCCATTGGATACT" + //
                "TGTGGTGCAATGTCACAGGGTATGATAGGCTATTGGTTGGAAACTGAAATCAATCGCATT" + //
                "TTAACTGAAATGAATAGTGATAGAACTGTAGGCACAATCGTTACACGTGTGGAAGTAGAT" + //
                "AAAGATGATCCACGATTTGATAACCCAACTAAACCAATTGGTCCTTTTTATACGAAAGAA" + //
                "GAAGTTGAAGAATTACAAAAAGAACAGCCAGACTCAGTCTTTAAAGAAGATGCAGGACGT" + //
                "GGTTATAGAAAAGTAGTTGCGTCACCACTACCTCAATCTATACTAGAACACCAGTTAATT" + //
                "CGAACTTTAGCAGACGGTAAAAATATTGTCATTGCATGCGGTGGTGGCGGTATTCCAGTT" + //
                "ATAAAAAAAGAAAATACCTATGAAGGTGTTGAAGCG";

    String arcC_4 = "TTATTAATCCAACAAGCTAAATCGAACAGTGACACAACGCCGGCAATGCCATTGGATACT" + //
                "TGTGGTGCAATGTCACAGGGTATGATAGGCTATTGGTTGGAAACTGAAATCAATCGCATT" + //
                "TTAACTGAAATGAATAGTGATAGAACCGTAGGCACAATCGTTACACGTGTGGAAGTAGAT" + //
                "AAAGATGATCCACGATTCAATAACCCAACCAAACCAATTGGTCCTTTTTATACGAAAGAA" + //
                "GAAGTTGAAGAATTACAAAAAGAACAGCCAGACTCAGTCTTTAAAGAAGATGCAGGACGT" + //
                "GGTTATAGAAAAGTAGTTGCGTCACCACTACCTCAATCTATACTAGAACACCAGTTAATT" + //
                "CGAACTTTAGCAGACGGAAAAAATATTGTCATTGCATGCGGTGGTGGCGGTATTCCAGTT" + //
                "ATAAAAAAAGAAAATACCTATGAAGGTGTTGAAGCG";

    String arcC_5 = "TTATTAATCCAACAAGCTAAATCGAACAGTGACACAACGCCGGCAATGCCATTGGATACT" + //
                "TGTGGTGCAATGTCACAGGGTATGATAGGCTATTGGTTGGAAACTGAAATCAATCGCATT" + //
                "TTAACTGAAATGAATAGTGATAGAACTGTAGGCACAATCGTTACACGTGTGGAAGTAGAT" + //
                "AAAGATGATCCACGATTTGATAACCCAACTAAACCAATTGGTCCTTTTTATACGAAAGAA" + //
                "GAAGTTGAAGAATTACAAAAAGAACAGCCAGACTCAGTCTTTAAAGAAGATGCAGGACTT" + //
                "GGTTATAGAAAAGTAGTTGCGTCACCACTACCTCAATCTATACTAGAACACCAGTTAATT" + //
                "CGAACTTTAGCAGACGGTAAAAATATTGTCATTGCATGCGGTGGTGGCGGTATTCCAGTT" + //
                "ATAAAAAAAGAAAATACCTATGAAGGTGTTGAAGCG";

    @Test
    public void testReadCSVLinesWithHexadecimalST() {
        String testFilepath = System.getProperty("user.dir") + "/src/test/java/optimalarborescence/unittests/parser/test_hex_st.csv";
        List<Object[]> rawData = Parser.readCSVLines(testFilepath);
        
        // Verify we have 3 sequences
        assertEquals(3, rawData.size());
        
        // Test 1: Small hex value (a = 10 in decimal)
        Object[] row1 = rawData.get(0);
        assertEquals("a", Parser.getSTFromProcessedCSVLine(row1));
        assertEquals(100L, row1[1]);
        assertEquals(200L, row1[2]);
        assertEquals(300L, row1[3]);
        assertEquals(400L, row1[4]);
        assertEquals(500L, row1[5]);
        
        // Test 2: Large hex value (too large for Long)
        Object[] row2 = rawData.get(1);
        String largeST = Parser.getSTFromProcessedCSVLine(row2);
        assertEquals("66d8ada072e6d80d85bf7635", largeST);
        assertEquals(1L, row2[1]);
        assertEquals(2L, row2[2]);
        assertEquals(3L, row2[3]);
        assertEquals(4L, row2[4]);
        assertEquals(5L, row2[5]);
        
        // Test 3: Medium hex value (ffff = 65535 in decimal)
        Object[] row3 = rawData.get(2);
        assertEquals("ffff", Parser.getSTFromProcessedCSVLine(row3));
        assertEquals(999L, row3[1]);
        assertEquals(888L, row3[2]);
        assertEquals(777L, row3[3]);
        assertEquals(666L, row3[4]);
        assertEquals(555L, row3[5]);
        
        // Verify processedCSVToTypingData works correctly with Object[]
        List<SequenceTypingData> typingData = Parser.processedCSVToTypingData(rawData);
        assertEquals(3, typingData.size());
        
        // Verify first sequence alleles (excluding ST)
        SequenceTypingData seq1 = typingData.get(0);
        assertEquals(5, seq1.getLength());
        assertEquals(Long.valueOf(100L), seq1.getElementAt(0));
        assertEquals(Long.valueOf(500L), seq1.getElementAt(4));
    }

    @Test
    public void testReadCSVLinesSkipsInvalidRows() {
        String testFilepath = System.getProperty("user.dir") + "/src/test/java/optimalarborescence/unittests/parser/test_invalid_rows.csv";
        List<Object[]> rawData = Parser.readCSVLines(testFilepath);
        
        // Should only have 3 valid rows (skipping empty ST row and empty line)
        assertEquals(3, rawData.size());
        
        // Verify the valid rows were parsed correctly
        assertEquals("a", Parser.getSTFromProcessedCSVLine(rawData.get(0)));
        assertEquals("66d8ada072e6d80d85bf7635", Parser.getSTFromProcessedCSVLine(rawData.get(1)));
        assertEquals("ffff", Parser.getSTFromProcessedCSVLine(rawData.get(2)));
        
        // Verify processedCSVToTypingData also works correctly
        List<SequenceTypingData> typingData = Parser.processedCSVToTypingData(rawData);
        assertEquals(3, typingData.size());
        
        // All sequences should have 3 alleles
        for (SequenceTypingData seq : typingData) {
            assertEquals(3, seq.getLength());
        }
    }

    private void initializeAllelicProfiles() {
        allelicSequences.add(arcC_1);
        allelicSequences.add(arcC_2);
        allelicSequences.add(arcC_3);
        allelicSequences.add(arcC_4);
        allelicSequences.add(arcC_5);

        for (String seq : allelicSequences) {
            Character[] allelicProfile = seq.chars().mapToObj(c -> (char)c).toArray(Character[]::new);
            allelicProfiles.add(new AllelicProfile(allelicProfile, allelicProfile.length));
        }
    }
}
