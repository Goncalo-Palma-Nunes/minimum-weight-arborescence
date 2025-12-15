package optimalarborescence;

import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.DirectedGraph;
import optimalarborescence.sequences.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.dynamic.*;

import java.util.List;
import java.io.File;
import java.io.FileNotFoundException;

import javax.management.RuntimeErrorException;


public class Main {

    private static final List<String> SEQUENCE_TYPE = List.of("mlst", "allelic");
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
        if (args.length == 4) {
            persistedGraphFile = args[3];
            if (!fileExists(persistedGraphFile)) {
                throw new FileNotFoundException("Persisted graph file does not exist: " + persistedGraphFile);
            }
        }

        int numNeighbors = approximatesGraph();
        String algorithmType = readAlgorithmType();
        
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

    private static Graph initializeGraph(String sequenceType, String inputFile, int numNeighbors) {
        Graph graph;
        if (numNeighbors > 0) { // approximate graph
        } 
        else { // exact graph
        }
        return null;
        // return graph;
    }
}
