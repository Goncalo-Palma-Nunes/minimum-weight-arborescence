# minimum-weight-arborescence

## About

This repository offers a scalable framework for the efficient computation and update of phylogenetic trees. Phylogenetic trees can be computed with this library by running one of several algorithms for the Minimum Weight Arborescence problem[^1][^*]. Two different implementations of Edmonds' static algorithm[^1] are provided: one that exploits memory-mapped files to store a phylogenetic graph[^**] and another implementation where the graph's edges are computed on demand. An additional legacy implementation, where each node's incidence edge set is kept in a pairing heap[^3] still exists in the PLACEHOLDER branch, although it is far less memory and time efficient than the other available implementations.

This library can handle phylogenetic datasets with and without missing data. When a dataset has no missing data, the available implementations of Edmonds' algorithm behave, in essence, as a classical greedy algorithm for the Minimum Spanning Tree (MST) problem, but with additional overhead. At this moment, there is currently no available implementation of a MST algorithm for when there is no missing data.

A Locality-Sensitive Hashing[^4] heuristic is also available, when running Edmonds' algorithm with the on demand computation of edges. When using this heuristic, only a subset of a *taxon*'s approximate nearest neighbors (according to a given distance function) are considered before computing any edges. In addition, this heuristic also considers only a subset the dataset's *loci* for these similarity queries. When this heuristic fails to find a valid neighbor to continue Edmonds' algorithm, the algorithm falls back to computing all edges. **Using a Locality-Sensitive Hashing heuristic can produce sub-optimal trees**.

[^*]: A Minimum Weight Arborescence is sometimes called an Optimum Branching or Directed Minimum Spanning Tree in the algorithms and combinatorial optimization literatures.
[^**]: Given an input dataset with genomic data, that dataset can be manipulated as a complete graph by mapping *taxa* to nodes and the evolutionary distance between them as edges. 

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
    1 & 
    \begin{aligned}
    & \text{if $v[i]$ has missing} \\ & \text{data and $u[i]$ does not}    
    \end{aligned}\\
    1 & \text{if } u[i] \not = v[i],\\
    0 & \text{otherwise }
\end{cases}
$$

## Compiling the project

`mvn compile`

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

## Accepted file formats

- Fasta files for sequences of nucleotide bases ('A', 'C', 'G', 'T');
- CSV (with tab delimitors) for MultiLocus Sequence Typing data.

## Infering or Updating a Phylogeny




## References:
[^1]: Edmonds, J. (1967). Optimum branchings. Journal of Research of the National Bureau of Standards Section B Mathematics and Mathematical Physics. https://doi.org/10.6028/JRES.071B.032
[^2]: Pollatos, G.G., Telelis, O.A., Zissimopoulos, V. (2006). Updating Directed Minimum Cost Spanning Trees. In: Àlvarez, C., Serna, M. (eds) Experimental Algorithms. WEA 2006. Lecture Notes in Computer Science, vol 4007. Springer, Berlin, Heidelberg. https://doi.org/10.1007/11764298_27
[^3]: Fredman, Michael L., Robert Sedgewick, Daniel D. Sleator, and Robert E. Tarjan. ‘The Pairing Heap: A New Form of Self-Adjusting Heap’. Algorithmica 1, no. 1 (1986): 111–29. https://doi.org/10.1007/BF01840439.
[^4]: Piotr Indyk and Rajeev Motwani. 1998. Approximate nearest neighbors: towards removing the curse of dimensionality. In Proceedings of the thirtieth annual ACM symposium on Theory of computing (STOC '98). Association for Computing Machinery, New York, NY, USA, 604–613. https://doi.org/10.1145/276698.276876

