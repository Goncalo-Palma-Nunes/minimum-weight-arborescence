# minimum-weight-arborescence

## About

This repository is part of a master's thesis in Computer Science at Instituto Superior Técnico (University of Lisbon). The thesis' title is "Online methods for analyzing core genomic relationships among 1 billion bacterial pathogens" and it is supervised by professors Alexandre Francisco and Cátia Vaz.

The goal of this thesis is to create a scalable implementation of Edmonds' algorithm for the static minimum weight arborescence problem[^1], as well as a dynamic version of the same algorithm, based on the Augmented Tree data-structure[^2], to efficiently compute and maintain phylogenetic trees for very large graphs.

## Compiling the project

`mvn compile`

## Executing a program

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
