### Citation Categories

## Problem

In this example, we attempt to classify documents in a citation network into one of several categories.

## Dataset

The dataset originally came from Cora.
It contains informations on the categories of some documents as well as links between those documents.
The links are represented as only one predicate, but can represent several relationships between documents such as:
 - Hyperlinks
 - Citations
 - Shared Authorship

## Origin

This example is a simplified version of one of the experiments from Bach et al.'s core PSL paper:
"Hinge-Loss Markov Random Fields and Probabilistic Soft Logic":
```
@article{bach:jmlr17,
  Author = {Bach, Stephen H. and Broecheler, Matthias and Huang, Bert and Getoor, Lise},
  Journal = {Journal of Machine Learning Research (JMLR)},
  Title = {Hinge-Loss {M}arkov Random Fields and Probabilistic Soft Logic},
  Year = {2017}
}
```

## Keywords

 - `cli`
 - `collective classification`
 - `evaluation`
 - `groovy`
 - `inference`
 - `real data`
 - `weight learning`
