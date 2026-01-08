package optimalarborescence;

import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.DirectedGraph;
import optimalarborescence.graph.PhylogeneticData;
import optimalarborescence.graph.DistanceMatrix;
import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.distance.HammingDistance;
import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.sequences.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.SerializableCameriniForest;
import optimalarborescence.inference.dynamic.*;
import optimalarborescence.inference.NeighbourJoining;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.memorymapper.EdgeListMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;


import javax.management.RuntimeErrorException;


public class Main {

    private static final String MLST = "mlst";
    private static final String ALLELIC = "allelic";
    private static final List<String> SEQUENCE_TYPE = List.of(MLST, ALLELIC);
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
    private static final List<String> OPERATION_TYPE = List.of(ADD, REMOVE, UPDATE, TEST);
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());
    private static final int BATCH_SIZE = 1000;  // Number of nodes to process in each batch for incremental graph building
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        if (args.length < 4 || args.length > 6) {
            System.err.println("Wrong Invocation: java -jar OptimalArborescence.jar <sequence_type> <input_sequence_file> <output_file> <operation_type> [<persisted_graph_file>] [<batch_size>]\nWhere:\n\t- <sequence_type> is either 'mlst' or 'allelic'\n\t- <input_sequence_file> is the path to the input sequence file\n\t- <output_file> is the path to the output file\n\t- <operation_type> is either 'add', 'remove', 'update', or 'test'\n\t- <persisted_graph_file> is an optional path to a persisted graph file to continue from a previous run\n\t- <batch_size> is an optional positive integer for test mode only, specifying how many points to add in each batch (only for static and neighborJoining algorithms)");
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
        
        if (args.length >= 5) {
            // Determine if arg[4] is persisted graph file or batch size for test mode
            if (operationType.equals(TEST)) {
                // For test mode, arg[4] could be either persisted file or batch size
                try {
                    batchSize = Integer.parseInt(args[4]);
                    if (batchSize <= 0) {
                        throw new IllegalArgumentException("Batch size must be a positive integer.");
                    }
                    // If there's a 6th argument, it's invalid for test mode
                    if (args.length == 6) {
                        throw new IllegalArgumentException("Test mode accepts only batch_size parameter, not persisted_graph_file.");
                    }
                } catch (NumberFormatException e) {
                    // If arg[4] is not a number, treat it as persisted graph file (for backward compatibility)
                    persistedGraphFile = args[4];
                    if (!fileExists(persistedGraphFile)) {
                        throw new FileNotFoundException("Persisted graph file does not exist: " + persistedGraphFile);
                    }
                    // Check if arg[5] exists and is batch size
                    if (args.length == 6) {
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
                if (args.length == 6) {
                    throw new IllegalArgumentException("Batch size parameter is only valid for test mode.");
                }
            }
        }
        validateParameters(sequenceType, inputSequenceFile, operationType, persistedGraphFile);

        // Test mode: iteratively add points in batches
        if (operationType.equals(TEST)) {
            runTestMode(sequenceType, inputSequenceFile, outputFile, persistedGraphFile, batchSize);
            return;
        }

        String algorithmType = readAlgorithmType();
        int numNeighbors = -1;
        if (!algorithmType.equals(NEIGHBOR_JOINING)) {
            // NJ always uses the full graph
            numNeighbors = approximatesGraph();
        }
        List<Point<?>> newPoints = processSequences(sequenceType, inputSequenceFile);
        NearestNeighbourSearchAlgorithm<?> nnAlgorithm = null;
        if (numNeighbors > 0) {
            sequenceLength = newPoints.get(0).getSequence().getLength();
            nnAlgorithm = selectNNAlgorithm(sequenceType, sequenceLength, persistedGraphFile);
            outputFile += "_approx_" + numNeighbors;
        }
        else { outputFile += "_exact"; }

        long startTime = System.currentTimeMillis();
        persistedGraphFile = initializeGraph(sequenceType, inputSequenceFile, numNeighbors, persistedGraphFile, nnAlgorithm, newPoints, algorithmType, operationType, outputFile);

        
        List<Edge> phylogeny = null;
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                outputFile += "_static_camerini";
                phylogeny = runStaticCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, operationType);
                break;
            case DYNAMIC_ALGORITHM:
                outputFile += "_dynamic_camerini";
                phylogeny = runDynamicCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, operationType);
                break;
            case NEIGHBOR_JOINING:
                outputFile += "_neighbor_joining";
                DistanceMatrix distanceMatrix = (DistanceMatrix) g;
                NeighbourJoining nj = new NeighbourJoining(distanceMatrix, distanceMatrix.getDistanceFunction());
                phylogeny = nj.inferPhylogeny(null).getEdges();

                // TODO - save the graph

                break;
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
        System.out.println("Execution time: " + (endTime - startTime) + " ms");

        // Save execution time and arborescence cost to a log file
        long cost = phylogeny.stream().mapToLong(Edge::getWeight).sum();
        System.out.println("Arborescence cost: " + cost);
        File logFile = new File(outputFile + "_log.txt");
        try (java.io.FileWriter writer = new java.io.FileWriter(logFile)) {
            writer.write("Execution time (ms): " + (endTime - startTime) + "\n");
            writer.write("Arborescence cost: " + cost + "\n");
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
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
    
    private static int readBatchSize(String algorithmType) {
        // Batch size only applies to static and neighbor joining algorithms
        if (algorithmType.equals(DYNAMIC_ALGORITHM)) {
            return 1; // Dynamic algorithm always processes one point at a time
        }
        
        System.out.println("Enter the batch size (positive integer) for adding points in test mode, or " + EXIT + " to quit:");
        System.out.println("(Batch size determines how many points are added before inferring the phylogeny)");
        
        while (true) {
            String response = System.console().readLine().trim().toLowerCase();
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
            case MLST:
                List<Integer[]> rawMLSTData = Parser.readCSVLines(inputFile);
                List<SequenceTypingData> mlstData = Parser.processedCSVToTypingData(rawMLSTData);
                for (int i = 0; i < mlstData.size(); i++) {
                    int identifier = Parser.getSTFromProcessedCSVLine(rawMLSTData.get(i));
                    points.add(new Point<>(identifier, mlstData.get(i)));
                }
                break;
            case ALLELIC:
                // TODO - implementar
                throw new NotImplementedException("Handling of allelic sequences has not been implemented yet.");
                // List<AllelicProfile> allelicProfiles = Parser.fastaToAllelicProfiles(inputFile);

                // // TODO - estes IDs estão inválidos para os perfis alélicos
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
                                        String algorithmType, String operationType, String outputFile) throws IOException {

        if (persistedGraphFile != null) {
            // Graph already exists. Add the new edges/points to it if needed.
            if (operationType.equals(ADD)) {
                // Load existing graph
                if (numNeighbors > 0) {
                    // Approximate graph
                    List<Node> newNodes = points.stream()
                                                .map(p -> new Node(p.getSequence(), p.getId()))
                                                .toList();
                    addNodesIncrementallyToApproximateGraph(newNodes, outputFile, numNeighbors, nnAlgorithm, numNeighbors);
                }
                else {
                    List<Node> newNodes = points.stream()
                                                .map(p -> new Node(p.getSequence(), p.getId()))
                                                .toList();
                    addNodesIncrementallyToExactGraph(newNodes, outputFile, points.get(0).getSequence().getLength());
                }
            }
            return persistedGraphFile;
        }

        if (algorithmType.equals(NEIGHBOR_JOINING)) {
            // Create full DistanceMatrix from points
            // return new DistanceMatrix(new HammingDistance(), points);
            throw new NotImplementedException("Building DistanceMatrix from scratch is not implemented yet.");
        }
        else { // exact graph
            // Use incremental construction to avoid memory overflow
            String tempFile = outputFile + "_building";
            if (numNeighbors > 0) {
                generateApproximateGraphIncrementally(points, tempFile, nnAlgorithm, numNeighbors);
            }
            else {
                generateExactGraphIncrementally(points, tempFile);
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

        while (!validNNAlgorithm(response)) {
            response = System.console().readLine().trim().toLowerCase();
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
                    return buildLSH(sequenceLength);
                case EXIT:
                    System.out.println("Exiting the program.");
                    System.exit(0);
                default:
                    System.out.println("Invalid response. Please enter '" + LSH + "'. Enter "+ EXIT + " to quit.");
            }
        }

        return null;
    }

    private static LSH<?> buildLSH(int sequenceLength) {
        System.out.println("Enter a sequence of positive integers for the amount of compared sequence positions, the number of hash tables, and the maximum distance between two points:\n\tFormat: <num_compared_positions> <num_hash_tables> <max_distance>\n\tExample: 10 5 3\n\tEnter " + EXIT + " to quit.\n");
        String response = "";

        while (true) {
            response = System.console().readLine().trim().toLowerCase();
            if (response.equals(EXIT)) {
                System.out.println("Exiting the program.");
                System.exit(0);
            }
            String[] parts = response.split(" ");
            if (parts.length != 3) {
                System.out.println("Invalid response. Please enter three positive integers separated by spaces. Enter "+ EXIT + " to quit.");
                continue;
            }
            try {
                int numComparedPositions = Integer.parseInt(parts[0]);
                int numHashTables = Integer.parseInt(parts[1]);
                int maxDistance = Integer.parseInt(parts[2]);
                if (numComparedPositions > 0 && numHashTables > 0 && maxDistance > 0) {
                    return new LSH<>(numComparedPositions, numHashTables, 0, sequenceLength - 1, new HammingDistance(), maxDistance);
                } else {
                    System.out.println("All values must be positive integers. Please try again. Enter "+ EXIT + " to quit.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid response. Please enter three positive integers separated by spaces. Enter "+ EXIT + " to quit.");
            }
        }
    }



    /**
     * Generates an exact graph incrementally to avoid memory overflow.
     * Creates initial graph with 2 nodes, saves to memory-mapped file,
     * then adds remaining nodes in batches, computing and persisting edges incrementally.
     * 
     * Only stores upper triangle of distance matrix (edges from existing nodes to new nodes)
     * to avoid redundant storage since distance(A,B) = distance(B,A).
     * 
     * Does NOT maintain an in-memory Graph - writes directly to memory-mapped files.
     * 
     * @param points All points to include in the graph
     * @param outputFile Path for the memory-mapped graph file
     */
    private static void generateExactGraphIncrementally(List<Point<?>> points, String outputFile) throws IOException {
        if (points.size() < 2) {
            throw new IllegalArgumentException("At least 2 points are required to build a graph.");
        }
        
        int sequenceLength = points.get(0).getSequence().getLength();
        DistanceFunction distanceFunction = new HammingDistance();
        
        // Step 1: Initialize with first 2 nodes
        // System.out.println("Initializing graph with first 2 nodes...");
        List<Node> initialNodes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            initialNodes.add(new Node(points.get(i).getSequence(), points.get(i).getId()));
        }
        
        // Create minimal graph with just 2 nodes and 1 edge (upper triangle)
        Graph graph = new Graph(new ArrayList<>());
        graph.addNode(initialNodes.get(0));
        graph.addNode(initialNodes.get(1));
        
        // Only create edge from node 0 -> node 1 (upper triangle)
        int distance = (int) distanceFunction.calculate(
            initialNodes.get(0).getPoint().getSequence(),
            initialNodes.get(1).getPoint().getSequence()
        );
        graph.addEdge(new Edge(initialNodes.get(0), initialNodes.get(1), distance));
        
        // Save initial graph to memory-mapped file
        ensureDirectoryExists(outputFile);
        GraphMapper.saveGraph(graph, sequenceLength, outputFile);
        // System.out.println("Initial graph saved. Adding remaining nodes in batches...");
        
        // Keep a lightweight cache of ALL nodes (just for distance computation)
        // This avoids expensive file reloads for every batch
        List<Node> allNodes = new ArrayList<>(initialNodes);
        
        // Step 2: Add remaining nodes in batches
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
            
            // Prepare edges for all new nodes (UPPER TRIANGLE ONLY)
            // Use cached allNodes instead of reloading from file
            Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
            
            System.out.println("Computing distances for " + batchSize + " new nodes against " + allNodes.size() + " existing nodes...");
            long distStart = System.currentTimeMillis();
            
            for (Node newNode : batchNodes) {
                List<Edge> incomingEdges = new ArrayList<>();
                
                // Create edges FROM existing nodes TO new node (upper triangle)
                for (Node existingNode : allNodes) {
                    int dist = (int) distanceFunction.calculate(
                        existingNode.getPoint().getSequence(),
                        newNode.getPoint().getSequence()
                    );
                    incomingEdges.add(new Edge(existingNode, newNode, dist));
                }
                
                nodeEdgesMap.put(newNode, incomingEdges);
            }
            
            long distEnd = System.currentTimeMillis();
            System.out.println("Distance computation: " + (distEnd - distStart) + " ms");
            
            // Add intra-batch edges (upper triangle within batch)
            System.out.println("Computing intra-batch edges...");
            for (int j = 0; j < batchNodes.size(); j++) {
                Node nodeJ = batchNodes.get(j);
                List<Edge> nodeJEdges = nodeEdgesMap.get(nodeJ);
                
                // Add edges from earlier nodes in batch to this node
                for (int k = 0; k < j; k++) {
                    Node nodeK = batchNodes.get(k);
                    int dist = (int) distanceFunction.calculate(
                        nodeK.getPoint().getSequence(),
                        nodeJ.getPoint().getSequence()
                    );
                    // nodeK -> nodeJ (upper triangle: earlier node to later node)
                    nodeJEdges.add(new Edge(nodeK, nodeJ, dist));
                }
            }
            
            // Add all nodes and edges in one batch operation
            System.out.println("Writing to memory-mapped file...");
            long writeStart = System.currentTimeMillis();
            GraphMapper.addNodesBatch(batchNodes, nodeEdgesMap, outputFile, sequenceLength);
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
                                                  int numNeighbors) throws IOException {
        throw new NotImplementedException("Incremental construction of approximate graphs is not implemented yet.");
    }

    /**
     * Adds nodes incrementally to an existing graph, computing and persisting edges in batches
     * to avoid memory overflow. Only stores upper triangle edges (from existing to new nodes).
     * 
     * Uses a memory cache of nodes to avoid expensive file reloads.
     * 
     * @param nodesToAdd List of nodes to add
     * @param outputFile Path to the memory-mapped graph file
     * @param sequenceLength Length of sequences (for serialization)
     */
    private static void addNodesIncrementallyToExactGraph(List<Node> nodesToAdd, 
                                                          String outputFile, int sequenceLength) throws IOException {
        if (nodesToAdd.isEmpty()) {
            return;
        }
        
        DistanceFunction distanceFunction = new HammingDistance();
        
        System.out.println("Adding " + nodesToAdd.size() + " nodes incrementally...");
        
        // Load existing nodes from file ONCE
        Map<Integer, Node> existingNodeMap = GraphMapper.loadNodeMap(outputFile);
        List<Node> existingNodes = new ArrayList<>(existingNodeMap.values());
        
        System.out.println("Loaded " + existingNodes.size() + " existing nodes from file.");
        
        // Prepare edges for all new nodes (upper triangle only)
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        
        long distStart = System.currentTimeMillis();
        for (int i = 0; i < nodesToAdd.size(); i++) {
            Node newNode = nodesToAdd.get(i);
            List<Edge> incomingEdges = new ArrayList<>();
            
            // Create edges FROM all existing nodes TO new node
            for (Node existingNode : existingNodes) {
                int dist = (int) distanceFunction.calculate(
                    existingNode.getPoint().getSequence(),
                    newNode.getPoint().getSequence()
                );
                incomingEdges.add(new Edge(existingNode, newNode, dist));
            }
            
            // Create edges FROM previously added nodes in this batch TO new node
            for (int j = 0; j < i; j++) {
                Node previousNode = nodesToAdd.get(j);
                int dist = (int) distanceFunction.calculate(
                    previousNode.getPoint().getSequence(),
                    newNode.getPoint().getSequence()
                );
                incomingEdges.add(new Edge(previousNode, newNode, dist));
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
        GraphMapper.addNodesBatch(nodesToAdd, nodeEdgesMap, outputFile, sequenceLength);
        long writeEnd = System.currentTimeMillis();
        System.out.println("File write: " + (writeEnd - writeStart) + " ms");
        
        System.out.println("All " + nodesToAdd.size() + " nodes added successfully.");
    }

    private static void addNodesIncrementallyToApproximateGraph(List<Node> nodesToAdd, 
                                                          String outputFile, int sequenceLength,
                                                          NearestNeighbourSearchAlgorithm<?> nnAlgorithm,
                                                          int numNeighbors) throws IOException {
        if (nodesToAdd.isEmpty()) return;
        if (nnAlgorithm == null || numNeighbors <= 0) {
            throw new IllegalArgumentException("Nearest Neighbour Search Algorithm and number of neighbors must be provided for approximate graph.");
        }


        DistanceFunction distanceFunction = new HammingDistance();
        System.out.println("Adding " + nodesToAdd.size() + " nodes incrementally to approximate graph...");

        // Load existing nodes from file
        Map<Integer, Node> existingNodeMap = GraphMapper.loadNodeMap(outputFile);
        List<Node> existingNodes = new ArrayList<>(existingNodeMap.values());
        System.out.println("Loaded " + existingNodes.size() + " existing nodes from file.");

        // Prepare edges for all new nodes
        Map<Node, List<Edge>> nodeEdgesMap = new HashMap<>();
        long distStart = System.currentTimeMillis();
        for (int i = 0; i < nodesToAdd.size(); i++) {
            Node newNode = nodesToAdd.get(i);
            List<Edge> incomingEdges = new ArrayList<>();
            // Find nearest neighbors using the NN algorithm
            @SuppressWarnings("unchecked")
            List<Point<Object>> neighbors = ((NearestNeighbourSearchAlgorithm<Object>) nnAlgorithm).neighbourSearch((Point<Object>) newNode.getPoint(), numNeighbors);
            // Create edges FROM neighbors TO new node
            for (Point<?> neighborPoint : neighbors) {
                Node neighborNode = existingNodeMap.get(neighborPoint.getId());
                if (neighborNode != null) {
                    int dist = (int) distanceFunction.calculate(
                        neighborNode.getPoint().getSequence(),
                        newNode.getPoint().getSequence()
                    );
                    incomingEdges.add(new Edge(neighborNode, newNode, dist));
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
        GraphMapper.addNodesBatch(nodesToAdd, nodeEdgesMap, outputFile, sequenceLength);
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

    private static List<Edge> runStaticCameriniAlgorithm(String sequenceType, String inputFile, String outputFile, int numNeighbors, List<Point<?>> points, String persistedGraphFile, String operationType) throws IOException {

        List<Node> nodesToProcess = null;
        nodesToProcess = points.stream().map(p -> new Node(p.getSequence(), p.getId())).toList();
        switch (operationType) {
            case ADD:
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), persistedGraphFile, points.get(0).getSequence().getLength());
                break;
            case REMOVE:
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, points.get(0).getSequence().getLength());
                break;
            case UPDATE:
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, points.get(0).getSequence().getLength());
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), persistedGraphFile, points.get(0).getSequence().getLength());
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation type: " + operationType);
        }
        
        CameriniForest camerini = new SerializableCameriniForest(EDGE_COMPARATOR, persistedGraphFile);
        
        System.out.println("Inferring phylogeny using Static Camerini Algorithm...");
        return camerini.inferPhylogeny(null).getEdges();
    }

    private static List<Edge> runDynamicCameriniAlgorithm(String sequenceType, String inputFile, String outputFile, int numNeighbors, List<Point<?>> points, String persistedGraphFile, String operationType) throws IOException {
        // Get sequence length from any point
        int sequenceLength = points.get(0).getSequence().getLength();

        // TODO - estamos a adicionar/remover nós desnecessários
        // TODO - em vez de carregar a linked list inteira
        // TODO - devia escolher apenas as arestas necessárias
        
        // Convert points to nodes
        List<Node> nodesToProcess = points.stream()
            .map(p -> new Node(p.getSequence(), p.getId()))
            .toList();
                
        // stream and cast dynamicCamerini.getRoots() to List<ATreeNode>
        SerializableFullyDynamicArborescence dynamicAlgorithm = new SerializableFullyDynamicArborescence(persistedGraphFile);
        
        // Load node map for edge reconstruction
        Map<Integer, Node> nodeMap = GraphMapper.loadNodeMap(persistedGraphFile);
        
        switch (operationType) {
            case ADD:
                // Step 1: Add nodes to persisted file using batch operation (without edges, they're already computed)
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), persistedGraphFile, sequenceLength);
                
                // Step 2: Reload node map to include newly added nodes
                nodeMap = GraphMapper.loadNodeMap(persistedGraphFile);
                
                // Step 3: For each new node, get its incoming edges and add them to the dynamic algorithm
                for (Node newNode : nodesToProcess) {
                    List<Edge> incomingEdges = GraphMapper.getIncomingEdges(persistedGraphFile, newNode.getId(), nodeMap);
                    
                    // Add each edge to the dynamic algorithm
                    for (Edge edge : incomingEdges) {
                        dynamicAlgorithm.addEdge(edge);
                    }
                }
                break;
                
            case REMOVE:
                // Step 1: Get edges that will be removed (edges incident to nodes being removed)
                String edgeFile = persistedGraphFile + "_edges.dat";
                
                for (Node nodeToRemove : nodesToProcess) {
                    // Get incoming edges
                    List<Edge> incomingEdges = GraphMapper.getIncomingEdges(persistedGraphFile, nodeToRemove.getId(), nodeMap);
                    for (Edge edge : incomingEdges) {
                        dynamicAlgorithm.removeEdge(edge);
                    }
                    
                    // Get outgoing edges using EdgeListMapper
                    List<Long> outgoingOffsets = EdgeListMapper.getOutgoingEdgeOffsets(edgeFile, nodeToRemove.getId());
                    for (Long offset : outgoingOffsets) {
                        List<Edge> edgesAtOffset = EdgeListMapper.loadLinkedList(edgeFile, offset);
                        // Filter to get only edges from this source
                        for (Edge edge : edgesAtOffset) {
                            if (edge.getSource().getId() == nodeToRemove.getId()) {
                                dynamicAlgorithm.removeEdge(edge);
                            }
                        }
                    }
                }
                
                // Step 2: Remove nodes from persisted file
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, sequenceLength);
                break;
                
            case UPDATE:
                // Step 1: Remove old edges from dynamic algorithm
                edgeFile = persistedGraphFile + "_edges.dat";
                
                for (Node nodeToUpdate : nodesToProcess) {
                    // Get incoming edges
                    List<Edge> incomingEdges = GraphMapper.getIncomingEdges(persistedGraphFile, nodeToUpdate.getId(), nodeMap);
                    for (Edge edge : incomingEdges) {
                        dynamicAlgorithm.removeEdge(edge);
                    }
                    
                    // Get outgoing edges using EdgeListMapper
                    List<Long> outgoingOffsets = EdgeListMapper.getOutgoingEdgeOffsets(edgeFile, nodeToUpdate.getId());
                    for (Long offset : outgoingOffsets) {
                        List<Edge> edgesAtOffset = EdgeListMapper.loadLinkedList(edgeFile, offset);
                        // Filter to get only edges from this source
                        for (Edge edge : edgesAtOffset) {
                            if (edge.getSource().getId() == nodeToUpdate.getId()) {
                                dynamicAlgorithm.removeEdge(edge);
                            }
                        }
                    }
                }
                
                // Step 2: Remove and re-add nodes to persisted file
                GraphMapper.removeNodesBatch(nodesToProcess, persistedGraphFile, sequenceLength);
                GraphMapper.addNodesBatch(nodesToProcess, new HashMap<>(), persistedGraphFile, sequenceLength);
                
                // Step 3: Reload node map and add new edges to dynamic algorithm
                nodeMap = GraphMapper.loadNodeMap(persistedGraphFile);
                for (Node updatedNode : nodesToProcess) {
                    List<Edge> incomingEdges = GraphMapper.getIncomingEdges(persistedGraphFile, updatedNode.getId(), nodeMap);
                    
                    // Add each edge to the dynamic algorithm
                    for (Edge edge : incomingEdges) {
                        dynamicAlgorithm.addEdge(edge);
                    }
                }
                break;
                
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

    private static void runTestMode(String sequenceType, String inputSequenceFile, String outputFile, String persistedGraphFile, Integer batchSize) throws FileNotFoundException, IOException {
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
        } else if (algorithmType.equals(DYNAMIC_ALGORITHM) && batchSize != 1) {
            System.out.println("Warning: Batch size is not applicable for dynamic algorithm. Using batch size of 1.");
            batchSize = 1;
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
        long startTime = System.currentTimeMillis();
        List<Edge> phylogeny = null;
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                phylogeny = runStaticTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, true);
                break;
            case DYNAMIC_ALGORITHM:
                phylogeny = runDynamicTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, true);
                break;
            case NEIGHBOR_JOINING:
                phylogeny = runNJTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, sequenceLength, true);
                break;
        }
        long endTime = System.currentTimeMillis();
        long cost = phylogeny.stream().mapToLong(Edge::getWeight).sum();
        logIterationDetails(startTime, endTime, "add", 2, 1, cost, outputFile);
        System.out.println("Initial phylogeny cost: " + cost);
        System.out.println("Initial iteration execution time: " + (endTime - startTime) + " ms");
        System.out.println("Initial graph created with 2 points. Phylogeny saved.");
        
        // Iteratively add remaining points in batches
        int i = 2;
        while (i < allPoints.size()) {
            // Determine batch size for this iteration
            int currentBatchSize = Math.min(batchSize, allPoints.size() - i);
            
            System.out.println("Adding batch of " + currentBatchSize + " point(s) (" + (i + 1) + " to " + (i + currentBatchSize) + " of " + allPoints.size() + ")...");
            
            List<Point<?>> batchPoints = allPoints.subList(i, i + currentBatchSize);
            
            startTime = System.currentTimeMillis();
            switch (algorithmType) {
                case STATIC_ALGORITHM:
                    phylogeny = runStaticTestIteration(sequenceType, batchPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, false);
                    break;
                case DYNAMIC_ALGORITHM:
                    // Dynamic algorithm processes points one by one
                    for (Point<?> point : batchPoints) {
                        phylogeny = runDynamicTestIteration(sequenceType, List.of(point), tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, false);
                    }
                    break;
                case NEIGHBOR_JOINING:
                    phylogeny = runNJTestIteration(sequenceType, batchPoints, tempGraphFile, outputFile, sequenceLength, false);
                    break;
            }
            endTime = System.currentTimeMillis();
            cost = phylogeny.stream().mapToLong(Edge::getWeight).sum();
            logIterationDetails(startTime, endTime, "add", currentBatchSize, i + 1, cost, outputFile);
            System.out.println("Updated phylogeny cost: " + cost);
            System.out.println("Iteration execution time: " + (endTime - startTime) + " ms");
            System.out.println("Batch added. Total points: " + (i + currentBatchSize));
            
            i += currentBatchSize;
            System.out.println("Batch added. Total points: " + i);
        }
        
        System.out.println("Test mode completed. Final phylogeny saved to: " + outputFile);
    }
    
    private static List<Edge> runStaticTestIteration(String sequenceType, List<Point<?>> points, String tempGraphFile, 
                                               String outputFile, int numNeighbors, 
                                               NearestNeighbourSearchAlgorithm<?> nnAlgorithm, 
                                               int sequenceLength, boolean isInitial) throws IOException {
        
        if (isInitial) {
            // Create new graph with initial points using memory-mapped files
            if (numNeighbors > 0) {
                // Approximate graph
                generateApproximateGraphIncrementally(points, tempGraphFile, nnAlgorithm, numNeighbors);
            } else {
                // Exact graph
                generateExactGraphIncrementally(points, tempGraphFile);
            }
        } else {
            // Add new nodes to existing memory-mapped graph
            List<Node> nodesToAdd = points.stream()
                .map(p -> new Node(p.getSequence(), p.getId()))
                .toList();
            
            if (numNeighbors > 0) {
                // Approximate graph
                addNodesIncrementallyToApproximateGraph(nodesToAdd, tempGraphFile, sequenceLength, nnAlgorithm, numNeighbors);
            } else {
                // Exact graph
                addNodesIncrementallyToExactGraph(nodesToAdd, tempGraphFile, sequenceLength);
            }
        }
        
        // Infer phylogeny using SerializableCameriniForest for lazy loading from memory-mapped files
        CameriniForest camerini = new SerializableCameriniForest(EDGE_COMPARATOR, tempGraphFile);
        System.out.println("Inferring phylogeny using Static Camerini Algorithm...");
        List<Edge> phylogeny = camerini.inferPhylogeny(null).getEdges();
        
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
            if (numNeighbors > 0) {
                // Approximate graph
                generateApproximateGraphIncrementally(points, tempGraphFile, nnAlgorithm, numNeighbors);
            } else {
                // Exact graph
                generateExactGraphIncrementally(points, tempGraphFile);
            }
            
            // Initialize dynamic algorithm from persisted graph
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(tempGraphFile);
            System.out.println("Inferring phylogeny using Dynamic Camerini Algorithm...");
            dynamicAlgorithm.inferPhylogeny(null);
        } else {
            // Load dynamic algorithm from persisted graph
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(tempGraphFile);
            
            // Add new nodes to memory-mapped file
            List<Node> nodesToAdd = points.stream()
                .map(p -> new Node(p.getSequence(), p.getId()))
                .toList();
            
            if (numNeighbors > 0) {
                // Approximate graph
                addNodesIncrementallyToApproximateGraph(nodesToAdd, tempGraphFile, sequenceLength, nnAlgorithm, numNeighbors);
            } else {
                // Exact graph
                addNodesIncrementallyToExactGraph(nodesToAdd, tempGraphFile, sequenceLength);
            }
            
            // Load node map for edge reconstruction
            Map<Integer, Node> nodeMap = GraphMapper.loadNodeMap(tempGraphFile);
            
            // Load edges for new nodes and add to dynamic algorithm
            for (Node newNode : nodesToAdd) {
                List<Edge> incomingEdges = GraphMapper.getIncomingEdges(tempGraphFile, newNode.getId(), nodeMap);
                
                // Add each edge to the dynamic algorithm
                for (Edge edge : incomingEdges) {
                    dynamicAlgorithm.addEdge(edge);
                }
            }
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
        System.out.println("Inferring phylogeny using Neighbor Joining...");
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

    private static void logIterationDetails(long startTime, long endTime, String operationType, int batchSize, int iterationNumber, long cost, String outputFile) throws IOException {
        long executionTime = endTime - startTime;
        String logFile = outputFile + "_test_log.txt";
        ensureDirectoryExists(logFile);
        try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(fw);
             java.io.PrintWriter out = new java.io.PrintWriter(bw)) {
            out.print("Iteration " + iterationNumber);
            out.print(" | Operation: " + operationType);
            out.print(" | Points processed in this iteration: " + batchSize);
            out.print(" | Phylogeny Cost: " + cost);
            out.println(" | Execution time (ms): " + executionTime);
        }
    }
}
