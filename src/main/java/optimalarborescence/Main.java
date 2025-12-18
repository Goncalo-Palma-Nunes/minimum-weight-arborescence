package optimalarborescence;

import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.DirectedGraph;
import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.distance.HammingDistance;
import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.sequences.*;
import optimalarborescence.nearestneighbour.*;
import optimalarborescence.inference.CameriniForest;
import optimalarborescence.inference.dynamic.*;
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
    private static final List<String> NN_ALGORITHMS = List.of(LSH);
    private static final String YES = "y";
    private static final String NO = "n";
    private static final String EXIT = "exit";
    private static final String ADD = "add";
    private static final String REMOVE = "remove";
    private static final String UPDATE = "update";
    private static final List<String> OPERATION_TYPE = List.of(ADD, REMOVE, UPDATE);
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        if (args.length < 4 || args.length > 5) {
            System.err.println("Wrong Invocation: java -jar OptimalArborescence.jar <sequence_type> <input_sequence_file> <output_file> <operation_type> [<persisted_graph_file>]\nWhere:\n\t- <sequence_type> is either 'mlst' or 'allelic'\n\t- <input_sequence_file> is the path to the input sequence file\n\t- <output_file> is the path to the output file\n\t- <operation_type> is either 'add', 'remove', or 'update'\n\t - <persisted_graph_file> is an optional path to a persisted graph file to continue from a previous run.");
            System.exit(1);
        }
        String sequenceType = args[0];
        sequenceType = sequenceType.toLowerCase();
        String inputSequenceFile = args[1]; String outputFile = args[2];
        String operationType = args[3].toLowerCase();
        validateParameters(sequenceType, inputSequenceFile, operationType);
        
        String persistedGraphFile = null;
        int sequenceLength = -1;
        Graph g = null;
        if (args.length == 4) {
            persistedGraphFile = args[3];
            if (!fileExists(persistedGraphFile)) {
                throw new FileNotFoundException("Persisted graph file does not exist: " + persistedGraphFile);
            }
        }

        int numNeighbors = approximatesGraph();
        List<Point<?>> newPoints = processSequences(sequenceType, inputSequenceFile);
        NearestNeighbourSearchAlgorithm<?> nnAlgorithm = null;
        if (numNeighbors > 0) {
            sequenceLength = newPoints.get(0).getSequence().getLength();
            nnAlgorithm = selectNNAlgorithm(sequenceType, sequenceLength);
        }
        g = initializeGraph(sequenceType, inputSequenceFile, numNeighbors, persistedGraphFile, nnAlgorithm, newPoints);
        String algorithmType = readAlgorithmType();
        
        switch (algorithmType) {
            case STATIC_ALGORITHM:
                runStaticCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, g, operationType);
                break;
            case DYNAMIC_ALGORITHM:
                runDynamicCameriniAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors, newPoints, persistedGraphFile, g, operationType);
                break;
            case NEIGHBOR_JOINING:
                // runNeighborJoiningAlgorithm(sequenceType, inputSequenceFile, outputFile, numNeighbors);
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

    private static Graph initializeGraph(String sequenceType, String inputFile, 
                                        int numNeighbors, String persistedGraphFile,
                                        NearestNeighbourSearchAlgorithm<?> nnAlgorithm, List<Point<?>> points) throws IOException {
        if (persistedGraphFile != null) {
            if (numNeighbors > 0) {
                return GraphMapper.loadDirectedGraph(persistedGraphFile, nnAlgorithm, numNeighbors);
            } 
            else {
                return GraphMapper.loadGraph(persistedGraphFile);
            }
        }

        if (numNeighbors > 0) { // approximate graph
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

    private static NearestNeighbourSearchAlgorithm<?> selectNNAlgorithm(String sequenceType, int sequenceLength) {
        System.out.println("Select the Nearest Neighbour Search Algorithm:\n'"+ LSH + "' for Locality Sensitive Hashing\nEnter " + EXIT + " to quit.");
        String response = "";

        while (!validNNAlgorithm(response)) {
            response = System.console().readLine().trim().toLowerCase();
            switch (response) {
                case LSH:
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
                    return new LSH<>(numComparedPositions, numHashTables, 0, sequenceLength, new HammingDistance(), maxDistance);
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

        if (persistedGraphFile != null) {
            saveChanges(g, persistedGraphFile, outputFile, points, operationType);
        }
        else {
            GraphMapper.saveGraph(g, points.get(0).getSequence().getLength(), outputFile);
        }
        return edges;
    }

    private static void saveChanges(Graph g, String persistedGraphFile, String outputFile, List<Point<?>> points, String operationType) throws IOException {
        switch (operationType) {
            case ADD:
                for (Node node : g.getNodes()) { // TODO - iterar sobre nós ou pontos? Acho que é pontos
                    List<Edge> incomingEdges = g.getNodeIncomingEdges(node);
                    GraphMapper.addNode(node, incomingEdges, outputFile, points.get(0).getSequence().getLength());
                }
                break;
            case REMOVE:
                for (Node node : g.getNodes()) { // TODO - iterar sobre nós ou pontos? Acho que é pontos
                    GraphMapper.removeNode(node, outputFile, points.get(0).getSequence().getLength());
                }
                break;
            case UPDATE:
                for (Node node : g.getNodes()) { // TODO - iterar sobre nós ou pontos? Acho que é pontos
                    GraphMapper.removeNode(node, outputFile, points.get(0).getSequence().getLength());
                }
                for (Node node : g.getNodes()) { // TODO - iterar sobre nós ou pontos? Acho que é pontos
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
}