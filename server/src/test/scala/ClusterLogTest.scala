import java.io.{BufferedReader, File, FileInputStream, InputStreamReader}

import cn.pandadb.server.{DataLogDetail, JsonDataLogRW}
import org.junit.{Assert, BeforeClass, Test}
import ClusterLogTest.logFile
/**
  * @Author: Airzihao
  * @Description:
  * @Date: Created at 19:21 2019/12/1
  * @Modified By:
  */
object ClusterLogTest {

  val testdataPath: String = "./src/test/testdata/";
  val logFilePath: String = "./src/test/testdata/datalog.json"
  val logFile = new File(logFilePath)
  @BeforeClass
  def prepareLogFile(): Unit = {
    if (logFile.exists()) {
      logFile.delete()
      logFile.createNewFile()
    } else {
      new File(testdataPath).mkdirs()
      logFile.createNewFile()
    }
  }
}

class ClusterLogTest {

  val expectedLogArray1: Array[String] = Array("Match(n2), return n2;")
  val expectedLogArray2: Array[String] = Array("Match(n2), return n2;", "Match(n3), return n3;")

  @Test
  def test1(): Unit = {
    val jsonDataLogRW = JsonDataLogRW.open(logFile)
    Assert.assertEquals(0, logFile.length())
    jsonDataLogRW.write(DataLogDetail(1, "Match(n1), return n1;"))
    jsonDataLogRW.write(DataLogDetail(2, "Match(n2), return n2;"))
    val _bf = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)))
    Assert.assertEquals(s"""{"versionNum":${1},"command":"Match(n1), return n1;"}""", _bf.readLine())
    Assert.assertEquals(s"""{"versionNum":${2},"command":"Match(n2), return n2;"}""", _bf.readLine())
  }

  @Test
  def test2(): Unit = {
    val jsonDataLogRW = JsonDataLogRW.open(logFile)
    val commandList = jsonDataLogRW.consume(logItem => logItem.command, 1)
    Assert.assertEquals(expectedLogArray1.head, commandList.toList.head)
  }

  @Test
  def test3(): Unit = {
    val jsonDataLog = JsonDataLogRW.open(logFile)
    jsonDataLog.write(DataLogDetail(3, "Match(n3), return n3;"))
    val commandList = jsonDataLog.consume(logItem => logItem.command, 1)
    val commandArr = commandList.toArray
    Assert.assertEquals(expectedLogArray2(0), commandArr(0))
    Assert.assertEquals(expectedLogArray2(1), commandArr(1))
  }
}