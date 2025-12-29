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
import optimalarborescence.inference.dynamic.*;
import optimalarborescence.inference.NeighbourJoining;
import optimalarborescence.memorymapper.GraphMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
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
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        if (args.length < 4 || args.length > 6) {
            System.err.println("Wrong Invocation: java -jar OptimalArborescence.jar <sequence_type> <input_sequence_file> <output_file> <operation_type> [<persisted_graph_file>] [<batch_size>]\nWhere:\n\t- <sequence_type> is either 'mlst' or 'allelic'\n\t- <input_sequence_file> is the path to the input sequence file\n\t- <output_file> is the path to the output file\n\t- <operation_type> is either 'add', 'remove', 'update', or 'test'\n\t- <persisted_graph_file> is an optional path to a persisted graph file to continue from a previous run\n\t- <batch_size> is an optional positive integer for test mode only, specifying how many points to add in each batch (only for static and neighborJoining algorithms)");
            System.exit(1);
        }
        String sequenceType = args[0];
        sequenceType = sequenceType.toLowerCase();
        String inputSequenceFile = args[1]; String outputFile = args[2];
        String operationType = args[3].toLowerCase();
        validateParameters(sequenceType, inputSequenceFile, operationType);
        
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
        g = initializeGraph(sequenceType, inputSequenceFile, numNeighbors, persistedGraphFile, nnAlgorithm, newPoints, algorithmType, operationType);
        
        Graph graph;
        List<Edge> phylogeny = null;
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                outputFile += "_static_camerini";
                graph = (Graph) g;
                phylogeny = runStaticCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, graph, operationType);
                break;
            case DYNAMIC_ALGORITHM:
                outputFile += "_dynamic_camerini";
                graph = (Graph) g;
                phylogeny = runDynamicCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, graph, operationType);
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

    private static void validateParameters(String sequenceType, String inputFile, String operationType) throws FileNotFoundException {
        if (!validSequenceType(sequenceType)) {
            throw new IllegalArgumentException("Invalid sequence type: " + sequenceType + ". Valid types are: " + SEQUENCE_TYPE);
        }
        if (!fileExists(inputFile)) {
            throw new FileNotFoundException("Input file does not exist: " + inputFile);
        }
        if (!OPERATION_TYPE.contains(operationType)) {
            throw new IllegalArgumentException("Invalid operation type: " + operationType + ". Valid types are: " + OPERATION_TYPE);
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

    private static PhylogeneticData initializeGraph(String sequenceType, String inputFile, 
                                        int numNeighbors, String persistedGraphFile,
                                        NearestNeighbourSearchAlgorithm<?> nnAlgorithm, List<Point<?>> points,
                                        String algorithmType, String operationType) throws IOException {

        if (persistedGraphFile != null) {
            if (numNeighbors > 0) {
                return GraphMapper.loadDirectedGraph(persistedGraphFile, nnAlgorithm, numNeighbors);
            } 
            else {
                Graph g = GraphMapper.loadGraph(persistedGraphFile);
                if (algorithmType.equals(NEIGHBOR_JOINING)) {
                    // Convert to DistanceMatrix
                    List<Point<?>> graphPoints = new ArrayList<>(g.getNodes().stream()
                        .map(n -> n.getPoint())
                        .toList());
                    switch (operationType) { // TODO - pensar em como optimizar/re-aproveitar as distâncias já calculadas
                        case ADD:
                            graphPoints.addAll(points);
                            break;
                        case REMOVE:
                            graphPoints.removeAll(points);
                            break;
                        case UPDATE:
                            graphPoints.removeAll(points);
                            graphPoints.addAll(points);
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported operation type: " + operationType);
                    }

                    return new DistanceMatrix(new HammingDistance(), graphPoints);
                }
                return g;
            }
        }

        if (algorithmType.equals(NEIGHBOR_JOINING)) {
            // Create full DistanceMatrix from points
            return new DistanceMatrix(new HammingDistance(), points);
        }
        else if (numNeighbors > 0) { // approximate graph
            switch (sequenceType) {
                case MLST:
                    return createMLSTDirectedGraph(nnAlgorithm, numNeighbors, points);
                case ALLELIC:
                    throw new NotImplementedException("Handling of allelic sequences has not been implemented yet.");
                default:
                    throw new IllegalArgumentException("Unsupported sequence type: " + sequenceType);
            }
        }
        else { // exact graph
            return generateExactGraph(points);
        }
    }

    private static DirectedGraph<SequenceTypingData> createMLSTDirectedGraph(
            NearestNeighbourSearchAlgorithm<?> nnAlgorithm, int numNeighbors, List<Point<?>> points) {
        @SuppressWarnings("unchecked")
        NearestNeighbourSearchAlgorithm<SequenceTypingData> typedAlgorithm = 
            (NearestNeighbourSearchAlgorithm<SequenceTypingData>) nnAlgorithm;
        
        @SuppressWarnings("unchecked")
        List<Point<SequenceTypingData>> typedPoints = (List<Point<SequenceTypingData>>) (List<?>) points;
        
        return new DirectedGraph<SequenceTypingData>(typedAlgorithm, numNeighbors, typedPoints);
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

    private static Graph generateExactGraph(List<Point<?>> points) {
        List<Node> nodes = new ArrayList<>();
        for (Point<?> point : points) {
            nodes.add(new Node(point.getSequence(), point.getId()));
        }
        int numNodes = nodes.size();
        List<Edge> edges = new ArrayList<>();
        DistanceFunction distanceFunction = new HammingDistance();
        for (int i = 0; i < numNodes; i++) {
            for (int j = i; j < numNodes; j++) {
                if (i != j) {
                    int distance = (int) distanceFunction.calculate(
                        nodes.get(i).getPoint().getSequence(),
                        nodes.get(j).getPoint().getSequence()
                    );
                    edges.add(new Edge(nodes.get(i), nodes.get(j), distance));
                    // edges.add(new Edge(nodes.get(j), nodes.get(i), distance));
                }
            }
        }
        return new Graph(edges);
    }

    private static void ensureDirectoryExists(String filePath) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    private static List<Edge> runStaticCameriniAlgorithm(String sequenceType, String inputFile, String outputFile, int numNeighbors, List<Point<?>> points, String persistedGraphFile, Graph g, String operationType) throws IOException {

        List<Node> nodesToProcess = null;
        if (persistedGraphFile != null) {
            // Just add/remove/update nodes from the persisted graph
            nodesToProcess = points.stream().map(p -> new Node(p.getSequence(), p.getId())).toList();
            switch (operationType) {
                case ADD:
                    g.addNodes(nodesToProcess);
                    break;
                case REMOVE:
                    g.removeNodes(nodesToProcess);
                    break;
                case UPDATE:
                    g.removeNodes(nodesToProcess);
                    g.addNodes(nodesToProcess);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation type: " + operationType);
                
            }
        }
        CameriniForest camerini = new CameriniForest(g, EDGE_COMPARATOR);
        List<Edge> edges = camerini.inferPhylogeny(g).getEdges();

        ensureDirectoryExists(outputFile);
        if (persistedGraphFile != null) {
            saveChanges(g, persistedGraphFile, outputFile, points, operationType);
        }
        else {
            GraphMapper.saveGraph(g, points.get(0).getSequence().getLength(), outputFile);
        }
        return edges;
    }

    private static void saveChanges(Graph g, String persistedGraphFile, String outputFile, List<Point<?>> points, String operationType) throws IOException {
        ensureDirectoryExists(outputFile);
        switch (operationType) {
            case ADD:
                for (Point<?> point : points) {
                    Node node = new Node(point);
                    List<Edge> incomingEdges = g.getNodeIncomingEdges(node);
                    GraphMapper.addNode(node, incomingEdges, outputFile, points.get(0).getSequence().getLength());
                }
                break;
            case REMOVE:
                for (Point<?> point : points) {
                    Node node = new Node(point);
                    GraphMapper.removeNode(node, outputFile, points.get(0).getSequence().getLength());
                }
                break;
            case UPDATE:
                for (Point<?> point : points) {
                    Node node = new Node(point);
                    GraphMapper.removeNode(node, outputFile, points.get(0).getSequence().getLength());
                }
                for (Point<?> point : points) {
                    Node node = new Node(point);
                    List<Edge> incomingEdges = g.getNodeIncomingEdges(node);
                    GraphMapper.addNode(node, incomingEdges, outputFile, points.get(0).getSequence().getLength());
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation type: " + operationType);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Edge> runDynamicCameriniAlgorithm(String sequenceType, String inputFile, String outputFile, int numNeighbors, List<Point<?>> points, String persistedGraphFile, Graph g, String operationType) throws IOException {
        // if (!(g instanceof DirectedGraph<?>)) {
        //     throw new IllegalArgumentException("Graph must be a DirectedGraph for the Dynamic Camerini Algorithm.");
        // }
        SerializableDynamicTarjanArborescence dynamicCamerini;
        FullyDynamicArborescence dynamicAlgorithm;
        List<Edge> edges;
        if (persistedGraphFile != null ) {
            DirectedGraph<Object> directedGraph = (DirectedGraph<Object>) g;
            int sequenceLength = points.get(0).getSequence().getLength();
            dynamicCamerini = new SerializableDynamicTarjanArborescence(inputFile, sequenceLength, g);

            // stream and cast dynamicCamerini.getRoots() to List<ATreeNode>
            List<ATreeNode> roots = dynamicCamerini.getRoots().stream()
                .map(root -> (ATreeNode) root)
                .toList();
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(g, roots, dynamicCamerini);

            switch (operationType) {
                case ADD:
                    addEdgesForPoints(directedGraph, (List<Point<Object>>) (List<?>) points, dynamicAlgorithm);
                    break;
                case REMOVE:
                    removeEdgesForPoints(directedGraph, (List<Point<Object>>) (List<?>) points, dynamicAlgorithm, g);
                    break;
                case UPDATE:
                    updateEdgesForPoints(directedGraph, (List<Point<Object>>) (List<?>) points, dynamicAlgorithm, g);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation type: " + operationType);
            }
            edges = dynamicAlgorithm.getCurrentArborescence();
        }
        else { // first time running the algorithm
            dynamicCamerini = new SerializableDynamicTarjanArborescence(
                new ArrayList<>(), new ArrayList<>(), new HashMap<>(), g);
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(g, new ArrayList<>(), dynamicCamerini);
            edges = dynamicAlgorithm.inferPhylogeny(g).getEdges();
        }

        return edges;
    }

    private static <T> void addEdgesForPoints(DirectedGraph<T> graph, List<Point<T>> points, FullyDynamicArborescence algorithm) {
        for (Point<T> point : points) {
            List<Point<T>> neighbors = graph.getNeighbors(point);
            List<Edge> edges = graph.getNNEdges(neighbors, new Node(point.getSequence(), point.getId()));
            for (Edge edge : edges) {
                algorithm.addEdge(edge);
            }
        }
    }

    private static <T> void removeEdgesForPoints(DirectedGraph<T> graph, List<Point<T>> points, FullyDynamicArborescence algorithm, Graph g) {
        for (Point<T> point : points) {
            Node nodeToRemove = new Node(point.getSequence(), point.getId());
            
            // Get all edges connected to this node (both incoming and outgoing)
            List<Edge> incomingEdges = g.getNodeIncomingEdges(nodeToRemove);
            List<Edge> outgoingEdges = g.getNodeOutgoingEdges(nodeToRemove);
            
            // Remove incoming edges from the dynamic algorithm
            for (Edge edge : incomingEdges) {
                algorithm.removeEdge(edge);
            }
            
            // Remove outgoing edges from the dynamic algorithm
            for (Edge edge : outgoingEdges) {
                algorithm.removeEdge(edge);
            }
        }
    }

    private static <T> void updateEdgesForPoints(DirectedGraph<T> graph, List<Point<T>> points, FullyDynamicArborescence algorithm, Graph g) {
        for (Point<T> point : points) {
            Node nodeToUpdate = new Node(point.getSequence(), point.getId());
            
            // Get all edges connected to this node (both incoming and outgoing)
            List<Edge> incomingEdges = g.getNodeIncomingEdges(nodeToUpdate);
            List<Edge> outgoingEdges = g.getNodeOutgoingEdges(nodeToUpdate);
            
            // Remove incoming edges from the dynamic algorithm
            for (Edge edge : incomingEdges) {
                algorithm.removeEdge(edge);
            }
            
            // Remove outgoing edges from the dynamic algorithm
            for (Edge edge : outgoingEdges) {
                algorithm.removeEdge(edge);
            }

            // Re-add updated edges
            List<Point<T>> neighbors = graph.getNeighbors(point);
            List<Edge> newEdges = graph.getNNEdges(neighbors, nodeToUpdate);
            for (Edge edge : newEdges) {
                algorithm.addEdge(edge);
            }
        }
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
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                runStaticTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, true);
                break;
            case DYNAMIC_ALGORITHM:
                runDynamicTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, true);
                break;
            case NEIGHBOR_JOINING:
                runNJTestIteration(sequenceType, initialPoints, tempGraphFile, outputFile, sequenceLength, true);
                break;
        }
        
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
                    runStaticTestIteration(sequenceType, batchPoints, tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, false);
                    break;
                case DYNAMIC_ALGORITHM:
                    // Dynamic algorithm processes points one by one
                    for (Point<?> point : batchPoints) {
                        runDynamicTestIteration(sequenceType, List.of(point), tempGraphFile, outputFile, numNeighbors, nnAlgorithm, sequenceLength, false);
                    }
                    break;
                case NEIGHBOR_JOINING:
                    runNJTestIteration(sequenceType, batchPoints, tempGraphFile, outputFile, sequenceLength, false);
                    break;
            }
            
            i += currentBatchSize;
            System.out.println("Batch added. Total points: " + i);
        }
        
        System.out.println("Test mode completed. Final phylogeny saved to: " + outputFile);
    }
    
    @SuppressWarnings("unchecked")
    private static void runStaticTestIteration(String sequenceType, List<Point<?>> points, String tempGraphFile, 
                                               String outputFile, int numNeighbors, 
                                               NearestNeighbourSearchAlgorithm<?> nnAlgorithm, 
                                               int sequenceLength, boolean isInitial) throws IOException {
        Graph graph;
        
        if (isInitial) {
            // Create new graph with initial points
            PhylogeneticData g = initializeGraph(sequenceType, null, numNeighbors, null, nnAlgorithm, points, STATIC_ALGORITHM, ADD);
            graph = (Graph) g;
        } else {
            // Load existing graph and add new points
            if (numNeighbors > 0) {
                graph = (Graph) GraphMapper.loadDirectedGraph(tempGraphFile, nnAlgorithm, numNeighbors);
            } else {
                graph = GraphMapper.loadGraph(tempGraphFile);
            }
            
            List<Node> nodesToAdd = points.stream()
                .map(p -> new Node(p.getSequence(), p.getId()))
                .toList();
            
            // Add nodes to graph
            graph.addNodes(nodesToAdd);
            
            // Create edges based on graph type
            if (numNeighbors > 0) {
                // Approximate graph: add edges only to nearest neighbors using DirectedGraph methods
                DirectedGraph<Object> directedGraph = (DirectedGraph<Object>) graph;
                for (Point<?> point : points) {
                    List<Point<Object>> neighbors = directedGraph.getNeighbors((Point<Object>) point);
                    List<Edge> edges = directedGraph.getNNEdges(neighbors, new Node(point.getSequence(), point.getId()));
                    for (Edge edge : edges) {
                        graph.addEdge(edge);
                    }
                }
            } else {
                // Exact graph: add edges from new nodes to all existing nodes
                List<Node> existingNodes = new ArrayList<>(graph.getNodes());
                existingNodes.removeAll(nodesToAdd); // Remove newly added nodes to get only existing ones
                
                DistanceFunction distanceFunction = new HammingDistance();
                List<Edge> newEdges = new ArrayList<>();
                
                // Edges from new nodes to existing nodes
                for (Node newNode : nodesToAdd) {
                    for (Node existingNode : existingNodes) {
                        int distance = (int) distanceFunction.calculate(
                            newNode.getPoint().getSequence(),
                            existingNode.getPoint().getSequence()
                        );
                        newEdges.add(new Edge(newNode, existingNode, distance));
                    }
                }
                
                // Edges between new nodes (if multiple new nodes)
                for (int i = 0; i < nodesToAdd.size(); i++) {
                    for (int j = i + 1; j < nodesToAdd.size(); j++) {
                        int distance = (int) distanceFunction.calculate(
                            nodesToAdd.get(i).getPoint().getSequence(),
                            nodesToAdd.get(j).getPoint().getSequence()
                        );
                        newEdges.add(new Edge(nodesToAdd.get(i), nodesToAdd.get(j), distance));
                    }
                }
                
                // Add edges to graph
                for (Edge edge : newEdges) {
                    graph.addEdge(edge);
                }
            }
        }
        
        // Infer phylogeny
        CameriniForest camerini = new CameriniForest(graph, EDGE_COMPARATOR);
        List<Edge> phylogeny = camerini.inferPhylogeny(graph).getEdges();
        
        // Save phylogeny and graph
        ensureDirectoryExists(outputFile);
        GraphMapper.saveArborescence(phylogeny, outputFile);
        GraphMapper.saveGraph(graph, sequenceLength, tempGraphFile);
        
        // Save LSH if applicable
        if (nnAlgorithm instanceof optimalarborescence.nearestneighbour.LSH<?>) {
            String lshParamsFile = tempGraphFile + LSH_EXTENSION;
            optimalarborescence.nearestneighbour.LSH.saveLSH(
                (optimalarborescence.nearestneighbour.LSH<?>) nnAlgorithm, 
                lshParamsFile
            );
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void runDynamicTestIteration(String sequenceType, List<Point<?>> points, String tempGraphFile,
                                                String outputFile, int numNeighbors,
                                                NearestNeighbourSearchAlgorithm<?> nnAlgorithm,
                                                int sequenceLength, boolean isInitial) throws IOException {
        Graph graph;
        FullyDynamicArborescence dynamicAlgorithm;
        
        if (isInitial) {
            // Create new graph with initial points
            PhylogeneticData g = initializeGraph(sequenceType, null, numNeighbors, null, nnAlgorithm, points, DYNAMIC_ALGORITHM, ADD);
            graph = (Graph) g;
            
            // Initialize dynamic algorithm
            SerializableDynamicTarjanArborescence dynamicCamerini = new SerializableDynamicTarjanArborescence(
                new ArrayList<>(), new ArrayList<>(), new HashMap<>(), graph);
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(graph, new ArrayList<>(), dynamicCamerini);
            dynamicAlgorithm.inferPhylogeny(graph);
        } else {
            // Load existing graph
            if (numNeighbors > 0) {
                graph = (Graph) GraphMapper.loadDirectedGraph(tempGraphFile, nnAlgorithm, numNeighbors);
            } else {
                graph = GraphMapper.loadGraph(tempGraphFile);
            }
            
            // Reconstruct dynamic algorithm from persisted graph
            SerializableDynamicTarjanArborescence dynamicCamerini = 
                new SerializableDynamicTarjanArborescence(tempGraphFile, sequenceLength, graph);
            
            List<ATreeNode> roots = dynamicCamerini.getRoots().stream()
                .map(root -> (ATreeNode) root)
                .toList();
            dynamicAlgorithm = new SerializableFullyDynamicArborescence(graph, roots, dynamicCamerini);
            
            // Add new edges for the new point
            if (numNeighbors > 0) {
                // Approximate graph: use DirectedGraph methods to add edges to nearest neighbors
                DirectedGraph<Object> directedGraph = (DirectedGraph<Object>) graph;
                addEdgesForPoints(directedGraph, (List<Point<Object>>) (List<?>) points, dynamicAlgorithm);
            } else {
                // Exact graph: add nodes and edges to all existing nodes
                List<Node> existingNodes = new ArrayList<>(graph.getNodes());
                List<Node> nodesToAdd = points.stream()
                    .map(p -> new Node(p.getSequence(), p.getId()))
                    .toList();
                graph.addNodes(nodesToAdd);
                
                // Create edges from new nodes to all existing nodes and add to dynamic algorithm
                DistanceFunction distanceFunction = new HammingDistance();
                for (Node newNode : nodesToAdd) {
                    for (Node existingNode : existingNodes) {
                        int distance = (int) distanceFunction.calculate(
                            newNode.getPoint().getSequence(),
                            existingNode.getPoint().getSequence()
                        );
                        Edge edge = new Edge(newNode, existingNode, distance);
                        graph.addEdge(edge);
                        dynamicAlgorithm.addEdge(edge);
                    }
                }
            }
        }
        
        // Get current phylogeny
        List<Edge> phylogeny = dynamicAlgorithm.getCurrentArborescence();
        
        // Save phylogeny and graph
        ensureDirectoryExists(outputFile);
        GraphMapper.saveArborescence(phylogeny, outputFile);
        GraphMapper.saveGraph(graph, sequenceLength, tempGraphFile);
        
        // Save LSH if applicable
        if (nnAlgorithm instanceof optimalarborescence.nearestneighbour.LSH<?>) {
            String lshParamsFile = tempGraphFile + LSH_EXTENSION;
            optimalarborescence.nearestneighbour.LSH.saveLSH(
                (optimalarborescence.nearestneighbour.LSH<?>) nnAlgorithm, 
                lshParamsFile
            );
        }
    }
    
    private static void runNJTestIteration(String sequenceType, List<Point<?>> points, String tempGraphFile,
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
    }
}