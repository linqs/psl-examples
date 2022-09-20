### Drug-Drug Interaction

## Problem
In this example, we attempt to infer unknown drug drug interactions from a network of
multiple drug-based similarities and known interactions. 

## Dataset
- general-interactions: This dataset came from DrugBank version 4.3 for a total of 4,293 interactions across 315 drugs.  There is also a file DrugBankIDs, which maps integer IDs to DrugIDs.  Each fold share the same similarity data. 
- crd-interactions: This dataset came from DrugBank and Drugs.com for a total of 10,106 CRD interactions across 807 drugs. These IDs are anonymized (so there is no DrugBankID mapping).  Each fold uses different similarity data (since blocking is stricter in the crd setting).
- ncrd-interactions: This dataset came from DrugBank and Drugs.com for a total of 45,737 NCRD interactions across 807 drugs. These IDs are also anonymized.  Each fold uses same similarity data.  

Each dataset above contain seven drug–drug similarities. Four of these similarity measures are drug-based: Chemical-based, Ligand-based, Side-effect-based and Annotation-based. Three similarities are between drug targets and computed by aggregating over known targets for the drugs: Sequence-based, PPI network-based, and Gene Ontology-based. 

## Experimental Setup
The default settings for the run script is for the dataset "general-interactions".  Therefore, the evaluator thresholds must be changed when running other datasets. 

## Origin
[A Probabilistic Approach for Collective Similarity-Based Drug-Drug Interaction Prediction](https://linqs.org/publications/#id:sridhar-bio16)
The data came from the original [repo](https://bitbucket.org/linqs/psl-drug-interaction-prediction/src/master/).  Each fold contains ground atoms which have been preprocessed and dumped from the original experiment.  


```
@article{sridhar:bio16,
    title = {A Probabilistic Approach for Collective Similarity-Based Drug-Drug Interaction Prediction},
    author = {Dhanya Sridhar and Shobeir Fakhraei and Lise Getoor},
    journal = {Bioinformatics},
    year = {2016},
    publisher = {Oxford},
    pages = {3175--3182},
    volume = {32},
    number = {20},
}
```

## Keywords

 - `cli`
 - `evaluation`
 - `inference`
 - `real data`
 - `weight learning`