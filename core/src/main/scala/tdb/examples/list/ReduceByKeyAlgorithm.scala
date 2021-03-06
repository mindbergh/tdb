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
import scala.collection.mutable.{Buffer, Map}

import tdb._
import tdb.list._
import tdb.util._

object ReduceByKeyAlgorithm {
  def mapper(pair: (Int, Int)) = {
    List(pair, (pair._1 * 2, pair._2), (pair._1 * 3, pair._2))
  }
}

class ReduceByKeyAdjust(list: AdjustableList[Int, Int])
  extends Adjustable[AdjustableList[Int, Int]] {

  def run(implicit c: Context) = {
    val mapped = list.flatMap(ReduceByKeyAlgorithm.mapper)
    mapped.reduceBy(_ + _, (pair1: (Int, Int), pair2:(Int, Int)) => pair1._1 - pair2._1)
  }
}

class ReduceByKeyAlgorithm(_conf: AlgorithmConf)
    extends Algorithm[Int, AdjustableList[Int, Int]](_conf) {
  val input = mutator.createList[Int, Int](conf.listConf)

  val data = new IntData(input, conf.runs, conf.count, conf.mutations)

  val adjust = new ReduceByKeyAdjust(input.getAdjustableList())

  def generateNaive() {
    data.generate()
  }

  def runNaive() {
    naiveHelper(data.table)
  }

  private def naiveHelper(input: Map[Int, Int]) = {
    val mapped = Buffer[(Int, Int)]()
    input.foreach(pair => mapped ++= ReduceByKeyAlgorithm.mapper(pair))

    val reduced = Map[Int, Int]()
    for ((key, value) <- mapped) {
      reduced(key) = value + reduced.getOrElse(key, 0)
    }

    reduced
  }

  def checkOutput(output: AdjustableList[Int, Int]) = {
    val sortedOutput = output.toBuffer(mutator).sortWith(_._1 < _._1)
    val answer = naiveHelper(data.table)
    val sortedAnswer = answer.toBuffer.sortWith(_._1 < _._1)

    sortedOutput == sortedAnswer
  }
}
