package optimalarborescence;

import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.DirectedGraph;
import optimalarborescence.sequences.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.dynamic.*;
import optimalarborescence.memorymapper.GraphMapper;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.management.RuntimeErrorException;


public class Main {

    private static final String MLST = "mlst";
    private static final String ALLELIC = "allelic";
    private static final List<String> SEQUENCE_TYPE = List.of(MLST, ALLELIC);
    private static final String STATIC_ALGORITHM = "static";
    private static final String DYNAMIC_ALGORITHM = "dynamic";
    private static final String YES = "y";
    private static final String NO = "n";
    private static final String EXIT = "exit";
    
    
    public static void main(String[] args) throws FileNotFoundException {
        
        if (args.length < 3 || args.length > 4) {
            System.err.println("Wrong Invocation: java -jar OptimalArborescence.jar <sequence_type> <input_sequence_file> <output_file> [<persisted_graph_file>]");
            System.exit(1);
        }
        String sequenceType = args[0];
        sequenceType = sequenceType.toLowerCase();
        String inputSequenceFile = args[1]; String outputFile = args[2];
        validateParameters(sequenceType, inputSequenceFile);
        
        String persistedGraphFile = null;
        Graph g = null;
        if (args.length == 4) {
            persistedGraphFile = args[3];
            if (!fileExists(persistedGraphFile)) {
                throw new FileNotFoundException("Persisted graph file does not exist: " + persistedGraphFile);
            }
        }

        int numNeighbors = approximatesGraph();
        g = initializeGraph(sequenceType, inputSequenceFile, numNeighbors, persistedGraphFile);

        String algorithmType = readAlgorithmType();
        List<Point<?>> newPoints = processSequences(sequenceType, inputSequenceFile);
        
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                runStaticCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors);
                break;
            case DYNAMIC_ALGORITHM:
                runDynamicCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors);
                break;
            default:
                throw new RuntimeErrorException(new Error("Something went wrong while selecting the algorithm type."));
        }
    }

    private static int approximatesGraph() {
        System.out.println("Do you want to approximate the graph using an approximate nearest neighbour search algorithm? Enter:\n\t- 0 to use the base data set;\n\t- a positive integer k (k > 0) for the maximum number of neighbors for each node;\n\t- or " + EXIT + " to quit.");

        String response = "";
        while (!response.equals(String.valueOf(YES)) && !response.equals(String.valueOf(NO))) {

            response = System.console().readLine().trim().toLowerCase();
            if (response.equals(EXIT)) {
                System.out.println("Exiting the program.");
                System.exit(0);
            }
            try {
                int k = Integer.parseInt(response);
                if (k >= 0) {
                    return k;
                } 
                else {
                    System.out.println("Invalid response. Please enter 0, a positive integer k (k > 0), or "+ EXIT + " to quit.");
                }
            } 
            catch (NumberFormatException e) {
                System.out.println("Invalid response. Please enter 0, a positive integer k (k > 0), or "+ EXIT + " to quit.");
            }
        }


        return -1;
    }

    private static void validateParameters(String sequenceType, String inputFile) throws FileNotFoundException {
        if (!validSequenceType(sequenceType)) {
            throw new IllegalArgumentException("Invalid sequence type: " + sequenceType + ". Valid types are: " + SEQUENCE_TYPE);
        }
        if (!fileExists(inputFile)) {
            throw new FileNotFoundException("Input file does not exist: " + inputFile);
        }
    }

    private static boolean validSequenceType(String sequenceType) {
        return SEQUENCE_TYPE.contains(sequenceType);
    }

    private static boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }

    private static String readAlgorithmType() {
        System.out.println("Select the algorithm type:\n'static' for the Static Camerini Algorithm\n'dynamic' for the Dynamic Camerini Algorithm\nEnter " + EXIT + " to quit.");

        String response = "";
        while (!response.equals(STATIC_ALGORITHM) && !response.equals(DYNAMIC_ALGORITHM)) {

            response = System.console().readLine().trim().toLowerCase();
            if (response.equals(STATIC_ALGORITHM)) {
                return STATIC_ALGORITHM;
            } else if (response.equals(DYNAMIC_ALGORITHM)) {
                return DYNAMIC_ALGORITHM;
            } else if (response.equals(EXIT)) {
                System.out.println("Exiting the program.");
                System.exit(0);
            } else {
                System.out.println("Invalid response. Please enter 'static' or 'dynamic'. Enter "+ EXIT + " to quit.");
            }
        }
        return " ";
    }

    private static List<Point<?>> processSequences(String sequenceType, String inputFile) {
        List<Point<?>> points = new ArrayList<>();
        
        switch (sequenceType) {
            case MLST:
                List<Integer[]> rawMLSTData = Parser.readCSVLines(inputFile);
                List<SequenceTypingData> mlstData = Parser.processedCSVToTypingData(rawMLSTData);
                for (int i = 0; i < mlstData.size(); i++) {
                    int identifier = Parser.getSTFromProcessedCSVLine(rawMLSTData.get(i));
                    points.add(new Point<>(identifier, mlstData.get(i)));
                }
                break;
            case ALLELIC:
                List<AllelicProfile> allelicProfiles = Parser.fastaToAllelicProfiles(inputFile);

                // TODO - estes IDs estão inválidos para os perfis alélicos
                // - Criar IDs sequencialmete, se for a 1ª execução do algoritmo
                // - Ou extrair do grafo persistido, se for uma continuação
                for (int i = 0; i < allelicProfiles.size(); i++) {
                    points.add(new Point<>(i, allelicProfiles.get(i)));
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported sequence type: " + sequenceType);
        }
        return points;
    }

    private static Graph initializeGraph(String sequenceType, String inputFile, 
                                        int numNeighbors, String persistedGraphFile) throws IOException {
        Graph graph = null;
        if (persistedGraphFile != null) {
            System.out.println("Loading persisted graph from file: " + persistedGraphFile);
            graph = GraphMapper.loadGraph(persistedGraphFile);
            System.out.println("Persisted graph loaded successfully.");
            return graph;
        }

        List<Point<?>> points = processSequences(sequenceType, inputFile);
        if (numNeighbors > 0) { // approximate graph
            NearestNeighbourSearchAlgorithm<?> nnSearchAlgorithm = 
                    new LSH(points);
            graph = new DirectedGraph()
        }
        else { // exact graph
        }
        return null;
        // return graph;
    }
}
