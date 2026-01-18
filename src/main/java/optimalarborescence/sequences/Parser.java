package optimalarborescence.sequences;

import java.util.Scanner;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import optimalarborescence.exception.NotImplementedException;

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

    public static List<Object[]> readCSVLines(String filepath) {
        List<Object[]> typingDataList = new ArrayList<>();
        try {
            FileReader filereader = new FileReader(filepath);

            CSVReader csvReader = new CSVReaderBuilder(filereader)
                .withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
                .build();
            String[] nextRecord;

            // skip header
            csvReader.readNext();

            while ((nextRecord = csvReader.readNext()) != null) {
                // Parse hexadecimal ST identifier as String (can be too large for Long)
                String sequenceType = nextRecord[0];
                Object[] typingDataSequence = new Object[nextRecord.length];
                typingDataSequence[0] = sequenceType;
                for (int i = 1; i < nextRecord.length; i++) {
                    // check if record can be parsed to long
                    try {
                        typingDataSequence[i] = Long.parseLong(nextRecord[i]);
                    } catch (NumberFormatException e) {
                        typingDataSequence[i] = -1L; // or any other default value for missing data
                    }
                    // typingDataSequence[i] = Long.parseLong(nextRecord[i]);
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

    public static List<SequenceTypingData> processedCSVToTypingData(List<Object[]> rawData) {
        List<SequenceTypingData> typingDataList = new ArrayList<>();
        for (int i = 0; i < rawData.size(); i++) {
            Object[] fullArray = rawData.get(i);
            // Extract only the allele columns (skip ST at index 0)
            Long[] alleles = new Long[fullArray.length - 1];
            for (int j = 1; j < fullArray.length; j++) {
                alleles[j - 1] = (Long) fullArray[j];
            }
            typingDataList.add(new SequenceTypingData(alleles, alleles.length));
        }
        return typingDataList;
    }

    public static String getSTFromProcessedCSVLine(Object[] rawData) {
        return (String) rawData[0];
    }


    public static List<SequenceTypingData> parseCSVWithMissingData(String filepath) {
        List<SequenceTypingData> typingDataList = new ArrayList<>();
        try {
            FileReader filereader = new FileReader(filepath);

            CSVReader csvReader = new CSVReaderBuilder(filereader)
                .withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
                .build();
            String[] nextRecord;

            // skip header
            csvReader.readNext();

            while ((nextRecord = csvReader.readNext()) != null) {
                // Skip ST column (index 0) and process only allele data (from index 1 onwards)
                Long[] alleles = new Long[nextRecord.length - 1];
                
                for (int i = 1; i < nextRecord.length; i++) {
                    // Check if the value is '?' (missing data)
                    if (nextRecord[i].trim().equals("?")) {
                        alleles[i - 1] = -1L; // Use -1 to represent missing data
                    } else {
                        // Try to parse to long
                        try {
                            alleles[i - 1] = Long.parseLong(nextRecord[i].trim());
                        } catch (NumberFormatException e) {
                            alleles[i - 1] = -1L; // Use -1 for any unparseable data
                        }
                    }
                }
                
                typingDataList.add(new SequenceTypingData(alleles, alleles.length));
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

}
