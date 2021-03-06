package cn.pandadb.driver

import cn.pandadb.network.{ClusterClient, NodeAddress}
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase}

import scala.collection.mutable

/**
 * @Author: codeBabyLin
 * @Description:
 * @Date: Created at 20:50 2019/11/27
 * @Modified By:
 */
trait Strategy{

}
case class RANDOM_PICK() extends Strategy{

}
case class ROBIN_ROUND() extends Strategy{

}
case class WORK_TIME_PICK() extends Strategy{

}
case class DEFAULT_PICK() extends Strategy{

}

case class EASY_ROUND() extends Strategy{

}

object SelectNode {

  val RONDOM_POLICY = 0
  val _POLICY = 1
  val robinArray = mutable.Map[NodeAddress, Long]()
  private var policy: Strategy = null
  private var index = 0

  private def getWriteNode(clusterOperator: ClusterClient): NodeAddress = {
    clusterOperator.getWriteMasterNode()
    //policyDefault
  }

  private def policyRandom(clusterOperator: ClusterClient): NodeAddress = {

    val nodeLists = clusterOperator.getAllNodes().toList
    val index = (new util.Random).nextInt(nodeLists.length)
    nodeLists(index)

  }

  private def policyRobinRound(clusterOperator: ClusterClient): NodeAddress = {
    if (robinArray.size == 0) {
      clusterOperator.getAllNodes().foreach(node => robinArray += node -> 0)
    }
    val list = robinArray.toList
    val sorted = list.sortBy(node => node._2)
    val head = sorted.head
    val node = robinArray.toList.sortBy(u => u._2).head._1
    robinArray(node) += 1
    node
  }

  private def easyRound(clusterOperator: ClusterClient): NodeAddress = {
    val nodeLists = clusterOperator.getAllNodes().toList
    if (this.index>=nodeLists.length) {
      this.index = 0
    }
    val node = nodeLists(this.index)
    this.index += 1
    node

  }

  def testRobinRound(): NodeAddress = {

    val list = robinArray.toList
    val sorted = list.sortBy(node => node._2)
    val head = sorted.head._1
    //val node = robinArray.toList.sortBy(u => u._2).head._1
    robinArray(head) += 1
    head

  }

  private def policyDefault(): NodeAddress = {
    val hos = "10.0.86.179"
    val por = 7687
    new NodeAddress(hos, por)
  }
  private def getReadNode(clusterOperator: ClusterClient, strategy: Strategy): NodeAddress = {
    strategy match {
      case RANDOM_PICK() => policyRandom(clusterOperator)
      case DEFAULT_PICK() => policyDefault
      case ROBIN_ROUND() => policyRobinRound(clusterOperator)
      case EASY_ROUND() => easyRound(clusterOperator)
      case _ => policyDefault

    }
  }
  private def getNode(isWriteStatement: Boolean, clusterOperator: ClusterClient, strategy: Strategy): NodeAddress = {
    if (isWriteStatement) getWriteNode(clusterOperator) else getReadNode(clusterOperator, strategy)
  }
  def setPolicy(strategy: Strategy): Unit = {
    this.policy = strategy
  }
  def getPolicy(): String = {
    this.policy match {
      case RANDOM_PICK() => "RANDOM_PICK"
      case DEFAULT_PICK() => "DEFAULT_PICK"
      case ROBIN_ROUND() => "ROBIN_ROUND"
      case EASY_ROUND() => "EASY_ROUND"
      case _ => "policyDefault-RANDOM_PICK"
    }
  }
  def getDriver(isWriteStatement: Boolean, clusterOperator: ClusterClient): Driver = {
    if (this.policy ==null) this.policy = new RANDOM_PICK
    getDriver(isWriteStatement, clusterOperator, this.policy)
  }
  def getDriver(isWriteStatement: Boolean, clusterOperator: ClusterClient, strategy: Strategy): Driver = {
    //val node = getNode(isWriteStatement, clusterOperator, new DEFAULT_PICK)
    val node = getNode(isWriteStatement, clusterOperator, strategy)
    val host = node.host
    val port = node.port
    val uri = s"bolt://$host:$port"
    GraphDatabase.driver(uri, AuthTokens.basic("", ""))
  }
}
