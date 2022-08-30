### Drug-drug interaction


## Problem
In this example, we attempt to infer unknown DDIs from a network of
multiple drug-based similarities and known interactions. 

## Dataset
Each dataset below  contain seven drug–drug similarities. Four of these
similarity measures are drug-based: Chemical-based, Ligand-based,
Side-effect-based and Annotation-based. Three similarities are be-
tween drug targets and computed by aggregating over known targets
for the drugs: Sequence-based, PPI network-based, and Gene
Ontology-based. 

- general-interactions: This dataset came from DrugBank version 4.3 for a total of 4293 interactiosn across 315 drugs.  There is also a file DrugBankIDs, which maps integer IDs to DrugIDs
- crd-interactions: This dataset came from DrugBank and Drugs.com for a total of 10,106 CRD interactions across 807 drugs. These IDs are anonymized (so there is no DrugBankID mapping)
- ncrd-interactions: This dataset came from DrugBank and Drugs.com for a total of 45,737 NCRD interactions across 807 drugs. These IDs are also anonymized. 

## Experimental Setup
The best threshold chosen for AUC evaluations is 0.4. 

## Origin
https://linqs.org/publications/#id:sridhar-bio16

@article{sridhar2016probabilistic,
  title={A probabilistic approach for collective similarity-based drug--drug interaction prediction},
  author={Sridhar, Dhanya and Fakhraei, Shobeir and Getoor, Lise},
  journal={Bioinformatics},
  volume={32},
  number={20},
  pages={3175--3182},
  year={2016},
  publisher={Oxford Univ Press}
}

## Keywords

 - `cli`
 - `evaluation`
 - `inference`
