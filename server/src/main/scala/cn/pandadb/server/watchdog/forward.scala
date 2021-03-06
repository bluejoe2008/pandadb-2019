package cn.pandadb.server.watchdog

import java.time.Duration
import java.{lang, util}

import cn.pandadb.server.MainServerContext
import org.neo4j.bolt.runtime.BoltResult.Visitor
import org.neo4j.bolt.runtime.{BoltResult, StatementMetadata, StatementProcessor, TransactionStateMachineSPI}
import org.neo4j.bolt.v1.runtime.bookmarking.Bookmark
import org.neo4j.cypher.result.QueryResult
import org.neo4j.driver._
import org.neo4j.driver.internal.util.Clock
import org.neo4j.driver.internal.value._
import org.neo4j.function.{ThrowingBiConsumer, ThrowingConsumer}
import org.neo4j.graphdb.{Direction, GraphDatabaseService, Label, Node, Relationship, RelationshipType}
import org.neo4j.kernel.impl.util.ValueUtils
import org.neo4j.values.AnyValue
import org.neo4j.values.virtual.MapValue

import scala.collection.{JavaConversions, mutable}

/**
  * Created by bluejoe on 2019/11/4.
  */
object ClientPool {
  lazy val session = {
    val pandaString = s"panda://${MainServerContext.zkServerAddressStr}/db"
    val driver = GraphDatabase.driver(pandaString, AuthTokens.basic("", ""));
    driver.session();
  }
}

class ForwardedStatementProcessor(source: StatementProcessor, spi: TransactionStateMachineSPI) extends StatementProcessor {
  val session = ClientPool.session
  val clock = Clock.SYSTEM;
  var _currentTransaction: Transaction = null;
  var _currentStatementResult: StatementResult = null;

  override def markCurrentTransactionForTermination(): Unit = source.markCurrentTransactionForTermination()

  override def commitTransaction(): Bookmark = source.commitTransaction()

  override def run(statement: String, params: MapValue): StatementMetadata = source.run(statement, params)

  override def run(statement: String, params: MapValue, bookmark: Bookmark,
                   txTimeout: Duration, txMetaData: util.Map[String, AnyRef]): StatementMetadata = {

    // param transformation, contribute by codeBabyLin
    val paramMap = new mutable.HashMap[String, AnyRef]()
    val myConsumer = new ThrowingBiConsumer[String, AnyValue, Exception]() {
      override def accept(var1: String, var2: AnyValue): Unit = {
        val key = var1
        val value = ValueUtils.asValue(var2).asObject()
        paramMap.update(key, value)
      }
    }
    params.foreach(myConsumer)

    val mapTrans = JavaConversions.mapAsJavaMap(paramMap)
    //extract metadata from _currentStatementResult.
    _currentTransaction = session.beginTransaction();
    _currentStatementResult = _currentTransaction.run(statement, mapTrans)
    new MyStatementMetadata(_currentStatementResult)
  }

  override def streamResult(resultConsumer: ThrowingConsumer[BoltResult, Exception]): Bookmark = {
    resultConsumer.accept(new MyBoltResult(_currentStatementResult));
    //return bookmark
    new Bookmark(spi.newestEncounteredTxId());
  }

  class MyBoltResult(result: StatementResult) extends BoltResult {
    override def fieldNames(): Array[String] = JavaConversions.collectionAsScalaIterable(result.keys()).toArray

    override def accept(visitor: Visitor): Unit = {
      val start = clock.millis();

      val it = result.stream().iterator();
      while (it.hasNext) {
        val record = it.next();
        visitor.visit(new MyRecord(record));
      }

      visitor.addMetadata("result_consumed_after", org.neo4j.values.storable.Values.longValue(clock.millis() - start));
      //query_type?
    }

    override def close(): Unit = _currentTransaction.close()
  }

  class MyRecord(record: Record) extends QueryResult.Record {
    override def fields(): Array[AnyValue] = {
      JavaConversions.collectionAsScalaIterable(record.values()).map {
        value: Value =>
          value match {
            //TODO: check different types of XxxValue, unpack and use ValueUtils to transform
            case v: NodeValue => ValueUtils.asAnyValue(new MyDriverNodeToDbNode(v))
            case v: org.neo4j.driver.internal.value.MapValue => ValueUtils.asAnyValue(v.asMap())
            case v: ListValue => ValueUtils.asAnyValue(v.asList())
            case v: IntegerValue => ValueUtils.asAnyValue(v.asLong())
            case v: FloatValue => ValueUtils.asAnyValue(v.asDouble())
            case v: BooleanValue => ValueUtils.asAnyValue(v.asBoolean())
            case v: DateValue => ValueUtils.asAnyValue(v.asLocalDate())
            case v: DateTimeValue => ValueUtils.asAnyValue(v.asLocalDateTime())
            case v: StringValue => ValueUtils.asAnyValue(v.asString())
            case _ =>
              ValueUtils.asAnyValue(value.asObject())
          }
      }.toArray
    }
  }

  class MyStatementMetadata(result: StatementResult) extends StatementMetadata {
    override def fieldNames(): Array[String] = JavaConversions.collectionAsScalaIterable(result.keys()).toArray
  }

  override def hasOpenStatement: Boolean = source.hasOpenStatement

  override def rollbackTransaction(): Unit = source.rollbackTransaction()

  override def hasTransaction: Boolean = source.hasTransaction

  override def reset(): Unit = source.reset()

  override def validateTransaction(): Unit = source.validateTransaction()

  override def beginTransaction(bookmark: Bookmark): Unit = source.beginTransaction(bookmark)

  override def beginTransaction(bookmark: Bookmark, txTimeout: Duration,
                                txMetadata: util.Map[String, AnyRef]): Unit =
    source.beginTransaction(bookmark, txTimeout, txMetadata)

  // class for driver node type transform to DB's node type
  class MyDriverNodeToDbNode(driverNode: NodeValue) extends Node {

    override def getId: Long = driverNode.asEntity().id()

    override def delete(): Unit = {}

    override def getRelationships: lang.Iterable[Relationship] = JavaConversions.asJavaIterable(None)

    override def hasRelationship: Boolean = false

    override def getRelationships(types: RelationshipType*): lang.Iterable[Relationship] = JavaConversions.asJavaIterable(None)

    override def getRelationships(direction: Direction, types: RelationshipType*): lang.Iterable[Relationship] = JavaConversions.asJavaIterable(None)

    override def hasRelationship(types: RelationshipType*): Boolean = false

    override def hasRelationship(direction: Direction, types: RelationshipType*): Boolean = false

    override def getRelationships(dir: Direction): lang.Iterable[Relationship] = JavaConversions.asJavaIterable(None)

    override def hasRelationship(dir: Direction): Boolean = false

    override def getRelationships(`type`: RelationshipType, dir: Direction): lang.Iterable[Relationship] = JavaConversions.asJavaIterable(None)

    override def hasRelationship(`type`: RelationshipType, dir: Direction): Boolean = false

    override def getSingleRelationship(`type`: RelationshipType, dir: Direction): Relationship = null

    override def createRelationshipTo(otherNode: Node, `type`: RelationshipType): Relationship = null

    override def getRelationshipTypes: lang.Iterable[RelationshipType] = JavaConversions.asJavaIterable(None)

    override def getDegree: Int = 0

    override def getDegree(`type`: RelationshipType): Int = 0

    override def getDegree(direction: Direction): Int = 0

    override def getDegree(`type`: RelationshipType, direction: Direction): Int = 0

    override def addLabel(label: Label): Unit = {}

    override def removeLabel(label: Label): Unit = {}

    override def hasLabel(label: Label): Boolean = true

    override def getLabels: lang.Iterable[Label] = {
      val itor = JavaConversions.asScalaIterator(driverNode.asNode().labels().iterator())
      val iter = itor.map(label => Label.label(label))
      JavaConversions.asJavaIterable(iter.toIterable)
    }

    override def getGraphDatabase: GraphDatabaseService = null

    override def hasProperty(key: String): Boolean = driverNode.get(key) != null

    override def getProperty(key: String): AnyRef = driverNode.get(key).asObject()

    override def getProperty(key: String, defaultValue: Any): AnyRef = driverNode.get(key, defaultValue)

    override def setProperty(key: String, value: Any): Unit = {}

    override def removeProperty(key: String): AnyRef = null

    override def getPropertyKeys: lang.Iterable[String] = JavaConversions.asJavaIterable(None)

    override def getProperties(keys: String*): util.Map[String, AnyRef] = JavaConversions.mapAsJavaMap(Map())

    override def getAllProperties: util.Map[String, AnyRef] = driverNode.asEntity().asMap()
  }

}