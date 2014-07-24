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
package tbd.examples.list

import scala.collection.{GenIterable, GenMap, Seq}
import scala.collection.mutable.Map

import tbd.{Adjustable, Context, ListConf, Mutator}
import tbd.mod.{AdjustableList, Mod}

object SortAlgorithm {
  def predicate(a: (Int, Int), b: (Int, Int)): Boolean = {
    a._2 < b._2
  }
}

class SortAlgorithm(_conf: Map[String, _], _listConf: ListConf)
    extends Algorithm[Int, AdjustableList[Int, Int]](_conf, _listConf) {
  val input = mutator.createList[Int, Int](listConf)

  data = new IntData(input, count, mutations)

  def runNaive(input: GenIterable[Int]) = {
     input.toBuffer.sortWith((one, two) => SortAlgorithm.predicate((0, one), (2, two)))
  }

  def checkOutput(
      input: Map[Int, Int],
      output: AdjustableList[Int, Int]) = {
    val sortedOutput = output.toBuffer

    val answer = runNaive(input.values)

    sortedOutput == answer.toBuffer
  }

  def run(implicit c: Context): AdjustableList[Int, Int] = {
    val pages = input.getAdjustableList()

    pages.sort(SortAlgorithm.predicate)
  }
}

class ChunkSortAlgorithm(_conf: Map[String, _], _listConf: ListConf)
    extends Algorithm[Int, Mod[(Int, Vector[(Int, Int)])]](_conf, _listConf) {
  val input = mutator.createChunkList[Int, Int](listConf)

  data = new IntData(input, count, mutations)

  def runNaive(input: GenIterable[Int]) = {
     input.toBuffer.sortWith((one, two) => SortAlgorithm.predicate((1, one), (2, two)))
  }

  def checkOutput(
      input: Map[Int, Int],
      output: Mod[(Int, Vector[(Int, Int)])]) = {
    val answer = runNaive(input.values)

    output.read()._2.map(_._2) == answer.toBuffer
  }

  def run(implicit c: Context): Mod[(Int, Vector[(Int, Int)])] = {
    val list = input.getChunkList()

    list.chunkSort(SortAlgorithm.predicate(_, _))
  }
}
