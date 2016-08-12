# **Twitter News Clustering**

The goal of this project is to create a continuous feed of relevant world news, based on tweets retrieved from the Twitter API. Due to the potentially large amount of data, the news feed is generated by a distributed system, using Apache Spark and Scala. The project was developed in the context of the seminar "Mining Massive Datasets" by Masters students ([Daniel Neuschäfer-Rube](https://github.com/dneuschaefer-rube), [Jaqueline Pollak](https://github.com/JaquelineP), [Benjamin Reißaus](https://github.com/BenReissaus)) at Hasso Plattner Institute (Potsdam, Germany). The following sections outline how to run the project, details on the algorithms can be found on the wiki pages.

## **Project structure / features**

### **Generating the news feed**

1. Build the jar using `mvn package`

2. Start the clustering with one of two modes:

    1. Using a pre-generated dump of tweets ([generate yourself](https://github.com/JaquelineP/TwitterNewsClustering/blob/master/gather-tweets/README.md), or use [this sample](https://drive.google.com/file/d/0B1M9c5rlifEmUDRwcllZU3Y5SWc/view?usp=sharing)): `spark-submit --class de.hpi.isg.mmds.sparkstreaming.Main target\SparkTwitterClustering-jar-with-dependencies.jar -input [path to twitter.dat]`

    2. Using live tweets from the API (requires config.txt ([see here](https://github.com/JaquelineP/TwitterNewsClustering/blob/master/gather-tweets/README.md) for format) in the resources folder): `spark-submit --class de.hpi.isg.mmds.sparkstreaming.Main target\SparkTwitterClustering-jar-with-dependencies.jar -source api`

3. Results are printed to console, but can also be visualized (see next section)

### **Visualization of the clustering**

![image alt text](https://raw.githubusercontent.com/JaquelineP/TwitterTextMining/master/slides/images/webapp.png)

To get an overview about the clustering results, we created a little webapp. The webpage shows for every cluster (y-axis) and every batch (x-axis) the amount of tweets, a representative tweet and some evaluation scores (e.g. silhouette). 

1. Create News Feed (see Generating the news feed)

2. To start the web  application we first need to aggregate clusterInfo from single batches:
Run `spark-submit --class de.hpi.isg.mmds.sparkstreaming.ClusterInfoAggregation target\SparkTwitterClustering-jar-with-dependencies.jar`

3. Start webapp:

    1. `cd ..\webapp`

    2. `node server.js`

4. Open browser: [http://localhost:3000/index](http://localhost:3000/index) 

5. To get a detailed info about all clusters which are in a certain cluster a certain batch, you can click on the cluster count or type: [http://localhost:3000/1663/0](http://localhost:3000/1663/0)

    3. The first number is the cluster id (e.g. 1663)

    4. The second number is the batch id starting with 0. 

## **Cluster**

* Spark version: 1.6.1

* Scala version: 2.10.5

* Java version: 1.8

* [e.g. Amazon: cluster size, Cores]

### **Run Scripts For Execution on Cluster**

We have created some scripts which help to run the clustering on a server. All scripts can be found in SparkTwitterClustering/scripts/

1. run buildAndCopyToCluster MASTER_PUBLIC_DNS

    1. This builds the Spark project using maven and then copies the jar with dependencies as well as the scripts and tweet data to the master node.

2. Connect to master e.g. via ssh

3. On master node: run bootstrap

    2. This copies the Twitter data to the HDFS and installs Java 8 and some other helpful tools (e.g. tmux, git, zsh)

4. run runOnCluster

    3. This finally executes the clustering for multiple setups (different #executers, #cores and #batchsizes)

    4. It saves the runtime in a csv file (runtime.csv)

## **Contributors**

[Daniel Neuschäfer-Rube](https://github.com/dneuschaefer-rube)

[Jaqueline Pollak](https://github.com/JaquelineP)

[Benjamin Reißaus](https://github.com/BenReissaus)
