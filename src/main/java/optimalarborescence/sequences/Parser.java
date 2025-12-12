package optimalarborescence.sequences;

import optimalarborescence.sequences.*;

import java.util.Scanner;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.ArrayList;

public class Parser {

    private static final char FASTA_HEADER_SYMBOL = '>';

    public static List<AllelicProfile> fastaToAllelicProfiles(String filepath) {
        List<AllelicProfile> allelicProfiles = new ArrayList<>();
        try {
            File file = new File(filepath);
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.charAt(0) == FASTA_HEADER_SYMBOL) {
                    // ignore the description line
                    continue;
                }
                // process the allelic profile line
                Character[] allelicProfile = new Character[line.length()];
                for (int i = 0; i < line.length(); i++) {
                    allelicProfile[i] = line.charAt(i);
                }
                allelicProfiles.add(new AllelicProfile(allelicProfile, allelicProfile.length));
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return allelicProfiles;
    }

    public static List<SequenceTypingData> csvToTypingData(String filepath) {
        List<SequenceTypingData> typingDataList = new ArrayList<>();
        try {
            FileReader filereader = new FileReader(filepath);

            CSVReader csvReader = new CSVReader(filereader);
            String[] nextRecord;

            // skip header
            csvReader.readNext();

            while ((nextRecord = csvReader.readNext()) != null) {
                Integer sequenceType = Integer.parseInt(nextRecord[0]); // not used currently
                Integer[] typingDataSequence = new Integer[nextRecord.length - 1];
                for (int i = 1; i < nextRecord.length; i++) {
                    typingDataSequence[i - 1] = Integer.parseInt(nextRecord[i]);
                }
                typingDataList.add(new SequenceTypingData(typingDataSequence, typingDataSequence.length));
            }
            csvReader.close();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            System.out.println("Parser csvToTypingData exception: Unexpected error occurred.");
            e.printStackTrace();
        }

        return typingDataList;
    }

}
