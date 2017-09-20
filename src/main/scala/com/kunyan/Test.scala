package com.kunyan

/**
 * Created by Administrator on 2017/6/8.
 *
 */
object Test {

  def main(args: Array[String]) {
    val str = "kv/getValueByKey?token=1f0fa90fg15ca560cef2g4a1e91f8719g9888b79aa909347c9c68634&table=kunyan_to_upload_inter_tab_mr&key=20170607140600_130"
    for(index <- 1 to 200){
      val string = ShangHaiMRKVDataDown.doGet(str)
      println(string)
    }

  }

}
