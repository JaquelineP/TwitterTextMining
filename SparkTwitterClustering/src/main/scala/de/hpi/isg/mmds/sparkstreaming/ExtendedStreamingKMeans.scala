package de.hpi.isg.mmds.sparkstreaming

import org.apache.spark.mllib.clustering.{KMeans, StreamingKMeans, StreamingKMeansModel}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.streaming.dstream.DStream

class ExtendedStreamingKMeans extends StreamingKMeans {

  private var ids = Array[Int]()
  private var maxId = -1
  private var addThreshold = 2.2
  private var mergeThreshold = 25.0

  def setAddThreshold(threshold: Double): this.type = {
    this.addThreshold = threshold
    this
  }

  def setMergeThreshold(threshold: Double): this.type = {
    this.mergeThreshold = threshold
    this
  }

  private def updateModel(newCenters: Array[Vector], newWeights: Array[Double]): this.type = {
    model = new StreamingKMeansModel(newCenters, newWeights)
    setK(newCenters.length)
    this
  }

  def addCentroids(centers: Array[Vector], weights: Array[Double]): this.type = {
    val newCenters = model.clusterCenters ++ centers
    val newWeights = model.clusterWeights ++ weights
    ids ++= Array.range(maxId + 1, newCenters.length - model.clusterCenters.length + maxId + 1)
    maxId += newCenters.length - model.clusterCenters.length
    updateModel(newCenters, newWeights)
  }

  def removeCentroids(centers: Array[Int], addWeights: Array[(Int, Double)] = Array.empty): this.type = {
    val updatedWeights = model.clusterWeights
    for ((id, weight) <- addWeights) { updatedWeights.update(id, updatedWeights(id) + weight) }

    // filter out centers to be removed
    var newData = (model.clusterCenters, updatedWeights, ids)
      .zipped
      .toArray
      .zipWithIndex
      .filter { case (data, index) => !centers.contains(index) }
      .map { case (data, index) => data }

    // there should always be at least one cluster
    if (newData.isEmpty) {
      maxId += 1
      newData = Array((Vectors.dense(Array.fill(model.clusterCenters(0).size)(-1.0)), 0.0, maxId))
    }

    val newCenters = newData.map { case (center, weight, id) => center }
    val newWeights = newData.map { case (center, weight, id) => weight }
    ids = newData.map { case (center, weight, id) => id }

    updateModel(newCenters, newWeights)
  }

  private def addClusters(data: DStream[Vector]): Unit = {
    data.foreachRDD { rdd =>
      // gather all tweets that need a new centroid
      val distantVectors = rdd.filter { (vector) =>
        val center = model.predict(vector)
        VectorUtils.scaledDist(vector, model.clusterCenters(center)) >= addThreshold
      }.collect()

      // determine which centroids to add (adding one for every candidate, unless one of the new ones already is good enough)
      var newCenters = Array[Vector]()
      distantVectors.foreach { vector =>
        val sum = KMeans.vectorSum(vector)
        var addNewCenter = true
        newCenters.foreach(center => addNewCenter &&= VectorUtils.scaledDist(vector, center, sum) >= addThreshold)
        if (addNewCenter) newCenters +:= vector.toDense
      }
      addCentroids(newCenters, Array.fill[Double](newCenters.length)(0.0))
    }
  }

  private def mergeClusters(data: DStream[Vector]): Unit = {
    data.foreachRDD { rdd =>
      // generate pairs of clusters to be merged
      val centerRDD = rdd.sparkContext.parallelize(model.clusterCenters.zipWithIndex)
      val clustersToMerge = centerRDD
        .cartesian(centerRDD)
        .filter { case ((vec1, index1), (vec2, index2)) => (index1 < index2) && (Vectors.sqdist(vec1, vec2) < mergeThreshold) }
        .map { case ((vec1, index1), (vec2, index2)) => (index1, index2) }
        .collect()

      // determine which clusters to delete; always delete smallest unless one of the two is already selected for deletion
      var clustersToDelete = Array[Int]()
      var addWeights = Array[(Int, Double)]()
      clustersToMerge.foreach { case (index1, index2) =>
        if (!clustersToDelete.contains(index1) && !clustersToDelete.contains(index2)) {
          if (model.clusterWeights(index1) > model.clusterWeights(index2)) {
            clustersToDelete +:= index2
            addWeights +:= (index1, model.clusterWeights(index2))
          } else {
            clustersToDelete +:= index1
            addWeights +:= (index2, model.clusterWeights(index1))
          }
        }
      }
      removeCentroids(clustersToDelete, addWeights)
    }
  }

  private def deleteOldClusters(data: DStream[Vector]): Unit = {
    data.foreachRDD { rdd =>
      // find clusters that have a low enough weight to be deleted
      val clustersToDelete = rdd.sparkContext.parallelize(model.clusterWeights)
        .zipWithIndex
        .filter { case (weight, index) => weight < 1.0 }
        .map { case (weight, index) => index.toInt }
        .collect()
      removeCentroids(clustersToDelete)
    }
  }

  override def trainOn(data: DStream[Vector]) {
    addClusters(data)
    super.trainOn(data)
    mergeClusters(data)
    deleteOldClusters(data)
  }

  def fixedId(id: Int): Int = {
    ids(id)
  }

  // overridden because we need to maintain maxId
  override def setRandomCenters(dim: Int, weight: Double, seed: Long = -1): this.type = {
    ids = Array.range(maxId + 1, k + maxId + 1)
    maxId += k
    if (seed == -1)
      super.setRandomCenters(dim, weight)
    else
      super.setRandomCenters(dim, weight, seed)
  }

  // overridden because we need to maintain maxId
  override def setInitialCenters(centers: Array[Vector], weights: Array[Double]): this.type = {
    ids = Array.range(maxId + 1, centers.length + maxId + 1)
    maxId += centers.length
    super.setInitialCenters(centers, weights)
  }

}
