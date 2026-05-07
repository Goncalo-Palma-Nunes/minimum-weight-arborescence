# minimum-weight-arborescence

## About

This repository offers a scalable framework for the efficient computation and update of phylogenetic trees. Phylogenetic trees can be computed with this library by running one of several algorithms for the Minimum Weight Arborescence problem[^1]\*. Two different implementations of Edmonds' static algorithm[^1] are provided: one that exploits memory-mapped files to store a phylogenetic graph\*\* and another implementation where the graph's edges are computed on demand. A dynamic algorithm to maintain Minimum Weight Arborescences, based on the Augmented Tree (ATree) data-structure, by Pollatos et al.[^2] is also available. An additional legacy implementation, where each node's incidence edge set is kept in a pairing heap[^3] still exists in the PLACEHOLDER branch, although it is far less memory and time efficient than the other available implementations.

This library can handle phylogenetic datasets with and without missing data. When a dataset has no missing data, the available implementations of Edmonds' algorithm behave, in essence, as a classical greedy algorithm for the Minimum Spanning Tree (MST) problem, but with additional overhead. At this moment, there is currently no available implementation of a MST algorithm for when there is no missing data.

A Locality-Sensitive Hashing[^4] heuristic is also available, when running Edmonds' algorithm with the on demand computation of edges. When using this heuristic, only a subset of a *taxon*'s approximate nearest neighbors (according to a given distance function) are considered before computing any edges. In addition, this heuristic also considers only a subset the dataset's *loci* for these similarity queries. When this heuristic fails to find a valid neighbor to continue Edmonds' algorithm, the algorithm falls back to computing all edges. **Using a Locality-Sensitive Hashing heuristic can produce sub-optimal trees**.

Notes:

\* A Minimum Weight Arborescence is sometimes called an Optimum Branching or Directed Minimum Spanning Tree in the algorithms and combinatorial optimization literatures.

\*\* Given an input dataset with genomic data, that dataset can be manipulated as a complete graph by mapping *taxa* to nodes and the evolutionary distance between them as edges. 

## Data Types and Input Files

This library supports the computation of phylogenetic trees between sequences of nucleotide bases and between sequences of MultiLocus Sequence Typing (MLST) data (as well as cgMLST and wgMLST extensions).

We assume that nucleotide sequences are inputted through FASTA files and that datasets based on typing data schemas come in a CSV format.

## Distance Measures

There are currently *two* available distance functions to compute edge weights.

For symmetric graphs, namely those representing datasets **without missing data**, we use the classic Hamming distance function.

For asymetric graphs, namely those representing datasets **with missing data**, we use a modified Hamming distance. This asymmetric hamming distance is defined as the sum over all sequence positions between two *taxa* of the following function:

$$
d(u, v, i) =
\begin{cases}
    1 & \text{if $v[i]$ has missing data and $u[i]$ does not,} \\
    1 & \text{if } u[i] \not = v[i],\\
    0 & \text{otherwise }
\end{cases}
$$

## Packaging the project into a .jar

This project can be packaged with maven into an executable .jar by running:

`mvn clean package`

This generates a file called OptimalArborescence.jar in the target/ directory. This file can then be run with:

`java -jar target/OptimalArborescence.jar`

## Compiling the project

With maven:

`mvn compile`

With javac (compiles classes to a lib/ folder):

`javac -d out -cp "lib/*" --release 21 $(find src/main/java -name "*.java")`

## Input Parameters

The basic invocation grammar for the Main.java compiled class is the following:

`java -jar OptimalArborescence.jar <sequence_type> [--missing-data] <input_sequence_file> <output_file> <operation_type> [<persisted_graph_file>] [--on-demand]`

**Parameters between squared brackets are optional**.

- <sequence_type>: Defines the type of data to be processed: ```mlst``` for typing data or ```nucleotide``` for nucleotide sequences;
- --missing-data: A boolean flag to signal that the input file has missing data;
- <input_sequence_file>: The relative or absolute path a CSV or FASTA file with the input sequences;
- <operation_type>: Type of dynamic graph operation to be performed. It can take the values ```add```, ```remove```, ```update```. For testing purposes, there is an additional option ```test```, which incrementally inserts a new *taxon* and performs a phylogenetic inference step until all the sequences in the <input_sequence_file> are inserted;
- <output_file>: The base path to where the output files should be written. At least two output files are always generated: a Node Index memory-mapped file for the sequences and an Edge Array memory-mapped file for the computed arborescence. If the --on-demand flag is not used and <operation_type> is ```add```, several other edge array files (one per new *taxon*) are created. Each such file stores the edges incident on that file's respective node/*taxon*;
- <persisted_graph_file>: The base path to the output files of a previously computed phylogenetic inference run. It is **not** the path to an arborescence, but the path and base name for the series of memory-mapped files associated with a previous inference run (that run's <output_file>). **When the operation to be performed is an update or remove, this parameter is mandatory**;
- --on-demand: A boolean flag to signal that Edmonds' contraction phase should compute edge weights on demand, instead of querying them from memory-mapped files. This flag can be useful for large datasets, when disk space is a limited resource. However, this is **significantly slower** than building and querying memory-mapped edge array files.

After invocation, the user is prompted for the following parameters:

- $$k$$: A positive integer to represent the maximum initial neighborhood size of a node for the LSH heuristic. If $$k=0$$, then no LSH heuristic is used to approximate the graph's distance matrix and the complete N x N graph (where N is the number of *taxa*) is used;
- $$algorithmType$$: The type of inference algorithm to be executed. It can take the value ```static``` to execute just Edmonds' algorithm or ```dynamic``` to use an ATree on top of Edmonds' static algorithm;

If $$k > 0$$ (if the distance matrix is to be approximated) the user is also prompted for the initialization parameters for an auxiliary data-structure (currently, the only supported one is a LSH struct)

- $$searchAlgorithm$$: The approximate nearest neighbor search algorithm to be used. Currently, the only possible option is ```lsh```

# LSH Initialization Parameters

- $$numHashParameters$$: The number of parameters used to initialize the hash functions. It can only take the value of a positive integer and must be at most the number of positions (number of *loci* or nucleotide positions) in the dataset's sequences. The $$numHashParameters$$ defines the number of sequence positions that are considered during similarity queries between two sequences;
- $$numHashFunctions$$: The number of unique hash functions used for the LSH heuristic:
- $$maxDistance$$: The maximum distance at which two sequences are still considered neighbors. If $$distance(x, y) > maxDistance$$, then sequences $$x$$ and $$y$$ are not considered to be neighbors (with regard to edge weight computations) by the LSH heuristic, even if they are matched by one or more hash functions.

## Executing Main.java

With maven:

`mvn exec:java -Dexec.mainClass="optimalarborescence.Main"`

With java:

`java -cp "out:lib/*" optimalarborescence.Main <input-paramters>`

# Examples

The examples in this section use java instead of mvn to execute our main program.

- Infering an initial phylogenetic tree with queries to memory-mapped edge arrays:

`java -cp out:lib/* optimalarborescence.Main mlst --missing-data /path/to/input.csv /path/to/output add`

- Infering an initial phylogenetic tree with on demand edge computations:

`java -cp out:lib/* optimalarborescence.Main mlst --missing-data /path/to/input.csv /path/to/output add --on-demand`

- Adding *taxa* to a previously computed phylogenetic tree:

`java -cp out:lib/* optimalarborescence.Main mlst --missing-data /path/to/input.csv /path/to/output add /path/to/previously/computed/tree`

- Removing *taxa* from a previously computed phylogenetic tree. The sequences in ```/path/to/input.csv``` are the sequences to be removed from ```/path/to/previously/computed/tree```. This last parameter (```/path/to/previously/computed/tree```) is non-optional during *taxa* removal operations.

`java -cp out:lib/* optimalarborescence.Main mlst --missing-data /path/to/input.csv /path/to/output remove /path/to/previously/computed/tree`

- Updating *taxa* sequences from a previously computed phylogenetic tree. The sequences in ```/path/to/input.csv``` are the sequences to be updated from ```/path/to/previously/computed/tree```. This last parameter (```/path/to/previously/computed/tree```) is non-optional during *taxa* removal operations.

`java -cp out:lib/* optimalarborescence.Main mlst --missing-data /path/to/input.csv /path/to/output update /path/to/previously/computed/tree`

## Executing a specific program

A specific compiled java program can be executed with the exec-maven-plugin in the following way:

`mvn exec:java -Dexec.mainClass="optimalarborescence.NAME"`

Where NAME in `optimalarborescence.NAME` should be replaced by the name of the file to be executed. For example, to print a simple "Hello, World!" as is the case for App.java, run:

`mvn exec:java -Dexec.mainClass="optimalarborescence.App"`

To add arguments to the program invocation, use the -Dexec.args flag, such as in the following example:

`mvn exec:java -Dexec.mainClass="optimalarborescence.Main" -Dexec.args="mlst input.csv output.txt add"`

## Running Tests

To run all the unit tests with maven, just run the following maven command at the project's root directory:

`mvn test`

To run a specific class of tests, add the `-Dtest` flag. For example, to run the NodeIndexMapperTest run:

`mvn test -Dtest=NodeIndexMapperTest`

If you just want to run a specific unit test from the a class of tests `<TestClass>`, run the following command:

`mvn test -Dtest=<TestClass>#<TEST_NAME>`

You can also use wild cards to run a set of tests. For example, the following command runs all the tests for the dynamic implementation of the minimum weight arborescence algorithm, by running all the tests whose class name starts with *FullyDynamic*:

`mvn test -Dtest="FullyDyanmic*"`

## References:
[^1]: Edmonds, J. (1967). Optimum branchings. Journal of Research of the National Bureau of Standards Section B Mathematics and Mathematical Physics. https://doi.org/10.6028/JRES.071B.032
[^2]: Pollatos, G.G., Telelis, O.A., Zissimopoulos, V. (2006). Updating Directed Minimum Cost Spanning Trees. In: Àlvarez, C., Serna, M. (eds) Experimental Algorithms. WEA 2006. Lecture Notes in Computer Science, vol 4007. Springer, Berlin, Heidelberg. https://doi.org/10.1007/11764298_27
[^3]: Fredman, Michael L., Robert Sedgewick, Daniel D. Sleator, and Robert E. Tarjan. ‘The Pairing Heap: A New Form of Self-Adjusting Heap’. Algorithmica 1, no. 1 (1986): 111–29. https://doi.org/10.1007/BF01840439.
[^4]: Piotr Indyk and Rajeev Motwani. 1998. Approximate nearest neighbors: towards removing the curse of dimensionality. In Proceedings of the thirtieth annual ACM symposium on Theory of computing (STOC '98). Association for Computing Machinery, New York, NY, USA, 604–613. https://doi.org/10.1145/276698.276876
