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
            add(new SequenceTypingData(new Integer[]{1, 1, 1, 1, 1, 1, 1}, 7));
            add(new SequenceTypingData(new Integer[]{2, 2, 2, 2, 2, 2, 26}, 7));
            add(new SequenceTypingData(new Integer[]{1, 1, 1, 9, 1, 1, 12}, 7));
            add(new SequenceTypingData(new Integer[]{10, 10, 8, 6, 10, 3, 2}, 7));
            add(new SequenceTypingData(new Integer[]{1, 4, 1, 4, 12, 1, 10}, 7));
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
        List<SequenceTypingData> parsedTypingDataList = Parser.csvToTypingData(csvFilepath);
        assertEquals(typingDataList.size(), parsedTypingDataList.size());
        for (int i = 0; i < typingDataList.size(); i++) {
            assertArrayEquals(typingDataList.get(i).getData(), parsedTypingDataList.get(i).getData());
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
