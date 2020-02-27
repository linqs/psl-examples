### Entity Resolution

## Problem

In this example, we attempt to resolve duplicate authors and papers in a citation database.

## Dataset

The dataset originally came from CiteSeer.
The full dataset contains around 3,000 author references and 1,500 paper references.
Part of the full dataset was used to create three varying size datasets:
 - small:
   - [link](https://linqs-data.soe.ucsc.edu/public/psl-examples-data/entity-resolution/entity-resolution-small.tar.gz)
   - Number of Authors: 1136
   - Number of Papers: 864
 - medium:
   - [link](https://linqs-data.soe.ucsc.edu/public/psl-examples-data/entity-resolution/entity-resolution-medium.tar.gz)
   - Number of Authors: 1813
   - Number of Papers: 1143
 - large:
   - [link](https://linqs-data.soe.ucsc.edu/public/psl-examples-data/entity-resolution/entity-resolution-large.tar.gz)
   - Number of Authors: 2892
   - Number of Papers: 1504

Currently this example is using the small dataset.
This can be changed be editing the `RAW_DATA_NAME` constants in the `data/fetchData.sh` script.

This dataset originally came from:
```
@article {bhattacharya:tkdd07,
    author = {Indrajit Bhattacharya and Lise Getoor},
    title = {Collective Entity Resolution In Relational Data},
    journal = {ACM Transactions on Knowledge Discovery from Data},
    year = {2007},
    pages = {1--36},
}
```

## Keywords

 - `cli`
 - `entity resolution`
 - `evaluation`
 - `inference`
 - `link prediction`
 - `real data`
 - `weight learning`
