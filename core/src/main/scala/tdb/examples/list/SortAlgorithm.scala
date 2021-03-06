/**
 * Copyright (C) 2013 Carnegie Mellon University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tdb.examples.list

import scala.collection.{GenIterable, GenMap, Seq}
import scala.collection.immutable.TreeSet
import scala.collection.mutable.Map

import tdb._
import tdb.list._
import tdb.util._

class QuickSortAdjust(list: AdjustableList[Int, Int])
  extends Adjustable[AdjustableList[Int, Int]] {
  def run(implicit c: Context) = {
    list.quicksort ((pair1: (Int, Int), pair2:(Int, Int)) => pair1._1 - pair2._1)
  }
}

class QuickSortAlgorithm(_conf: AlgorithmConf)
    extends Algorithm[Int, AdjustableList[Int, Int]](_conf) {
  val input = mutator.createList[Int, Int](conf.listConf)

  val adjust = new QuickSortAdjust(input.getAdjustableList())

  val data = new IntData(input, conf.runs, conf.count, conf.mutations)

  def generateNaive() {
    data.generate()
  }

  def runNaive() {
    naiveHelper(data.table)
  }

  private def naiveHelper(input: Map[Int, Int]) = {
    input.toBuffer.sortWith(_._1 < _._1)
  }

  def checkOutput(output: AdjustableList[Int, Int]) = {
    val sortedOutput = output.toBuffer(mutator)
    val answer = naiveHelper(data.table)

    //println(sortedOutput)
    //println(answer.toBuffer)

    sortedOutput == answer.toBuffer
  }
}

class MergeSortAdjust(list: AdjustableList[Int, Int])
  extends Adjustable[AdjustableList[Int, Int]] {
  def run(implicit c: Context) = {
    list.mergesort((pair1: (Int, Int), pair2:(Int, Int)) => pair1._1 - pair2._1)
  }
}

class MergeSortAlgorithm(_conf: AlgorithmConf)
    extends Algorithm[Int, AdjustableList[Int, Int]](_conf) {
  val input = mutator.createList[Int, Int](conf.listConf)

  val adjust = new MergeSortAdjust(input.getAdjustableList())

  val data = new IntData(input, conf.runs, conf.count, conf.mutations)

  def generateNaive() {
    data.generate()
  }

  def runNaive() {
    naiveHelper(data.table)
  }

  private def naiveHelper(input: Map[Int, Int]) = {
    input.toBuffer.sortWith(_._1 < _._1)
  }

  def checkOutput(output: AdjustableList[Int, Int]) = {
    val sortedOutput = output.toBuffer(mutator)
    val answer = naiveHelper(data.table)

    //println(sortedOutput)
    //println(answer.toBuffer)

    sortedOutput == answer.toBuffer
  }
}
