# minimum-weight-arborescence

## About

This repository is part of an initial exploratory phase for a master's thesis in Computer Science at Instituto Superior Técnico (University of Lisbon). The thesis' title is "Online methods for analyzing core genomic relationships among 1 billion bacterial pathogens" and it is supervised by professors Alexandre Francisco and Cátia Vaz.

The problem this thesis aims to solve is **how to efficiently update a phylogenetic tree**.

## Compiling the project

`mvn compile`

## Executing a program

A specific compiled java program can be executed with the exec-maven-plugin in the following way:

`mvn exec:java -Dexec.mainClass="optimalarborescence.NAME"`

Where NAME in `optimalarborescence.NAME` should be replaced by the name of the file to be executed. For example, to print a simple "Hello, World!" as is the case for App.java, run:

`mvn exec:java -Dexec.mainClass="optimalarborescence.App"`

To see an example of writing a simple graph to a memory mapped file and reading it run:

`mvn exec:java -Dexec.mainClass="optimalarborescence.Main"`