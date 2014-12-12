#!/bin/sh
exec scala "$0" "$@"
!#

import java.io._
import scala.xml.{Utility, XML}

object Convert {
  def main(args: Array[String]) {
    val elems = XML.loadFile("wiki.xml")
    val output = new BufferedWriter(new OutputStreamWriter(
      new FileOutputStream("wiki2.xml"), "utf-8"))

    output.write("<elems>")
    (elems \\ "elem").map(elem => {
      (elem \\ "key").map(key => {
        (elem \\ "value").map(value => {
	  output.write("<elem><key>" + Utility.escape(key.text) + "</key><value>" +
              Utility.escape(value.text) + "</value></elem>")
        })
      })
    })
    output.write("</elems>")
    output.close()
  }
}
