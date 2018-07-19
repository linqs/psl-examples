### Preference Prediction

## Problem

In this example, we attempt to discover a user's preference for a specific joke.
This can also be viewed as a recommendation problem where out recommendation would just be the joke with the highest predicted rating.

## Dataset

The dataset originally came from the Jester project.
The full dataset contains ratings for almost 25,000 users on 100 jokes.
Each joke is rated on a scale of [-10, +10] \(which has been normalized to [0, 1] for PSL\).

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
 - `collaborative filtering`
 - `evaluation`
 - `groovy`
 - `inference`
 - `link prediction`
 - `real data`
 - `recommendation`
 - `weight learning`
