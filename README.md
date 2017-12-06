# PSL Examples

Various examples to showcase the functionality of PSL.

Each experiment will generally have the following structure:
```
   <experiment>
   ├── cli
   │   ├── <experiment>.data
   │   ├── <experiment>.psl
   │   └── run.sh
   ├── data
   │   └── fetchData.sh
   └── README.md
```

The `data` directory will be where the data for each experiment will be downloaded and extracted.
Every other directory will be a different interface through which you can run PSL.
Each inference directory will contain a `run.sh` script that will handle all the data/dependency fetching and running the example.
