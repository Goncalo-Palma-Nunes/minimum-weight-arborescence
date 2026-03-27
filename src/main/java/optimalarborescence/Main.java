package optimalarborescence;

import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.DirectedGraph;
import optimalarborescence.graph.PhylogeneticData;
import optimalarborescence.graph.DistanceMatrix;
import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.distance.HammingDistance;
import optimalarborescence.distance.DirectionalHammingDistance;
import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.sequences.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.SerializableCameriniForest;
import optimalarborescence.inference.dynamic.*;
import optimalarborescence.inference.NeighbourJoining;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.memorymapper.EdgeListMapper;
import optimalarborescence.EntropyParser;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;


import javax.management.RuntimeErrorException;


public class Main {

    private static final String MLST_SYMMETRIC = "mlst-symmetric";
    private static final String MLST_MISSING_DATA = "mlst-missing-data";
    private static final String ALLELIC = "allelic";
    private static final List<String> SEQUENCE_TYPE = List.of(MLST_SYMMETRIC, MLST_MISSING_DATA, ALLELIC);
    private static final List<String> SYMMETRIC_DATA = List.of(MLST_SYMMETRIC, ALLELIC);
    private static final String STATIC_ALGORITHM = "static";
    private static final String DYNAMIC_ALGORITHM = "dynamic";
    private static final String NEIGHBOR_JOINING = "neighborJoining";
    private static final String LSH = "lsh";
    private static final String LSH_EXTENSION = ".lshparams";
    private static final List<String> NN_ALGORITHMS = List.of(LSH);
    private static final String YES = "y";
    private static final String NO = "n";
    private static final String EXIT = "exit";
    private static final String ADD = "add";
    private static final String REMOVE = "remove";
    private static final String UPDATE = "update";
    private static final String TEST = "test";
    private static final String ON_DEMAND = "--on-demand";
    private static final List<String> OPERATION_TYPE = List.of(ADD, REMOVE, UPDATE, TEST);
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());
    private static final int BATCH_SIZE = 1000;  // Number of nodes to process in each batch for incremental graph building
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        // Check for --on-demand flag
        boolean onDemand = false;
        int effectiveArgsLength = args.length;
        
        if (args.length > 0 && args[args.length - 1].equals(ON_DEMAND)) {
            onDemand = true;
            effectiveArgsLength = args.length - 1;
        }
        
        if (effectiveArgsLength < 4 || effectiveArgsLength > 6) {
            System.err.println("Wrong Invocation: java -jar OptimalArborescence.jar <sequence_type> <input_sequence_file> <output_file> <operation_type> [<persisted_graph_file>] [<batch_size>] [--on-demand]\nWhere:\n\t- <sequence_type> is either 'mlst-symmetric', 'mlst-missing-data', or 'allelic'\n\t- <input_sequence_file> is the path to the input sequence file\n\t- <output_file> is the path to the output file\n\t- <operation_type> is either 'add', 'remove', 'update', or 'test'\n\t- <persisted_graph_file> is an optional path to a persisted graph file to continue from a previous run\n\t- <batch_size> is an optional positive integer for test mode only, specifying how many points to add in each batch (only for static and neighborJoining algorithms)\n\t the '--on-demand' flag tells the program to compute edge distances on demand, instead of storing them explicitly in a memory mapped file.");
            System.exit(1);
        }
        String sequenceType = args[0];
        sequenceType = sequenceType.toLowerCase();
        String inputSequenceFile = args[1]; String outputFile = args[2];
        String operationType = args[3].toLowerCase();
        
        String persistedGraphFile = null;
        Integer batchSize = null;
        int sequenceLength = -1;
        PhylogeneticData g = null;
        
        if (effectiveArgsLength >= 5) {
            // Determine if arg[4] is persisted graph file or batch size for test mode
            if (operationType.equals(TEST)) {
                // For test mode, arg[4] could be either persisted file or batch size
                try {
                    batchSize = Integer.parseInt(args[4]);
                    if (batchSize <= 0) {
                        throw new IllegalArgumentException("Batch size must be a positive integer.");
                    }
                    // If there's a 6th argument, it's invalid for test mode
                    if (effectiveArgsLength == 6) {
                        throw new IllegalArgumentException("Test mode accepts only batch_size parameter, not persisted_graph_file.");
                    }
                } catch (NumberFormatException e) {
                    // If arg[4] is not a number, treat it as persisted graph file (for backward compatibility)
                    persistedGraphFile = args[4];
                    if (!fileExists(persistedGraphFile)) {
                        throw new FileNotFoundException("Persisted graph file does not exist: " + persistedGraphFile);
                    }
                    // Check if arg[5] exists and is batch size
                    if (effectiveArgsLength == 6) {
                        try {
                            batchSize = Integer.parseInt(args[5]);
                            if (batchSize <= 0) {
                                throw new IllegalArgumentException("Batch size must be a positive integer.");
                            }
                        } catch (NumberFormatException e2) {
                            throw new IllegalArgumentException("Invalid batch size parameter: " + args[5]);
                        }
                    }
                }
            } else {
                // For non-test modes, arg[4] is persisted graph file
                persistedGraphFile = args[4];
                if (!fileExists(persistedGraphFile)) {
                    throw new FileNotFoundException("Persisted graph file does not exist: " + persistedGraphFile);
                }
                if (effectiveArgsLength == 6) {
                    throw new IllegalArgumentException("Batch size parameter is only valid for test mode.");
                }
            }
        }
        validateParameters(sequenceType, inputSequenceFile, operationType, persistedGraphFile);

        // Test mode: iteratively add points in batches
        if (operationType.equals(TEST)) {
            runTestMode(sequenceType, inputSequenceFile, outputFile, persistedGraphFile, batchSize, onDemand);
            return;
        }

        String algorithmType = readAlgorithmType();
        int numNeighbors = -1;
        if (!algorithmType.equals(NEIGHBOR_JOINING)) {
            // NJ always uses the full graph
            numNeighbors = approximatesGraph();
        }
        List<Point<?>> newPoints = processSequences(sequenceType, inputSequenceFile);
        
        // Validate we have enough data points
        if (newPoints.size() < 2) {
            System.err.println("\nError: Insufficient data points!");
            System.err.println("  - Points found: " + newPoints.size());
            System.err.println("  - Minimum required: 2");
            System.err.println("\nPossible causes:");
            System.err.println("  1. CSV file has too few valid rows");
            System.err.println("  2. Most rows were skipped due to validation errors (check warnings above)");
            System.err.println("  3. File format is incorrect (expecting tab-separated values)");
            System.err.println("\nPlease check your input file: " + inputSequenceFile);
            System.exit(1);
        }
        
        System.out.println("\nSuccessfully loaded " + newPoints.size() + " data points.");
        
        NearestNeighbourSearchAlgorithm<?> nnAlgorithm = null;
        if (numNeighbors > 0) {
            sequenceLength = newPoints.get(0).getSequence().getLength();
            nnAlgorithm = selectNNAlgorithm(sequenceType, sequenceLength, persistedGraphFile);
            outputFile += "_approx_" + numNeighbors;
        }
        else { outputFile += "_exact"; }
        if (onDemand) { outputFile += "_ondemand"; }

        long startTime = System.currentTimeMillis();
        persistedGraphFile = initializeGraph(sequenceType, inputSequenceFile, numNeighbors, persistedGraphFile, nnAlgorithm, newPoints, algorithmType, operationType, outputFile, onDemand);

        
        List<Edge> phylogeny = null;
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                outputFile += "_static_camerini";
                phylogeny = runStaticCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, operationType, onDemand, nnAlgorithm);
                break;
            case DYNAMIC_ALGORITHM:
                outputFile += "_dynamic_camerini";
                phylogeny = runDynamicCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, operationType);
                break;
            case NEIGHBOR_JOINING:
                throw new NotImplementedException("Neighbor Joining algorithm is not implemented yet.");
                // TODO - save the graph

                // break;
            default:
                throw new RuntimeErrorException(new Error("Something went wrong while selecting the algorithm type."));
        }

        if (g instanceof DirectedGraph<?> && nnAlgorithm != null) {
            // Serialize the Nearest Neighbour Search Algorithm parameters if applicable
            if (nnAlgorithm instanceof optimalarborescence.nearestneighbour.LSH<?>) {
                serializeNNAlgorithm(nnAlgorithm, persistedGraphFile, outputFile);
            }
        }

        // save arborescence
        GraphMapper.saveArborescence(phylogeny, outputFile);
        long endTime = System.currentTimeMillis();
        System.out.println("Num new nodes added: " + newPoints.size());
        System.out.println("Num arborescence edges: " + phylogeny.size());
        System.out.println("Execution time: " + (endTime - startTime) + " ms");

        // Save execution time and arborescence cost to a log file
        long cost = phylogeny.stream().mapToLong(Edge::getWeight).sum();
        System.out.println("Arborescence cost: " + cost);
        File logFile = new File(outputFile + "_log.txt");
        try (java.io.FileWriter writer = new java.io.FileWriter(logFile)) {
            writer.write("Num New nodes: " + newPoints.size() + "\n");
            writer.write("Num arborescence edges: " + phylogeny.size() + "\n");
            writer.write("Execution time (ms): " + (endTime - startTime) + "\n");
            writer.write("Arborescence cost: " + cost + "\n");
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }

        if (!isValidArborescence(GraphMapper.loadGraph(persistedGraphFile), new Graph(phylogeny))) {
            System.err.println("Invalid arborescence generated.");
            System.exit(1);
        } else {
            System.out.println("Arborescence validation passed.");
        }
    }

    private static int approximatesGraph() throws IOException{
        System.out.println("Do you want to approximate the graph using an approximate nearest neighbour search algorithm? Enter:\n\t- 0 to use the base data set;\n\t- a positive integer k (k > 0) for the maximum number of neighbors for each node;\n\t- or " + EXIT + " to quit.");

        String response = "";
        while (!response.equals(String.valueOf(YES)) && !response.equals(String.valueOf(NO))) {

            //response = System.console().readLine().trim().toLowerCase();
	    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
	    response = reader.readLine();
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
    
    private static int readBatchSize(String algorithmType) throws IOException {
        System.out.println("Enter the batch size (positive integer) for adding points in test mode, or " + EXIT + " to quit:");
        System.out.println("(Batch size determines how many points are added before inferring the phylogeny)");
        
        String response = "";
        while (true) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            response = reader.readLine();
            if (response.equals(EXIT)) {
                System.out.println("Exiting the program.");
                System.exit(0);
            }
            try {
                int batchSize = Integer.parseInt(response);
                if (batchSize > 0) {
                    return batchSize;
                } else {
                    System.out.println("Invalid response. Please enter a positive integer or " + EXIT + " to quit.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid response. Please enter a positive integer or " + EXIT + " to quit.");
            }
        }
    }

    private static void validateParameters(String sequenceType, String inputFile, String operationType, String persistedGraphFile) throws FileNotFoundException {
        if (!validSequenceType(sequenceType)) {
            throw new IllegalArgumentException("Invalid sequence type: " + sequenceType + ". Valid types are: " + SEQUENCE_TYPE);
        }
        if (!fileExists(inputFile)) {
            throw new FileNotFoundException("Input file does not exist: " + inputFile);
        }
        if (!OPERATION_TYPE.contains(operationType)) {
            throw new IllegalArgumentException("Invalid operation type: " + operationType + ". Valid types are: " + OPERATION_TYPE);
        }
        if ((operationType.equals(UPDATE) || operationType.equals(REMOVE)) && persistedGraphFile == null) {
            throw new NotImplementedException("Update and Remove operations require a persisted graph file to continue from a previous run.");
        }
    }

    private static boolean validSequenceType(String sequenceType) {
        return SEQUENCE_TYPE.contains(sequenceType);
    }

    private static boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }

    private static String readAlgorithmType() throws IOException {
        System.out.println("Select the algorithm type:\n'static' for the Static Camerini Algorithm\n'dynamic' for the Dynamic Camerini Algorithm\nEnter " + EXIT + " to quit.");

        String response = "";
        while (!response.equals(STATIC_ALGORITHM) && !response.equals(DYNAMIC_ALGORITHM)) {

            //response = System.console().readLine().trim().toLowerCase();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
	    response = reader.readLine().trim().toLowerCase();
	    if (response.equals(STATIC_ALGORITHM)) {
                return STATIC_ALGORITHM;
            } else if (response.equals(DYNAMIC_ALGORITHM)) {
                return DYNAMIC_ALGORITHM;
            } else if (response.equals(NEIGHBOR_JOINING)) {
                return NEIGHBOR_JOINING;
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
            case MLST_SYMMETRIC:
            case MLST_MISSING_DATA:
                List<Object[]> rawMLSTData = Parser.readCSVLines(inputFile);
                List<SequenceTypingData> mlstData = Parser.processedCSVToTypingData(rawMLSTData);
                for (int i = 0; i < mlstData.size(); i++) {
                    String identifier = Parser.getSTFromProcessedCSVLine(rawMLSTData.get(i));
                    points.add(new Point<>(i, mlstData.get(i)));
                }
                break;
            case ALLELIC:
                // TO DO - implementar
                throw new NotImplementedException("Handling of allelic sequences has not been implemented yet.");
                // List<AllelicProfile> allelicProfiles = Parser.fastaToAllelicProfiles(inputFile);

                // // Nota - estes IDs estão inválidos para os perfis alélicos
                // // - Criar IDs sequencialmete, se for a 1ª execução do algoritmo
                // // - Ou extrair do grafo persistido, se for uma continuação
                // for (int i = 0; i < allelicProfiles.size(); i++) {
                //     points.add(new Point<>(i, allelicProfiles.get(i)));
                // }
                // break;
            default:
                throw new IllegalArgumentException("Unsupported sequence type: " + sequenceType);
        }
        return points;
    }

    private static String initializeGraph(String sequenceType, String inputFile, 
                                        int numNeighbors, String persistedGraphFile,
                                        NearestNeighbourSearchAlgorithm<?> nnAlgorithm, List<Point<?>> points,
                                        String algorithmType, String operationType, String outputFile, boolean onDemand) throws IOException {
        if (onDemand) {
            // Save graph with nodes only, no edges
            GraphMapper.saveGraph(points.stream().
            map(p -> new Node(p.getSequence(), p.getId()))
                                                .toList(), points.get(0).getSequence().getLength(), outputFile);
            return outputFile;
        }


        if (persistedGraphFile != null) {
            // Graph already exists. Add the new edges/points to it if needed.
            if (operationType.equals(ADD)) {
                // Load existing graph
                if (numNeighbors > 0) {
                    // Approximate graph
                    List<Node> newNodes = points.stream()
                                                .map(p -> new Node(p.getSequence(), p.getId()))
                                                .toList();
                    addNodesIncrementallyToApproximateGraph(newNodes, outputFile, points.get(0).getSequence().getLength(), nnAlgorithm, numNeighbors, sequenceType);
                }
                else {
                    List<Node> newNodes = points.stream()
                                                .map(p -> new Node(p.getSequence(), p.getId()))
                                                .toList();
                    addNodesIncrementallyToExactGraph(newNodes, outputFile, points.get(0).getSequence().getLength(), sequenceType);
                }
            }
            return persistedGraphFile;
        }

        if (algorithmType.equals(NEIGHBOR_JOINING)) {
            // Create full DistanceMatrix from points
            throw new NotImplementedException("Building DistanceMatrix from scratch is not implemented yet.");
        }
        else { // exact graph
            // Use incremental construction to avoid memory overflow
            String tempFile = outputFile + "_building";
            if (numNeighbors > 0) {
                generateApproximateGraphIncrementally(points, tempFile, nnAlgorithm, numNeighbors, sequenceType);
            }
            else {
                generateExactGraphIncrementally(points, tempFile, sequenceType);
            }
            return tempFile;
        }
    }

    private static boolean validNNAlgorithm(String response) {
        return NN_ALGORITHMS.contains(response);
    }

    private static NearestNeighbourSearchAlgorithm<?> selectNNAlgorithm(String sequenceType, int sequenceLength, String persistedGraphFile) throws IOException {
        System.out.println("Select the Nearest Neighbour Search Algorithm:\n'"+ LSH + "' for Locality Sensitive Hashing\nEnter " + EXIT + " to quit.");
        String response = "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (!validNNAlgorithm(response)) {
            if (System.console() != null) {
                response = System.console().readLine().trim().toLowerCase();
            } else {
                response = reader.readLine().trim().toLowerCase();
            }
            switch (response) {
                case LSH:
                    if (persistedGraphFile != null) {
                        // Load previously saved LSH parameters
                        try {
                            String lshParamsFile = persistedGraphFile;

                            // replace file extension with .lshparams
                            int dotIndex = lshParamsFile.lastIndexOf('.');
                            if (dotIndex != -1) {
                                lshParamsFile = lshParamsFile.substring(0, dotIndex) + LSH_EXTENSION;
                            } else {
                                lshParamsFile = lshParamsFile + LSH_EXTENSION;
                            }

                            return optimalarborescence.nearestneighbour.LSH.loadLSH(lshParamsFile);
                        }
                        catch (Exception e) { // File doesn't exist or can't be loaded
                            break; // proceed to build new LSH
                        }
                    }
                    return buildLSH(sequenceLength, sequenceType);
                case EXIT:
                    System.out.println("Exiting the program.");
                    System.exit(0);
                default:
                    System.out.println("Invalid response. Please enter '" + LSH + "'. Enter "+ EXIT + " to quit.");
            }
        }

        return null;
    }

    private static LSH<?> buildLSH(int sequenceLength, String sequenceType) {
        // System.out.println("Enter a sequence of positive integers for the amount of compared sequence positions, the number of hash tables, and the maximum distance between two points:\n\tFormat: <num_compared_positions> <num_hash_tables> <max_distance>\n\tExample: 10 5 3\n\tEnter " + EXIT + " to quit.\n");
        // String response = "";


        System.out.println("Building LSH with default parameters: comparing the " + 1300 + " highest entropy positions, using 1 hash table, and a maximum distance of 1671).");
        int numComparedPositions = 1300;
        int numHashTables = 1;
        int maxDistance = 1671;
        DistanceFunction distanceFunction = SYMMETRIC_DATA.contains(sequenceType) ? new HammingDistance() : new DirectionalHammingDistance();
        List<Integer> highestEntropyIndices;
        try {
            highestEntropyIndices = EntropyParser.parseTopEntropies("/scratch/gn/entropy_sorted.json", numComparedPositions);
        }
        catch (IOException e) {
            highestEntropyIndices = new ArrayList<>();
            for (int i = 0; i < numComparedPositions; i++) {
                highestEntropyIndices.add(i);
            }
        }

        return new LSH<>(numComparedPositions, numHashTables, 0, sequenceLength - 1, distanceFunction, maxDistance, highestEntropyIndices);
    }


    private static Edge buildEdge(Node u, Node v, String sequenceType, DistanceFunction distanceFunction) {
        int dist = (int) distanceFunction.calculate(u.getPoint().getSequence(), v.getPoint().getSequence());
        return new Edge(u, v, dist);
    }


    /**
     * Generates an exact graph incrementally to avoid memory overflow.
     * Creates initial graph with 2 nodes, saves to memory-mapped file,
     * then adds remaining nodes in batches, computing and persisting edges incrementally.
     * <p>
     * Writes directly to memory-mapped files.
     * 
     * @param points All points to include in the graph
     * @param outputFile Path for the memory-mapped graph file
     */
    private static void generateExactGraphIncrementally(List<Point<?>> points, String outputFile, String sequenceType) throws IOException {
        if (points.size() < 2) {
            throw new IllegalArgumentException(
                "At least 2 points are required to build a graph. Found: " + points.size() + " points. " +
                "This should have been caught earlier - please report this as a bug."
            );
        }
        
        int sequenceLength = points.get(0).getSequence().getLength();
        DistanceFunction distanceFunction = SYMMETRIC_DATA.contains(sequenceType) ? new HammingDistance() : new DirectionalHammingDistance();
        
        // Initialize with first 2 nodes
        List<Node> initialNodes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            initialNodes.add(new Node(points.get(i).getSequence(), points.get(i).getId()));
        }
        
        // Create minimal graph with just 2 nodes
        Graph graph = new Graph(new ArrayList<>());
        graph.addNode(initialNodes.get(0));
        graph.addNode(initialNodes.get(1));
        
        // Create first two edges
        graph.addEdge(buildEdge(initialNodes.get(0), initialNodes.get(1), sequenceType, distanceFunction));
        graph.addEdge(buildEdge(initialNodes.get(1), initialNodes.get(0), sequenceType, distanceFunction));
        
        // Save initial graph to memory-mapped file
        ensureDirectoryExists(outputFile);
        GraphMapper.saveGraph(graph, sequenceLength, outputFile);
        
        // Keep a cache of nodes
        List<Node> allNodes = new ArrayList<>(initialNodes);
        
        // Add remaining nodes in batches
        int totalPoints = points.size();
        long startTime = System.currentTimeMillis();
        for (int i = 2; i < totalPoints; i += BATCH_SIZE) {
            int endIdx = Math.min(i + BATCH_SIZE, totalPoints);
            List<Point<?>> batchPoints = points.subList(i, endIdx);
            int batchSize = batchPoints.size();
            
            long batchStart = System.currentTimeMillis();
            System.out.println("\n=== Processing batch: nodes " + (i + 1) + " to " + endIdx + " of " + totalPoints + " ===");
            
            // Create nodes for this batch
            List<Node> batchNodes = new ArrayList<>();
            for (Point<?> point : batchPoints) {
                batchNodes.add(new Node(point.getSequence(), point.getId()));
            }
            
            // Prepare edges for all new nodes
            // Use cached allNodes instead of reloading from file
            Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
            
            System.out.println("Computing distances for " + batchSize + " new nodes against " + allNodes.size() + " existing nodes...");
            long distStart = System.currentTimeMillis();
            
            for (Node newNode : batchNodes) {
                List<Edge> incomingEdges = new ArrayList<>();
                
                // Create edges between existing nodes and new node
                for (Node existingNode : allNodes) {
                    incomingEdges.add(buildEdge(existingNode, newNode, sequenceType, distanceFunction));
                    Edge outgoingEdge = buildEdge(newNode, existingNode, sequenceType, distanceFunction);
                    nodeEdgesMap.computeIfAbsent(existingNode, k -> new ArrayList<>()).add(outgoingEdge);
                }
                
                nodeEdgesMap.put(newNode, incomingEdges);
            }
            
            long distEnd = System.currentTimeMillis();
            System.out.println("Distance computation: " + (distEnd - distStart) + " ms");
            
            List<List<Edge>> batchEdgeLists = new ArrayList<>(batchNodes.size());
            for (Node node : batchNodes) {
                batchEdgeLists.add(nodeEdgesMap.get(node));
            }
        
            // Add intra-batch edges
            System.out.println("Computing intra-batch edges...");
            for (int j = 0; j < batchNodes.size(); j++) {
                Node nodeJ = batchNodes.get(j);
                // List<Edge> nodeJEdges = nodeEdgesMap.get(nodeJ);
                List<Edge> nodeJEdges = batchEdgeLists.get(j);
                
                for (int k = 0; k < j; k++) {
                    Node nodeK = batchNodes.get(k);
                    // List<Edge> nodeKEdges = nodeEdgesMap.get(nodeK);
                    List<Edge> nodeKEdges = batchEdgeLists.get(k);
                    Edge incoming = buildEdge(nodeK, nodeJ, sequenceType, distanceFunction);
                    if (!SYMMETRIC_DATA.contains(sequenceType)) {
                        // For directional data, add edges in both directions
                         Edge outgoing = buildEdge(nodeJ, nodeK, sequenceType, distanceFunction);
                            nodeKEdges.add(outgoing);
                    }
                    else nodeKEdges.add(new Edge(nodeJ, nodeK, incoming.getWeight())); // For symmetric data, use same weight for both directions
                    nodeJEdges.add(buildEdge(nodeK, nodeJ, sequenceType, distanceFunction)); // Incoming
                }
            }
            
            // Add all nodes and edges in one batch operation
            System.out.println("Writing to memory-mapped file...");
            long writeStart = System.currentTimeMillis();
            GraphMapper.addNodesBatch(batchNodes, nodeEdgesMap, new HashMap<>(), outputFile, sequenceLength);
            long writeEnd = System.currentTimeMillis();
            System.out.println("File write: " + (writeEnd - writeStart) + " ms");
            
            // Add new nodes to cache for next iteration
            allNodes.addAll(batchNodes);
            
            // Clear map to free memory
            nodeEdgesMap.clear();
            
            long batchEnd = System.currentTimeMillis();
            long batchTime = batchEnd - batchStart;
            long totalTime = batchEnd - startTime;
            double avgTimePerNode = batchTime / (double) batchSize;
            int nodesRemaining = totalPoints - endIdx;
            long estimatedRemaining = (long) (nodesRemaining * avgTimePerNode / 1000.0);
            
            System.out.println("Batch completed in " + batchTime + " ms (" + String.format("%.2f", avgTimePerNode) + " ms/node)");
            System.out.println("Total time: " + (totalTime / 1000) + " seconds");
            System.out.println("Estimated time remaining: ~" + estimatedRemaining + " seconds");
            System.out.println("Nodes in cache: " + allNodes.size());
        }
        
        System.out.println("Graph construction complete. All nodes and edges saved to memory-mapped file.");
    }


    private static void generateApproximateGraphIncrementally(List<Point<?>> points, String outputFile, 
                                                  NearestNeighbourSearchAlgorithm<?> nnAlgorithm,
                                                  int numNeighbors, String sequenceType) throws IOException {
        if (points.size() < 2) {
            throw new IllegalArgumentException("At least 2 points are required to build a graph.");
        }
        
        int sequenceLength = points.get(0).getSequence().getLength();
        DistanceFunction distanceFunction = SYMMETRIC_DATA.contains(sequenceType) 
            ? new HammingDistance() 
            : new DirectionalHammingDistance();
        
        @SuppressWarnings("unchecked")
        NearestNeighbourSearchAlgorithm<Object> nnAlgo = (NearestNeighbourSearchAlgorithm<Object>) nnAlgorithm;
        
        // Maintain node map for efficiency
        Map<Integer, Node> nodeMap = new HashMap<>();
        
        // Initialize with first 2 nodes
        List<Node> initialNodes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Node node = new Node(points.get(i).getSequence(), points.get(i).getId());
            initialNodes.add(node);
            nodeMap.put(node.getId(), node);
        }
        
        // Create minimal graph with 2 nodes
        Graph graph = new Graph(new ArrayList<>());
        graph.addNode(initialNodes.get(0));
        graph.addNode(initialNodes.get(1));
        graph.addEdge(buildEdge(initialNodes.get(0), initialNodes.get(1), sequenceType, distanceFunction));
        // TODO - ADICIONAR EDGE NA OUTRA DIREÇÃO TAMBÉM
        
        // Add initial nodes to NN algorithm
        for (Node node : initialNodes) {
            @SuppressWarnings("unchecked")
            Point<Object> point = (Point<Object>) node.getPoint();
            nnAlgo.storePoint(point);
        }
        
        // Save initial graph
        ensureDirectoryExists(outputFile);
        GraphMapper.saveGraph(graph, sequenceLength, outputFile);
        
        // Add remaining nodes in batches
        int totalPoints = points.size();
        long startTime = System.currentTimeMillis();
        
        for (int i = 2; i < totalPoints; i += BATCH_SIZE) {
            int endIdx = Math.min(i + BATCH_SIZE, totalPoints);
            List<Point<?>> batchPoints = points.subList(i, endIdx);
            int batchSize = batchPoints.size();
            
            long batchStart = System.currentTimeMillis();
            System.out.println("\n=== Processing batch: nodes " + (i + 1) + " to " + endIdx + " of " + totalPoints + " ===");
            
            // Create nodes for this batch
            List<Node> batchNodes = new ArrayList<>();
            for (Point<?> point : batchPoints) {
                Node node = new Node(point.getSequence(), point.getId());
                batchNodes.add(node);
                nodeMap.put(node.getId(), node);
            }
            
            // Insert all batch nodes into NN algorithm BEFORE searching
            System.out.println("Inserting " + batchSize + " nodes into NN algorithm...");
            for (Node node : batchNodes) {
                @SuppressWarnings("unchecked")
                Point<Object> point = (Point<Object>) node.getPoint();
                nnAlgo.storePoint(point);
            }
            
            // Find nearest neighbors and create edges
            Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
            Map<Node, List<Edge>> existingNodeNewEdges = new HashMap<>();
            Set<String> createdEdges = new HashSet<>();  // Track edges to avoid duplicates
            
            System.out.println("Finding " + numNeighbors + " nearest neighbors for " + batchSize + " new nodes...");
            long distStart = System.currentTimeMillis();
            
            for (Node newNode : batchNodes) {
                List<Edge> incomingEdges = new ArrayList<>();
                
                // Find k nearest neighbors
                @SuppressWarnings("unchecked")
                Point<Object> queryPoint = (Point<Object>) newNode.getPoint();
                List<Point<Object>> neighbors = nnAlgo.neighbourSearch(queryPoint, numNeighbors);
                
                // Create edges between neighbors and new node
                for (Point<?> neighborPoint : neighbors) {
                    // Skip self-loops
                    if (neighborPoint.getId() == newNode.getId()) {
                        continue;
                    }
                    
                    Node neighborNode = nodeMap.get(neighborPoint.getId());
                    if (neighborNode == null) {
                        continue; // Shouldn't happen, but be safe
                    }
                    
                    Edge edge = buildEdge(neighborNode, newNode, sequenceType, distanceFunction);
                    
                    // Create unique key to avoid duplicates
                    String edgeKey;
                    if (SYMMETRIC_DATA.contains(sequenceType)) {
                        // For symmetric: unordered pair (always min_id, max_id)
                        int minId = Math.min(edge.getSource().getId(), edge.getDestination().getId());
                        int maxId = Math.max(edge.getSource().getId(), edge.getDestination().getId());
                        edgeKey = minId + "-" + maxId;
                    } else {
                        // For asymmetric: ordered pair (source_id -> dest_id)
                        edgeKey = edge.getSource().getId() + "->" + edge.getDestination().getId();
                    }
                    
                    // Only add if not already created
                    if (createdEdges.add(edgeKey)) {
                        if (edge.getDestination().equals(newNode)) {
                            // Edge points TO new node
                            incomingEdges.add(edge);
                        } else {
                            // Edge points TO existing/other node
                            Node destNode = edge.getDestination();
                            existingNodeNewEdges.computeIfAbsent(destNode, k -> new ArrayList<>()).add(edge);
                        }
                    }
                }
                
                nodeEdgesMap.put(newNode, incomingEdges);
            }
            
            long distEnd = System.currentTimeMillis();
            System.out.println("NN search and edge creation: " + (distEnd - distStart) + " ms");
            
            // Write to memory-mapped file
            System.out.println("Writing to memory-mapped file...");
            long writeStart = System.currentTimeMillis();
            GraphMapper.addNodesBatch(batchNodes, nodeEdgesMap, existingNodeNewEdges, outputFile, sequenceLength);
            long writeEnd = System.currentTimeMillis();
            System.out.println("File write: " + (writeEnd - writeStart) + " ms");
            
            // Clear maps to free memory
            nodeEdgesMap.clear();
            existingNodeNewEdges.clear();
            createdEdges.clear();
            
            long batchEnd = System.currentTimeMillis();
            long batchTime = batchEnd - batchStart;
            long totalTime = batchEnd - startTime;
            double avgTimePerNode = batchTime / (double) batchSize;
            int nodesRemaining = totalPoints - endIdx;
            long estimatedRemaining = (long) (nodesRemaining * avgTimePerNode / 1000.0);
            
            System.out.println("Batch completed in " + batchTime + " ms (" + String.format("%.2f", avgTimePerNode) + " ms/node)");
            System.out.println("Total time: " + (totalTime / 1000) + " seconds");
            System.out.println("Estimated time remaining: ~" + estimatedRemaining + " seconds");
        }
        
        System.out.println("Approximate graph construction complete. All nodes and edges saved to memory-mapped file.");
    }

    /**
     * Adds nodes incrementally to an existing graph, computing and persisting edges in batches
     * to avoid memory overflow.
     * <p>
     * Uses a memory cache of nodes to avoid expensive file reloads.
     * 
     * @param nodesToAdd List of nodes to add
     * @param outputFile Path to the memory-mapped graph file
     * @param sequenceLength Length of sequences (for serialization)
     */
    private static void addNodesIncrementallyToExactGraph(List<Node> nodesToAdd, 
                                                          String outputFile, int sequenceLength, String sequenceType) throws IOException {
        if (nodesToAdd.isEmpty()) {
            return;
        }
        
        DistanceFunction distanceFunction = SYMMETRIC_DATA.contains(sequenceType) ? new HammingDistance() : new DirectionalHammingDistance();
        
        System.out.println("Adding " + nodesToAdd.size() + " nodes incrementally...");
        
        // Load existing nodes from file
        Map<Integer, Node> existingNodeMap = GraphMapper.loadNodeMap(outputFile);
        List<Node> existingNodes = new ArrayList<>(existingNodeMap.values());
        
        System.out.println("Loaded " + existingNodes.size() + " existing nodes from file.");
        
        // Prepare edges for all new nodes
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        Map<Node, List<Edge>> existingNodeNewEdges = new HashMap<>();  // Track edges TO existing nodes
        
        long distStart = System.currentTimeMillis();
        for (int i = 0; i < nodesToAdd.size(); i++) {
            Node newNode = nodesToAdd.get(i);
            List<Edge> incomingEdges = new ArrayList<>();
            
            // Create edges between all existing nodes and new node
            for (Node existingNode : existingNodes) {
                Edge incoming = buildEdge(existingNode, newNode, sequenceType, distanceFunction);
                Edge outgoing = new Edge(newNode, existingNode, incoming.getWeight());
                incomingEdges.add(incoming);
                existingNodeNewEdges.computeIfAbsent(existingNode, k -> new ArrayList<>()).add(outgoing);
            }
            
            // Create edges between previously added nodes in this batch and new node
            for (int j = 0; j < i; j++) {
                Node previousNode = nodesToAdd.get(j);
                Edge incoming = buildEdge(previousNode, newNode, sequenceType, distanceFunction);
                Edge outgoing = new Edge(newNode, previousNode, incoming.getWeight());
                incomingEdges.add(incoming);
                existingNodeNewEdges.computeIfAbsent(previousNode, k -> new ArrayList<>()).add(outgoing);
            }
            
            nodeEdgesMap.put(newNode, incomingEdges);
            
            if ((i + 1) % 100 == 0) {
                System.out.println("Prepared " + (i + 1) + "/" + nodesToAdd.size() + " nodes");
            }
        }
        long distEnd = System.currentTimeMillis();
        System.out.println("Distance computation: " + (distEnd - distStart) + " ms");
        
        // Add all nodes and edges in one batch operation
        long writeStart = System.currentTimeMillis();
        GraphMapper.addNodesBatch(nodesToAdd, nodeEdgesMap, existingNodeNewEdges, outputFile, sequenceLength);
        long writeEnd = System.currentTimeMillis();
        System.out.println("File write: " + (writeEnd - writeStart) + " ms");
        
        System.out.println("All " + nodesToAdd.size() + " nodes added successfully.");
    }

    private static void addNodesIncrementallyToApproximateGraph(List<Node> nodesToAdd, 
                                                          String outputFile, int sequenceLength,
                                                          NearestNeighbourSearchAlgorithm<?> nnAlgorithm,
                                                          int numNeighbors, String sequenceType) throws IOException {
        if (nodesToAdd.isEmpty()) return;
        if (nnAlgorithm == null || numNeighbors <= 0) {
            throw new IllegalArgumentException("Nearest Neighbour Search Algorithm and number of neighbors must be provided for approximate graph.");
        }


        DistanceFunction distanceFunction = SYMMETRIC_DATA.contains(sequenceType) ? new HammingDistance() : new DirectionalHammingDistance();
        System.out.println("Adding " + nodesToAdd.size() + " nodes incrementally to approximate graph...");

        // Load existing nodes from file
        Map<Integer, Node> existingNodeMap = GraphMapper.loadNodeMap(outputFile);
        List<Node> existingNodes = new ArrayList<>(existingNodeMap.values());
        System.out.println("Loaded " + existingNodes.size() + " existing nodes from file.");

        // Prepare edges for all new nodes
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        Map<Node, List<Edge>> existingNodeNewEdges = new HashMap<>();  // Track edges TO existing nodes
        long distStart = System.currentTimeMillis();
        for (int i = 0; i < nodesToAdd.size(); i++) {
            Node newNode = nodesToAdd.get(i);
            List<Edge> incomingEdges = new ArrayList<>();
            // Find nearest neighbors using the NN algorithm
            @SuppressWarnings("unchecked")
            List<Point<Object>> neighbors = ((NearestNeighbourSearchAlgorithm<Object>) nnAlgorithm).neighbourSearch((Point<Object>) newNode.getPoint(), numNeighbors);
            // Create edges between neighbors and new node
            for (Point<?> neighborPoint : neighbors) {
                Node neighborNode = existingNodeMap.get(neighborPoint.getId());
                if (neighborNode != null) {
                    Edge edge = buildEdge(neighborNode, newNode, sequenceType, distanceFunction);
                    
                    if (edge.getDestination().equals(newNode)) {
                        // Edge points TO new node (incoming to new node)
                        incomingEdges.add(edge);
                    } else {
                        // Edge points FROM new node TO existing node
                        existingNodeNewEdges.computeIfAbsent(neighborNode, k -> new ArrayList<>()).add(edge);
                    }
                }
            }
            nodeEdgesMap.put(newNode, incomingEdges);
            existingNodes.add(newNode);
            existingNodeMap.put(newNode.getId(), newNode);
            if ((i + 1) % 100 == 0) {
                System.out.println("Prepared " + (i + 1) + "/" + nodesToAdd.size() + " nodes");
            }
        }
        long distEnd = System.currentTimeMillis();
        System.out.println("Distance computation: " + (distEnd - distStart) + " ms");
        
        // Add all nodes and edges in one batch operation
        long writeStart = System.currentTimeMillis();
        GraphMapper.addNodesBatch(nodesToAdd, nodeEdgesMap, existingNodeNewEdges, outputFile, sequenceLength);
        long writeEnd = System.currentTimeMillis();
        System.out.println("File write: " + (writeEnd - writeStart) + " ms");
        
        System.out.println("All " + nodesToAdd.size() + " nodes added successfully.");
    }

    private static void ensureDirectoryExists(String filePath) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    private static List<Edge> runStaticCameriniAlgorithm(String sequenceType, String inputFile, String outputFile, int numNeighbors, List<Point<?>> points, String persistedGraphFile, String operationType, boolean onDemand, NearestNeighbourSearchAlgorithm<?> nnAlgorithm) throws IOException {

        List<Node> nodesToProcess = null;
        nodesToProcess = points.stream().map(p -> new Node(p.getSequence(), p.getId())).toList();
        switch (operationType) {
            case ADD:
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), new HashMap<>(), persistedGraphFile, points.get(0).getSequence().getLength());
                break;
            case REMOVE:
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, points.get(0).getSequence().getLength());
                break;
            case UPDATE:
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, points.get(0).getSequence().getLength());
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), new HashMap<>(), persistedGraphFile, points.get(0).getSequence().getLength());
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation type: " + operationType);
        }
        
        if (persistedGraphFile == null) {
            throw new IllegalArgumentException("Persisted graph file must be provided for Static Camerini Algorithm.");
        }
        DistanceFunction distanceFunction = SYMMETRIC_DATA.contains(sequenceType) ? new HammingDistance() : new DirectionalHammingDistance();
        CameriniForest camerini = new SerializableCameriniForest(EDGE_COMPARATOR, persistedGraphFile, onDemand, nnAlgorithm, numNeighbors, distanceFunction, SYMMETRIC_DATA.contains(sequenceType));
        
        return camerini.inferPhylogeny(null).getEdges();
    }

    private static List<Edge> runDynamicCameriniAlgorithm(String sequenceType, String inputFile, String outputFile, int numNeighbors, List<Point<?>> points, String persistedGraphFile, String operationType) throws IOException {
        // Get sequence length from any point
        int sequenceLength = points.get(0).getSequence().getLength();
        
        // Convert points to nodes
        List<Node> nodesToProcess = points.stream()
            .map(p -> new Node(p.getSequence(), p.getId()))
            .toList();
                
        // stream and cast dynamicCamerini.getRoots() to List<ATreeNode>
        SerializableFullyDynamicArborescence dynamicAlgorithm = new SerializableFullyDynamicArborescence(persistedGraphFile);
        
        switch (operationType) {
            case ADD:
                // Add nodes to persisted file using batch operation (without edges, they're already computed)
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), new HashMap<>(), persistedGraphFile, sequenceLength);

                // Collect all edges incident to new nodes (both directions), then add in a single batch call
                List<Edge> allEdgesToAdd = new ArrayList<>();
                for (Node newNode : nodesToProcess) {
                    allEdgesToAdd.addAll(GraphMapper.getIncomingEdges(persistedGraphFile, newNode.getId()));
                    allEdgesToAdd.addAll(GraphMapper.getOutgoingEdges(persistedGraphFile, newNode.getId()));
                }
                System.out.println("Adding " + allEdgesToAdd.size() + " edges in batch...");
                dynamicAlgorithm.addEdges(allEdgesToAdd);
                break;
                
            case REMOVE:
                // Collect all edges incident to nodes being removed, then remove in a single batch call
                List<Edge> allEdgesToRemove = new ArrayList<>();
                for (Node nodeToRemove : nodesToProcess) {
                    allEdgesToRemove.addAll(GraphMapper.getIncomingEdges(persistedGraphFile, nodeToRemove.getId()));
                    allEdgesToRemove.addAll(GraphMapper.getOutgoingEdges(persistedGraphFile, nodeToRemove.getId()));
                }
                System.out.println("Removing " + allEdgesToRemove.size() + " edges in batch...");
                dynamicAlgorithm.removeEdges(allEdgesToRemove);
                
                // Remove nodes from persisted file and the incoming edge files for each of them
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, sequenceLength);
                // Remove outgoing edges from edge file for each removed node
                for (Node node: nodesToProcess) {
                    GraphMapper.removeOutgoingEdges(persistedGraphFile, node.getId());
                }
                break;
                
            case UPDATE:
                // Remove old edges from dynamic algorithm
                for (Node nodeToUpdate : nodesToProcess) {
                    // Get incoming edges
                    List<Edge> incomingEdges = GraphMapper.getIncomingEdges(persistedGraphFile, nodeToUpdate.getId());
                    for (Edge edge : incomingEdges) {
                        dynamicAlgorithm.removeEdge(edge);
                    }
                    
                    // Get outgoing edges
                    List<Edge> outgoingEdges = GraphMapper.getOutgoingEdges(persistedGraphFile, nodeToUpdate.getId());
                    for (Edge edge : outgoingEdges) {
                        dynamicAlgorithm.removeEdge(edge);
                    }
                }
                
                // Remove and re-add nodes to persisted file
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, sequenceLength);
                for (Node node: nodesToProcess) {
                    GraphMapper.removeOutgoingEdges(persistedGraphFile, node.getId());
                }
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), new HashMap<>(), persistedGraphFile, sequenceLength);
                
                // Atualizar com os pesos novos TODO
                throw new NotImplementedException();
                // break;
                
            default:
                throw new IllegalArgumentException("Unsupported operation type: " + operationType);
        }

        return dynamicAlgorithm.getCurrentArborescence();
    }

    private static void serializeNNAlgorithm(NearestNeighbourSearchAlgorithm<?> nnAlgorithm, String persistedGraphFile, String outputFile) throws IOException {
        String lshParamsFile;
        if (persistedGraphFile != null) {
            lshParamsFile = persistedGraphFile;
            // replace file extension with .lshparams
            int dotIndex = lshParamsFile.lastIndexOf('.');
            if (dotIndex != -1) {
                lshParamsFile = lshParamsFile.substring(0, dotIndex) + LSH_EXTENSION;
            } else {
                lshParamsFile = lshParamsFile + LSH_EXTENSION;
            }
        }
        else {
            lshParamsFile = outputFile;
            // append .lshparams extension
            lshParamsFile += LSH_EXTENSION;
        }
        optimalarborescence.nearestneighbour.LSH.saveLSH(
            (optimalarborescence.nearestneighbour.LSH<?>) nnAlgorithm, 
            lshParamsFile
        );
    }

    private static void runTestMode(String sequenceType, String inputSequenceFile, String outputFile, String persistedGraphFile, Integer batchSize, boolean onDemand) throws FileNotFoundException, IOException {
        System.out.println("Running in TEST mode: adding points in batches...");
        
        // Get user parameters
        String algorithmType = readAlgorithmType();
        int numNeighbors = -1;
        if (!algorithmType.equals(NEIGHBOR_JOINING)) {
            numNeighbors = approximatesGraph();
        }
        
        // Get batch size if not provided as command line argument
        if (batchSize == null) {
            batchSize = readBatchSize(algorithmType);
        }
        
        // Load all points from the input file
        List<Point<?>> allPoints = processSequences(sequenceType, inputSequenceFile);
        
        if (allPoints.size() < 2) {
            throw new IllegalArgumentException("Test mode requires at least 2 points in the input file.");
        }
        
        int sequenceLength = allPoints.get(0).getSequence().getLength();
        
        // Set up NN algorithm if using approximate graph
        NearestNeighbourSearchAlgorithm<?> nnAlgorithm = null;
        if (numNeighbors > 0) {
            nnAlgorithm = selectNNAlgorithm(sequenceType, sequenceLength, null);
            outputFile += "_approx_" + numNeighbors;
        } else {
            outputFile += "_exact";
        }
        
        // Add algorithm type to output file name
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                outputFile += "_static_camerini";
                break;
            case DYNAMIC_ALGORITHM:
                outputFile += "_dynamic_camerini";
                break;
            case NEIGHBOR_JOINING:
                outputFile += "_neighbor_joining";
                break;
        }
        
        outputFile += "_test_batch" + batchSize;
        String tempGraphFile = outputFile + "_temp";
        
        // Initialize with first 2 points
        System.out.println("Initializing with first 2 points...");
        List<Point<?>> initialPoints = allPoints.subList(0, 2);
        
        // Run initial iteration based on algorithm type
        List<Edge> phylogeny = null;
        List<Long> iterationTimes = new ArrayList<>(List.of(0L, 0L)); // [preProcessTime, inferenceTime]
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                phylogeny = runStaticTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, true, onDemand, iterationTimes);
                break;
            case DYNAMIC_ALGORITHM:
                phylogeny = runDynamicTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, true);
                break;
            case NEIGHBOR_JOINING:
                phylogeny = runNJTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, sequenceLength, true);
                break;
        }
        long cost = phylogeny.stream().mapToLong(Edge::getWeight).sum();
        logIterationDetails(iterationTimes.get(0), iterationTimes.get(1), "add", 2, 1, cost, outputFile);
        System.out.println("Initial phylogeny cost: " + cost);
        System.out.println("Initial iteration execution time: " + (iterationTimes.get(0) + iterationTimes.get(1)) + " ms");
        System.out.println("Initial graph created with 2 points. Phylogeny saved.");
        
        // Iteratively add remaining points in batches
        int i = 2;
        while (i < allPoints.size()) {
            // Determine batch size for this iteration
            int currentBatchSize = Math.min(batchSize, allPoints.size() - i);
            
            System.out.println("Adding batch of " + currentBatchSize + " point(s) (" + (i + 1) + " to " + (i + currentBatchSize) + " of " + allPoints.size() + ")...");
            
            List<Point<?>> batchPoints = allPoints.subList(i, i + currentBatchSize);
            
            switch (algorithmType) {
                case STATIC_ALGORITHM:
                    phylogeny = runStaticTestIteration(sequenceType, batchPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, false, onDemand, iterationTimes);
                    break;
                case DYNAMIC_ALGORITHM:
                    phylogeny = runDynamicTestIteration(sequenceType, batchPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, false);
                    break;
                case NEIGHBOR_JOINING:
                    phylogeny = runNJTestIteration(sequenceType, batchPoints, tempGraphFile, outputFile, sequenceLength, false);
                    break;
            }
            cost = phylogeny.stream().mapToLong(Edge::getWeight).sum();
            logIterationDetails(iterationTimes.get(0), iterationTimes.get(1), "add", currentBatchSize, i + 1, cost, outputFile);
            System.out.println("Updated phylogeny cost: " + cost);
            System.out.println("Iteration execution time: " + (iterationTimes.get(0) + iterationTimes.get(1)) + " ms");
            System.out.println("Batch added. Total points: " + (i + currentBatchSize));
            
            i += currentBatchSize;
            System.out.println("Batch added. Total points: " + i);
        }
        
        System.out.println("Test mode completed. Final phylogeny saved to: " + outputFile);
    }
    
    private static List<Edge> runStaticTestIteration(String sequenceType, List<Point<?>> points, String tempGraphFile, 
                                               String outputFile, int numNeighbors, 
                                               NearestNeighbourSearchAlgorithm<?> nnAlgorithm, 
                                               int sequenceLength, boolean isInitial, boolean onDemand,
                                               List<Long> iterationTimes) throws IOException {
        long startPreProcessTime = System.currentTimeMillis();
        if (isInitial) {
            // Create new graph with initial points using memory-mapped files
            generateExactGraphIncrementally(points, tempGraphFile, sequenceType);
        } else {
            // Add new nodes to existing memory-mapped graph
            List<Node> nodesToAdd = points.stream()
                .map(p -> new Node(p.getSequence(), p.getId()))
                .toList();
            
            addNodesIncrementallyToExactGraph(nodesToAdd, tempGraphFile, sequenceLength, sequenceType);
        }
        long endPreProcessTime = System.currentTimeMillis();
        iterationTimes.set(0, endPreProcessTime - startPreProcessTime);
        
        // Infer phylogeny using SerializableCameriniForest for lazy loading from memory-mapped files
        long startInferenceTime = System.currentTimeMillis();
        CameriniForest camerini = new SerializableCameriniForest(EDGE_COMPARATOR, tempGraphFile, onDemand, nnAlgorithm, numNeighbors,
                SYMMETRIC_DATA.contains(sequenceType) ? new HammingDistance() : new DirectionalHammingDistance(),
                SYMMETRIC_DATA.contains(sequenceType));
        List<Edge> phylogeny = camerini.inferPhylogeny(null).getEdges();
        long endInferenceTime = System.currentTimeMillis();
        iterationTimes.set(1, endInferenceTime - startInferenceTime);
        
        // Save phylogeny
        ensureDirectoryExists(outputFile);
        GraphMapper.saveArborescence(phylogeny, outputFile);
        
        // Save LSH parameters if applicable
        if (nnAlgorithm instanceof optimalarborescence.nearestneighbour.LSH<?>) {
            String lshParamsFile = tempGraphFile + LSH_EXTENSION;
            optimalarborescence.nearestneighbour.LSH.saveLSH(
                (optimalarborescence.nearestneighbour.LSH<?>) nnAlgorithm, 
                lshParamsFile
            );
        }

        return phylogeny;
    }
    
    private static List<Edge> runDynamicTestIteration(String sequenceType, List<Point<?>> points, String tempGraphFile,
                                                String outputFile, int numNeighbors,
                                                NearestNeighbourSearchAlgorithm<?> nnAlgorithm,
                                                int sequenceLength, boolean isInitial) throws IOException {
        SerializableFullyDynamicArborescence dynamicAlgorithm;
        
        if (isInitial) {
            // Create new graph with initial points using memory-mapped files
            generateExactGraphIncrementally(points, tempGraphFile, sequenceType);

            // Initialize dynamic algorithm from persisted graph
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(tempGraphFile);
            dynamicAlgorithm.inferPhylogeny(null);
        } else {
            // Load dynamic algorithm from persisted graph
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(tempGraphFile);
            
            // Add new nodes to memory-mapped file
            List<Node> nodesToAdd = points.stream()
                .map(p -> new Node(p.getSequence(), p.getId()))
                .toList();

            addNodesIncrementallyToExactGraph(nodesToAdd, tempGraphFile, sequenceLength, sequenceType);
            
            
            // Load node map for edge reconstruction
            // Map<Integer, Node> nodeMap = GraphMapper.loadNodeMap(tempGraphFile);
            
            // Collect all edges incident to new nodes (both directions), then add in a single batch call
            List<Edge> allEdgesToAdd = new ArrayList<>();
            for (Node newNode : nodesToAdd) {
                allEdgesToAdd.addAll(GraphMapper.getIncomingEdges(tempGraphFile, newNode.getId()));
                allEdgesToAdd.addAll(GraphMapper.getOutgoingEdges(tempGraphFile, newNode.getId()));
            }
            System.out.println("Adding " + allEdgesToAdd.size() + " edges in batch...");
            dynamicAlgorithm.addEdges(allEdgesToAdd);
        }
        
        // Get current phylogeny
        List<Edge> phylogeny = dynamicAlgorithm.getCurrentArborescence();
        
        // Save phylogeny
        ensureDirectoryExists(outputFile);
        GraphMapper.saveArborescence(phylogeny, outputFile);
        
        // Save LSH parameters if applicable
        if (nnAlgorithm instanceof optimalarborescence.nearestneighbour.LSH<?>) {
            String lshParamsFile = tempGraphFile + LSH_EXTENSION;
            optimalarborescence.nearestneighbour.LSH.saveLSH(
                (optimalarborescence.nearestneighbour.LSH<?>) nnAlgorithm, 
                lshParamsFile
            );
        }
        return phylogeny;
    }
    
    private static List<Edge> runNJTestIteration(String sequenceType, List<Point<?>> points, String tempGraphFile,
                                           String outputFile, int sequenceLength, boolean isInitial) throws IOException {
        List<Point<?>> allPoints;
        
        if (isInitial) {
            // Start with initial points
            allPoints = new ArrayList<>(points);
        } else {
            // Load existing graph and extract points
            Graph graph = GraphMapper.loadGraph(tempGraphFile);
            allPoints = new ArrayList<>(graph.getNodes().stream()
                .map(n -> n.getPoint())
                .toList());
            allPoints.addAll(points);
        }
        
        // Create distance matrix with all points
        DistanceMatrix distanceMatrix = new DistanceMatrix(new HammingDistance(), allPoints);
        
        // Infer phylogeny
        NeighbourJoining nj = new NeighbourJoining(distanceMatrix, distanceMatrix.getDistanceFunction());
        List<Edge> phylogeny = nj.inferPhylogeny(null).getEdges();
        
        // Save phylogeny
        ensureDirectoryExists(outputFile);
        GraphMapper.saveArborescence(phylogeny, outputFile);
        
        // Save graph representation (for loading points in next iteration)
        // Create a simple graph from the points to persist node information
        List<Node> nodes = allPoints.stream()
            .map(p -> new Node(p.getSequence(), p.getId()))
            .toList();
        Graph tempGraph = new Graph(new ArrayList<>()); // Empty edges, just nodes
        for (Node node : nodes) {
            tempGraph.addNode(node);
        }
        GraphMapper.saveGraph(tempGraph, sequenceLength, tempGraphFile);
        return phylogeny;
    }

    private static void logIterationDetails(long preProcess, long inferenceT, String operationType, int batchSize, int iterationNumber, long cost, String outputFile) throws IOException {
        long preProcessTime = preProcess;
        long inferenceTime = inferenceT;
        String logFile = outputFile + "_test_log.txt";
        ensureDirectoryExists(logFile);
        try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(fw);
             java.io.PrintWriter out = new java.io.PrintWriter(bw)) {
            out.print("Iteration " + iterationNumber);
            out.print(" | Operation: " + operationType);
            out.print(" | Points processed in this iteration: " + batchSize);
            out.print(" | Phylogeny Cost: " + cost);
            out.println(" | Pre-process time (ms): " + preProcessTime);
            out.println(" | Inference time (ms): " + inferenceTime);
        }
    }


    private static boolean isValidArborescence(Graph graph, Graph arborescence) {
        if (arborescence.getNumNodes() != graph.getNumNodes() || arborescence.getNumEdges() != graph.getNumNodes() - 1) {
            return false;
        }

        Map<Integer, Node> incidentNodes = new HashMap<>();
        for (Edge edge : arborescence.getEdges()) {
            if (!graph.getEdges().contains(edge)) {
                return false;
            }
            Node dest = edge.getDestination();
            if (incidentNodes.containsKey(dest.getId())) {
                return false; // More than one incoming edge to the same node
            }
            incidentNodes.put(dest.getId(), dest);
        }

        List<Node> allNodes = graph.getNodes();
        for (Node node : incidentNodes.values()) {
            allNodes.remove(node);
        }
        if (allNodes.size() != 1) {
            return false; // More than one root or missing nodes
        }
        Node root = allNodes.get(0);

        if (!BFS(arborescence, root)) { // It is not a spanning tree
            return false; // Not all nodes are reachable from the root
        }

        return true;
    }

    private static boolean BFS(Graph graph, Node start) {
        List<Node> visited = new ArrayList<>();
        List<Node> queue = new ArrayList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.remove(0);
            visited.add(current);

            for (Edge edge : graph.getEdges()) {
                if (edge.getSource().equals(current)) {
                    Node neighbor = edge.getDestination();
                    if (!visited.contains(neighbor) && !queue.contains(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return visited.size() == graph.getNumNodes();
    }
}