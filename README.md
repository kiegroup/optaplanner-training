Developing Drools, OptaPlanner and jBPM
=======================================

**If you want to build or contribute to a kiegroup project, [read this document](https://github.com/droolsjbpm/droolsjbpm-build-bootstrap/blob/master/README.md).**

**It will save you and us a lot of time by setting up your development environment correctly.**
It solves all known pitfalls that can disrupt your development.
It also describes all guidelines, tips and tricks.
If you want your pull requests (or patches) to be merged into master, please respect those guidelines.

To build optaplanner-training locally:

    $ git clone .../optaplanner-training.git
    $ git clone .../optaplanner
    $ cd optaplanner-training
    $ cd optaplanner-training-generator
    # Generate the labs using the latest optaplanner example sources
    $ ant
    $ cd ..
    $ mvn clean install -Dfull
    # Zip is there
    $ ls optaplanner-training-assembly/target
