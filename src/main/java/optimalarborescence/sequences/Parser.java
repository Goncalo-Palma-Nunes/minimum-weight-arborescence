package optimalarborescence.sequences;

import java.util.Scanner;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParserBuilder;

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

            // skip the first line
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
            else {
                scanner.close();
                return allelicProfiles;
            }

            String allelicProfile = "";
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.charAt(0) == FASTA_HEADER_SYMBOL) {
                    // ignore the description line
                    allelicProfiles.add(new AllelicProfile(
                        allelicProfile.chars().mapToObj(c -> (char)c).toArray(Character[]::new), 
                        allelicProfile.length())
                    );
                    allelicProfile = "";
                }
                else {
                    // process the allelic profile line
                    for (int i = 0; i < line.length(); i++) {
                        allelicProfile += line.charAt(i);
                    }
                }
            }
            if (!allelicProfile.isEmpty()) { // add the last allelic profile
                allelicProfiles.add(new AllelicProfile(
                    allelicProfile.chars().mapToObj(c -> (char)c).toArray(Character[]::new), 
                    allelicProfile.length())
                );
            }

            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return allelicProfiles;
    }

    public static List<Integer[]> readCSVLines(String filepath) {
        List<Integer[]> typingDataList = new ArrayList<>();
        try {
            FileReader filereader = new FileReader(filepath);

            CSVReader csvReader = new CSVReaderBuilder(filereader)
                .withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
                .build();
            String[] nextRecord;

            // skip header
            csvReader.readNext();

            while ((nextRecord = csvReader.readNext()) != null) {
                Integer sequenceType = Integer.parseInt(nextRecord[0]); // not used currently
                Integer[] typingDataSequence = new Integer[nextRecord.length];
                typingDataSequence[0] = sequenceType;
                for (int i = 1; i < nextRecord.length; i++) {
                    typingDataSequence[i] = Integer.parseInt(nextRecord[i]);
                }
                // typingDataList.add(new SequenceTypingData(typingDataSequence, typingDataSequence.length));
                typingDataList.add(typingDataSequence);
            }
            csvReader.close();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return typingDataList;
    }

    public static List<SequenceTypingData> processedCSVToTypingData(List<Integer[]> rawData) {
        List<SequenceTypingData> typingDataList = new ArrayList<>();
        for (int i = 1; i < rawData.size(); i++) {
            // start at 1 to skip ID column
            Integer[] dataArray = rawData.get(i);
            typingDataList.add(new SequenceTypingData(dataArray, dataArray.length));
        }
        return typingDataList;
    }

    public static int getSTFromProcessedCSVLine(Integer[] rawData) {
        return rawData[0];
    }

}
