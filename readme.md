# I am
**Name :** Sarthak Agarwal

**UIN :** 679962604

**UIC email :** sagarw35@uic.edu

# Introduction
Many real-world scenarios can be modeled as large graphs evolving over time, such as social media connections. In general, tracking each change occurring in the graph can be challenging. It is more feasible to have snapshots of the graph at different timestamps. Although these snapshots would have high similarity, slight differences would exist due to actions taken between these timestamps. To understand the changes made during those periods, one would need to compare the two graphs. The graph isomorphism problem is NP-complete; however, even approximate predictions could provide valuable insights. In this project, completed as a homework assignment for CS-441 at the **University of Illinois Chicago**, we address this problem and predict changes using a Hadoop MapReduce-based program. To simulate two similar graphs, we utilize [Professor Grechanik's](https://www.cs.uic.edu/~drmark/) marvelous [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) program, which generates an original graph and a perturbed one, with controllable degrees of perturbation. The [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) program also records the changes made in the original graph and stores them in a YAML file. We use this golden set of changes to evaluate the performance of our MapReduce-based program in predicting those changes.

# Program Working

## Inputs
The program requires seven input locations to function correctly:

1. Path to the original graph
2. Path to the perturbed graph
3. Directory for creating graph shards (should exist already for aws env)
4. Directory for the Map-Reduce job output (should not exist already)
5. Path to the ground-truth YAML file
6. Directory for the final analysis output (should exist already for aws env)
7. An additional input that specifies the running environment, accepting either 'aws' or 'local'

Inputs can be provided via command line arguments, which will take precedence over the config file.

## Sharding
The program begins by loading the original and perturbed graphs generated by [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim). It then creates `numOfGraphDivisions` non-disjoint pieces from each graph. Nodes in the graphs are divided into disjoint divisions based on __DFS__ to ensure connected nodes are placed in the same node bucket, termed as `subjectNodes`. Each subgraph consists of these `subjectNodes` and their neighbors within a range determined by the `shardingExtraHops` parameter. The combination of original and perturbed graph pieces is stored as **JSON files** in the `shardDir`.

## Map-Reduce
- **Mapper:** Processes the JSON files, each containing a piece of the original and perturbed graph. It computes [SimRank](https://en.wikipedia.org/wiki/SimRank) on these pieces and outputs the best node match and a similarity score (`simScore`). The mapper then broadcasts key-value pairs for each **subjectNodes** in both subgraphs. The key is formatted as `{label}_{id}`, and the values as `{corresponding_node_id_from_other_graph}_{sim_score}`, where the label is either "original" or "perturbed".

- **Reducer:** Takes as input the key-value set pair with the maximum simScore. From the value set, it first identifies the element (in the form of {corresponding_node_id_from_other_graph}_{sim_score}) corresponding to the maximum sim_score. If the sim_score is less than 1, meaning no perfect match is found, it outputs the pair with the original key and the value as the element with the highest sim_score. It does not output anything in case a perfect match is found.

## Post-Processing
The program reads the Map-Reduce output line by line, extracting information about the label (original or perturbed), node id, corresponding node id from the other graph, and the max `simScore`.

- If the label is "original", the program adds a new value `(node_id, max_sim_score)` to the `modified_removed_buffer`.
- If the label is "perturbed", the node id is added to the `added_buffer`.

Next, the **K-Means clustering algorithm** categorizes the `modified_removed_buffer` elements into two clusters based on their `simScore`. The cluster with the lower simScore is identified as **removed nodes**, while the other as **modified nodes**. Nodes present in the `added_buffer` but not in the modified nodes are identified as **added nodes**.

## Scoring
The program compares the identified nodes with the golden set of changes in the ground truth YAML file (`groundTruthYAML`). It calculates ACC, BTLR, and VPR scores based on traceability links. Nodes matched between the original and perturbed graphs are considered traceability links. Correctly matched unchanged and modified nodes are counted as Accepted Traceability Links (ATL), while correctly matched removed and added nodes are counted as Discarded Traceability Links (DTL). Other parameters, such as WTL and CTL, are calculated accordingly.

The program also calculates the precision, recall, and F1-Scores for the predictions made regarding Modified, Removed, and Added nodes. All these scores, along with the ACC, BTLR, and VPR scores, are stored in a YAML file in the `analysisOutputDir`, as specified in the config. Additionally, the program creates a bar plot of these scores for enhanced visualization, which is also stored in the `analysisOutputDir`.

Sample output YAML file:
```agsl
Traceability-link based scores: :
  acc: 0.7401
  btlr: 0.0895
  vpr: 0.7797
Confusion-matrix based scores: :
  Modified Nodes:
    Precision: 0.6154
    Recall: 0.5854
    F-1 Score: 0.6
  Added Nodes:
    Precision: 0.5641
    Recall: 1.0
    F-1 Score: 0.7213
  Removed Nodes:
    Precision: 0.561
    Recall: 0.6053
    F-1 Score: 0.5823

```
## Directory structure for src folder
```
src/
├─ main/
│  ├─ resources/
│  │  └─ application.conf
│  ├─ scala/
│     ├─ AWS_utils/
│     │  └─ AWSUtils.scala
│     ├─ config_manager/
│     │  └─ ConfigManager.scala
│     ├─ load_utils/
│     │  ├─ GraphLoadUtils.scala
│     │  └─ GraphUtils.scala
│     ├─ main.scala
│     ├─ map_reduce/
│     │  ├─ MapReduce.scala
│     │  ├─ mapper/
│     │  │  ├─ SimRank.scala
│     │  │  └─ SubgraphMapper.scala
│     │  └─ reducer/
│     │     └─ SubgraphReducer.scala
│     └─ post_map_reduce/
│        ├─ ProcessMapRedOut.scala
│        └─ utils/
│           └─ Utils.scala
└─ test/
├─ resources/
│  └─ valid_test_graph.ngs
└─ scala/
└─ AllTests.scala
```
# Execution Guide

Follow the steps below to execute the program either locally or on AWS.

## Create Executable JAR

1. **Clone the Project:**  
   Obtain a copy of the source code on your local machine.

2. **Build the Project:**  
   Navigate to the project directory and execute:
   This command cleans, compiles, and packages the project into an executable JAR file.
```agsl
sbt clean compile assembly
```
3. **Locate the JAR:**  
   Find the generated JAR file inside the `target` folder.

## Local Execution

Set up hadoop, and run the program on your local machine with the following command, replacing `arg1` to `arg7` with your actual input values:

```agsl
java -jar myfile.jar arg1 arg2 arg3 arg4 arg5 arg6 arg7
```
## AWS Execution

1. **Upload to S3:**  
   Place the necessary files, including the JAR, into an S3 bucket.

2. **Create EMR Job:**  
   Initialize an EMR job equipped with Hadoop.

3. **Add Custom Step:**  
   Include a custom step in the EMR job, specifying the JAR's location in the S3 bucket and providing the seven required command-line arguments. Ensure "aws" is stated as the seventh argument to denote the AWS environment.

# Limitations

## 1. Lack of Parallelism in SimRank
Despite the extensive use of memoization to optimize performance, the current implementation does not incorporate parallelism in SimRank calculations. Implementing parallel processing could significantly expedite the Map-Reduce job execution.

## 2. Unverified and Unadjusted for Multi-Machine Clusters
The program hasn't been tested or adjusted to operate on clusters with multiple machines. While the Hadoop framework is designed to efficiently run on and leverage the power of multi-machine configurations, specific enhancements and adjustments may be necessary to optimize this program for such an environment.

# Video Demo 