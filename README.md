# PSL Examples

Various examples to showcase the functionality of PSL.

Each experiment will generally have the following structure:
```
   <experiment>
   ├── cli
   │   ├── <experiment>.data
   │   ├── <experiment>.psl
   │   └── run.sh
   ├── groovy
   │   ├── pom.xml
   │   ├── run.sh
   │   └── src
   ├── python
   │   ├── <experiment>.py
   │   └── run.sh
   ├── data
   │   └── fetchData.sh
   └── README.md
```

The `data` directory will be where the data for each experiment will be downloaded and extracted.
Every other directory will be a different interface through which you can run PSL.
Each inference directory will contain a `run.sh` script that will handle all the data/dependency fetching and running the example.

The examples are organized such that you can copy an experiment directory, delete the interfaces that you are not using, and the remaining interface(s) will run.
That is, all interfaces are independent but all rely on the data directory.
A convenient way to start a new project is to copy an existing example and delete the interfaces you do not wish to use.
