package com.kunyan

import java.io._
import java.net.{HttpURLConnection, SocketTimeoutException, URL, URLConnection}
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import kunyan.com.MySendMail
import org.apache.commons.codec.binary.Hex
import org.json.{JSONException, JSONObject}
import sun.misc.BASE64Decoder

import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._

/**
 * Created by lcm on 2016/8/15.
 * 此类用来从上海KV数据库接收
 * 上海离线的股票搜索查看数据
 */
object ShangHaiMRKVDataDown {


  def main(args: Array[String]) {

    val token = getToken
    val curDate = getDate(args(0))
    val yearToHour = args(0)
    println(yearToHour)
    val pool = Executors.newFixedThreadPool(60)
    var dataListBuf = new ListBuffer[String]

    for (minInt <- 0 to 59) {

      val run = new Runnable() {
        @Override
        def run() {
          var minute = ""

          if (minInt < 10) {
            minute = "0" + minInt
          } else {
            minute = "" + minInt
          }

          val yearToMinute = yearToHour + minute
          val data = getOneMinuteData(yearToMinute, token)
          dataListBuf = dataListBuf ++: data

        }
      }

      pool.execute(run)
    }

    pool.shutdown()

    while (!pool.isTerminated) {
      Thread.sleep(5000)
    }

    val dirPath = args(1) + "/" + curDate
    val dir = new File(dirPath)
    if(!dir.exists()){
      dir.mkdirs()
    }

    val outPath = dirPath+"/" + "shdx_" + yearToHour
    val write = new PrintWriter(outPath)
    val dataList = dataListBuf.toList

    for (data <- dataList) {
      write.write(data + "\n")
    }

    write.close()

    val dataFile = new File(outPath)

    val sdf = new SimpleDateFormat("yyyy年MM月dd日 E HH时 mm分 ss秒 S毫秒")

    if (dataFile.exists() && dataFile.isFile) {

      if (dataFile.length() < 500000) {

        val size = dataFile.length() / 1024
        MySendMail.sendMail("离线A股热度接收异常", "接收到< " + yearToHour + " >的数据文件大小为："
          + size + " KB" + "<br>" + sdf.format(new Date()))
      }

    } else {

      MySendMail.sendMail("离线A股热度接收异常", "没有数据文件")
    }

  }

  /**
   * 获取一分钟数据
   * @param yearToMinute 字符串年月日时分 如200808080808:2008年8月8日8点8分
   * @param token 请求参数
   * @return 数据集
   */
  def getOneMinuteData(yearToMinute: String, token: String): ListBuffer[String] = {

    val data = new ListBuffer[String]
    var second = ""

    for (secInt <- 0 to 59) {

      if (secInt < 10) {
        second = "0" + secInt
      } else {
        second = "" + secInt
      }

      breakable {

        var index = 0
        var empty = 0

        while (index < 2500) {

          index = index + 1
          val key = yearToMinute + second + "_" + index
          val aData = getAData(key, token)

          //println(aData)
          if (aData != "") {
            data.+=(aData)
            empty = 0
          } else {
            empty = empty + 1
          }

          if (empty > 3) {
            break()
          }
        }
      }
    }

    data
  }

  /**
   * 从KV表取一条数据
   * @param key 从KV取数据的key
   * @param token 发送http请求所需的参数
   * @return 取到的数据字符串
   */
  def getAData(key: String, token: String): String = {

    val url = "kv/getValueByKey?token=" + token + "&table=" + Parameter.KV_TABLE_NAME + "&key=" + key
//        println(url)
    var aData = ""

    var back = doGet(url)

    while (back == "time out") {
      back = doGet(url)
    }

    if (back != "") {

      try {

        val jsonOBJ = new JSONObject(back)
        val result = jsonOBJ.getString("result")

        if (result != "null") {

          val value = new JSONObject(result).getString("value")
          aData = new String((new BASE64Decoder).decodeBuffer(value))

        }

      } catch {

        case jSONException: JSONException =>
          jSONException.printStackTrace()

      }
    }

    aData
  }

  //获取日期
  def getDate(hour:String): String = {

    val year = hour.substring(0,4)
    val mon = hour.substring(4,6)
    val day = hour.substring(6,8)
    year + "-" + mon + "-" + day
  }

  /**
   * 获取电信的token的方法
   * @return 获取的token字符串
   */
  def getToken: String = {

    val apiKey: String = Parameter.API_KEY
    val userName: String = Parameter.KUN_YAN_USER_NAME
    val password: String = Parameter.KUN_YAN_PASSWORD

    val getToken: String = "getToken?apiKey=" + apiKey + "&" + "sign=" + sign(md5Encode(password), userName + apiKey)
    //    println(getToken)

    var token = ""
    try {

      token = doGet(getToken)

      while (token == "time out") {
        token = doGet(getToken)
      }

      val jbToken: JSONObject = new JSONObject(token)
      token = jbToken.getString("result")

    } catch {

      case exception: Exception =>
        exception.printStackTrace()

    }

    token
  }

  /**
   * 获取http请求时所需的签名
   *
   * @param secretKey 经过md5加密处理的秘钥
   * @param data      用户名称和apiKey
   * @return 签名字符串
   */
  def sign(secretKey: String, data: String): String = {

    val signingKey: SecretKeySpec = new SecretKeySpec(secretKey.getBytes, Parameter.HMAC_SHA1_ALGORITHM)
    val mac: Mac = Mac.getInstance(Parameter.HMAC_SHA1_ALGORITHM)
    mac.init(signingKey)
    val rawHmac: Array[Byte] = mac.doFinal(data.getBytes)

    Hex.encodeHexString(rawHmac)

  }

  /**
   * 对字符串做md5编码
   *
   * @param str 需要md5编码的字符串
   * @return 做了md5编码之后的字符串
   */
  private def md5Encode(str: String): String = {

    var md5: MessageDigest = null

    try {

      md5 = MessageDigest.getInstance("MD5")

    } catch {

      case ioException: Exception =>
        ioException.printStackTrace()
        return ""

    }

    val byteArray: Array[Byte] = str.getBytes("UTF-8")
    val md5Bytes: Array[Byte] = md5.digest(byteArray)
    val hexValue: StringBuilder = new StringBuilder

    for (md5Byte <- md5Bytes) {

      val `val`: Int = md5Byte.toInt & 0xff

      if (`val` < 16) {
        hexValue.append("0")
      }

      hexValue.append(Integer.toHexString(`val`))
    }

    hexValue.toString()
  }

  /**
   * 此方法为http请求
   *
   * @param url 指定所需的请求
   * @return 请求到的数据
   */
  def doGet(url: String): String = {

    val localURL: URL = new URL(Parameter.SH_KV_URL_HEAD + url)
//            println(Parameter.SH_KV_URL_HEAD + url)
    val connection: URLConnection = localURL.openConnection
    val httpURLConnection: HttpURLConnection = connection.asInstanceOf[HttpURLConnection]

    httpURLConnection.setConnectTimeout(10000)
    httpURLConnection.setReadTimeout(10000)
    httpURLConnection.setRequestProperty("Connection", "keep-alive")
    httpURLConnection.setRequestProperty("Accept-Charset", "utf-8")
    httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

    var inputStream: InputStream = null
    var inputStreamReader: InputStreamReader = null
    var reader: BufferedReader = null
    val resultBuilder: StringBuilder = new StringBuilder
    var tempLine: String = null

    try {

      if (httpURLConnection.getResponseCode == 200) {

        inputStream = httpURLConnection.getInputStream
        inputStreamReader = new InputStreamReader(inputStream)
        reader = new BufferedReader(inputStreamReader)

        while ( {
          tempLine = reader.readLine
          tempLine
        } != null) {

          resultBuilder.append(tempLine)

        }
      }

    } catch {

      case timeOut: SocketTimeoutException =>
        println("time out")
        resultBuilder.append("time out")
      case exception: Exception =>
        exception.printStackTrace()

    } finally {

      if (reader != null) {
        reader.close()
      }

      if (inputStreamReader != null) {
        inputStreamReader.close()
      }

      if (inputStream != null) {
        inputStream.close()
      }
      if(httpURLConnection != null){
        httpURLConnection.disconnect()
      }

    }

    resultBuilder.toString()
  }
}
