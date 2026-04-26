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

    public static List<Object[]> readCSVLines(String filepath) {
        List<Object[]> typingDataList = new ArrayList<>();
        int lineNumber = 1; // Start at 1, will be 2 after skipping header
        int skippedRows = 0;
        try {
            FileReader filereader = new FileReader(filepath);

            // First, detect the delimiter by reading the first line
            FileReader delimiterReader = new FileReader(filepath);
            CSVReader tempReader = new CSVReaderBuilder(delimiterReader).build();
            String[] firstLine = tempReader.readNext();
            tempReader.close();
            
            // Detect delimiter: use tab if found, otherwise use comma
            char delimiter = ',';
            if (firstLine != null && firstLine.length == 1 && firstLine[0].contains("\t")) {
                delimiter = '\t';
            }
            System.out.println("Detected CSV delimiter: " + (delimiter == '\t' ? "tab" : "comma"));

            CSVReader csvReader = new CSVReaderBuilder(filereader)
                .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                .build();
            String[] nextRecord;

            // skip header
            csvReader.readNext();
            lineNumber++;

            while ((nextRecord = csvReader.readNext()) != null) {
                lineNumber++;
                
                // Skip rows with insufficient data (need at least ST + 1 allele)
                if (nextRecord.length < 2) {
                    System.err.println("Warning: Skipping line " + lineNumber + " - insufficient columns (found " + nextRecord.length + ", need at least 2)");
                    skippedRows++;
                    continue;
                }
                
                // Parse hexadecimal ST identifier as String (can be too large for Long)
                String sequenceType = nextRecord[0];
                
                // Skip rows with empty or null ST identifier
                if (sequenceType == null || sequenceType.trim().isEmpty()) {
                    System.err.println("Warning: Skipping line " + lineNumber + " - empty or null ST identifier");
                    skippedRows++;
                    continue;
                }
                
                Object[] typingDataSequence = new Object[nextRecord.length];
                typingDataSequence[0] = sequenceType.trim();
                for (int i = 1; i < nextRecord.length; i++) {
                    typingDataSequence[i] = SequenceTypingData.normalizeAlleleValue(nextRecord[i]);
                }
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
            // Skip rows that don't have allelic data (length must be > 1)
            if (fullArray == null || fullArray.length < 2) {
                continue;
            }
            // Extract only the allelic columns (skip ST at index 0)
            Long[] alleles = new Long[fullArray.length - 1];
            for (int j = 1; j < fullArray.length; j++) {
                alleles[j - 1] = SequenceTypingData.normalizeAlleleValue(fullArray[j]);
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
            FileReader delimiterReader = new FileReader(filepath);
            CSVReader tempReader = new CSVReaderBuilder(delimiterReader).build();
            String[] firstLine = tempReader.readNext();
            tempReader.close();
            
            char delimiter = ',';
            if (firstLine != null && firstLine.length == 1 && firstLine[0].contains("\t")) {
                delimiter = '\t';
            }
            
            FileReader filereader = new FileReader(filepath);

            CSVReader csvReader = new CSVReaderBuilder(filereader)
                .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                .build();
            String[] nextRecord;

            // skip header
            csvReader.readNext();

            while ((nextRecord = csvReader.readNext()) != null) {
                // Skip ST column (index 0) and process only allelic data (from index 1 onwards)
                Long[] alleles = new Long[nextRecord.length - 1];
                
                for (int i = 1; i < nextRecord.length; i++) {
                    alleles[i - 1] = SequenceTypingData.normalizeAlleleValue(nextRecord[i]);
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
