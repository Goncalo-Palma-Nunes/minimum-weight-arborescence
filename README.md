# minimum-weight-arborescence

## About

This repository is part of an initial phase of a master's thesis in Computer Science at Instituto Superior Técnico (University of Lisbon). The thesis' title is "Online methods for analyzing core genomic relationships among 1 billion bacterial pathogens" and it is supervised by professors Alexandre Francisco and Cátia Vaz.

The problem this thesis aims to tackle is **how to efficiently update a phylogenetic tree**.

## Compiling the project

`mvn compile`

## Executing a program

A specific compiled java program can be executed with the exec-maven-plugin in the following way:

`mvn exec:java -Dexec.mainClass="optimalarborescence.NAME"`

Where NAME in `optimalarborescence.NAME` should be replaced by the name of the file to be executed. For example, to print a simple "Hello, World!" as is the case for App.java, run:

`mvn exec:java -Dexec.mainClass="optimalarborescence.App"`

To see an example of writing a simple graph to a memory mapped file and reading it run:

`mvn exec:java -Dexec.mainClass="optimalarborescence.Main"`

## Running Tests

To run all the unit tests with maven, just run the following maven command at the project's root directory:

`mvn test`

To run a specific class of tests, add the `-Dtest` flag. For example, to run the NodeIndexMapperTest run:

`mvn test -Dtest=NodeIndexMapperTest`

If you just want to run a specific unit test from the a class of tests `<TestClass>`, run the following command:

`mvn test -Dtest=<TestClass>#<TEST_NAME>`

You can also use wild cards to run a set of tests. For example, the following command runs all the tests for the dynamic implementation of the minimum weight arborescence algorithm, by running all the tests whose class name starts with *FullyDynamic*:

`mvn test -Dtest="FullyDyanmic*"`