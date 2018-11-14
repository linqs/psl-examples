### Knowledge Graph Inference

## Problem

In this example, we attempt to construct a knowledge graph from noisy facts and an ontology.

## Dataset

The dataset originally came from CMUs Never-Ending Language Learning (NELL) project.
NELL is a large knowledge graph that attempt to extract facts from natural language found on the internet.
This data is the output from some of Nell's raw extractors (before it has been added to the knowledge graph).


### Dataset Details
Since this datasets is fairly complex and contains many files, here is a more detailed description of the predicates/files.

```yaml
Partition: observations
   Predicate: CANDCAT_*
   Files:
      CANDCAT_*_obs.txt
   Description:
      Contains all the candidate categories for each entity/object.
      These candidate categories come from many different algorithms/systems (hence the multiple files).
      The model assumes that all these are noisy and may be false.
      These values may come with a probability/score in the range [0, 1].

   Predicate: CANDREL_*
   Files:
      CANDREL_*_obs.txt
   Description:
      Similar to CANDCAT, but for relations instead of entities.

   Predicate: PROMCAT_GENERAL
   Files:
      PROMCAT_GENERAL_obs.txt
   Description:
      Similar to CANDCAT, but I think these ones came from a different source or process.

   Predicate: PROMREL_GENERAL
   Files:
      PROMREL_GENERAL_obs.txt
   Description:
      Similar to PROMCAT_GENERAL, but for relations.

   Predicate: CAT
   Files:
      CAT_obs.txt
   Description:
      Observed categories that are known to be true.

   Predicate: REL
   Files:
      REL_obs.txt
   Description:
      Observed relations that are known to be true.

   Predicate: SAMEENTITY
   Files:
      SAMEENTITY_obs.txt
   Description:
      Observed instance of two entities being the same.

   Predicate: VALCAT
   Files:
      VALCAT_obs.txt
   Description:
      Prior information provided on a per-(entity, category) basis.
      I do not know off the top of my head how this is computed, but I assume it is some sort of aggregation over entities and categories.

   Predicate: VALREL
   Files:
      VALREL_obs.txt
   Description:
      Like ValCat, but for relations.

   NELLs Ontology
      NELL provides an ontology on top of the knowledge graph.
      These predicates supply information about this ontology.

      Predicate: DOMAIN
      Files:
         DOMAIN_obs.txt
      Description:
         Domain constraint.

      Predicate: INV
      Files:
         INV_obs.txt
      Description:
         Inverse relationship.

      Predicate: MUT
      Files:
         MUT_obs.txt
      Description:
         Mutual Exclusion constraint for entities.

      Predicate: RANGE2
      Files:
         RANGE2_obs.txt
      Description:
         Range constraint.

      Predicate: RMUT
      Files:
         RMUT_obs.txt
      Description:
         Mutual Exclusion constraint for relations.

      Predicate: RSUB
      Files:
         RSUB.txt
      Description:
         Subsumption relationship for relations.

      Predicate: SUB
      Files:
         SUB_obs.txt
      Description:
         Subsumption relationship for entities.

Partition: targets
   Predicate: CAT
   Files:
      CAT_targets.txt
   Description:
      All the categories that PSL is to infer a value for.

   Predicate: REL
   Files:
      REL_targets.txt
   Description:
      All the relations that PSL is to infer a value for.

Partition: truth
   Predicate: CAT
   Files:
      CAT_truth.txt
   Description:
      Truth values for categories that is used for weight learning or evaluation.

   Predicate: REL
   Files:
      REL_truth.txt
   Description:
      Truth values for relations that is used for weight learning or evaluation.
```

## Origin

This example is a simplified version of one of the experiments from Pujara et al.'s paper:
"Knowledge Graph Identification":
```
@conference {pujara:iswc13,
  title = {Knowledge Graph Identification},
  booktitle = {International Semantic Web Conference (ISWC)},
  year = {2013},
  note = {Winner of Best Student Paper award},
  author = {Pujara, Jay and Miao, Hui and Getoor, Lise and Cohen, William}
}
```

## Keywords

 - `bulk rules`
 - `cli`
 - `evaluation`
 - `groovy`
 - `inference`
 - `real data`
 - `weight learning`
