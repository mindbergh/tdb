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
package tbd.test

import org.scalatest._

import tbd._
import tbd.list._
import tbd.table._
import tbd.TBD._

/**
 * These tests are intended for manual testing of what gets reexecuted when
 * running various algorithms, and will generally be commented out on master.
 */

class ReduceTest(input: ListInput[Int, Int])
    extends Adjustable[Mod[(Int, Int)]] {
  def run(implicit c: Context) = {
    val list = input.getAdjustableList()
    list.reduce((pair1: (Int, Int), pair2: (Int, Int)) => {
      //println("reducing " + pair1._2 + " " + pair2._2)
      (pair1._1, pair1._2 + pair2._2)
    })
  }
}

class SortTest(input: ListInput[Int, Double])
    extends Adjustable[AdjustableList[Int, Double]] {
  def run(implicit c: Context) = {
    val list = input.getAdjustableList()
    list.sort((pair1, pair2) => {
      println("      comparing " + pair1 + " " + pair2)
      pair1._2 < pair2._2
    })
  }
}

class ChunkSortTest(input: ChunkListInput[Int, Double])
    extends Adjustable[AdjustableList[Int, Double]] {
  def run(implicit c: Context) = {
    val list = input.getChunkList()
    list.sort((pair1, pair2) => {
      println("      comparing " + pair1 + " " + pair2)
      pair1._2 < pair2._2
    })
  }
}

class SplitTest(input: ListInput[Int, Int], input2: TableInput[Int, Int])
    extends Adjustable[Mod[(AdjustableList[Int, Int], AdjustableList[Int, Int])]] {
  def run(implicit c: Context) = {
    val table = input2.getTable()
    val pivot = table.get(1)

    val list = input.getAdjustableList()

    mod {
      read(pivot) {
	case pivot =>
	  write(list.split(pair => {
	    println("splitting " + pair)
	    pair._2 < pivot
	  }))
      }
    }
  }
}

class MergeTest(input: ListInput[Int, Int], input2: ListInput[Int, Int])
    extends Adjustable[AdjustableList[Int, Int]] {
  def run(implicit c: Context) = {
    val list = input.getAdjustableList()
    val list2 = input2.getAdjustableList()

    list.asInstanceOf[ModList[Int, Int]]
      .merge(list2.asInstanceOf[ModList[Int, Int]], (pair: (Int, Int), pair2: (Int, Int)) => {
	println("comparing " + pair + " " + pair2)
	pair._2 <= pair2._2
      })
  }
}

class ChunkMergeTest(input: ChunkListInput[Int, Int], input2: ChunkListInput[Int, Int])
    extends Adjustable[AdjustableList[Int, Int]] {
  def run(implicit c: Context) = {
    val list = input.getChunkList()
    val list2 = input2.getChunkList()

    list.asInstanceOf[ChunkList[Int, Int]]
      .merge(list2.asInstanceOf[ChunkList[Int, Int]], (pair: (Int, Int), pair2: (Int, Int)) => {
	println("comparing " + pair + " " + pair2)
	pair._2 <= pair2._2
      })
  }
}

class MapReduceTest(input: ListInput[Int, Int])
    extends Adjustable[Mod[(Int, Int)]] {
  def run(implicit c: Context) = {
    val list = input.getAdjustableList()

    val mapped = list.map(pair => {
      println("mapping " + pair)
      (pair._1, pair._2 * 2)
    })

    mapped.reduce((pair1, pair2) => {
      println("reducing " + pair1 + " and " + pair2)
      (pair1._1, pair1._2 + pair2._2)
    })
  }
}

class ReexecutionTests extends FlatSpec with Matchers {
  /*"ReduceTest" should "reexecute only the necessary reduce steps" in {
    val mutator = new Mutator()
    val input = ListInput[Int, Int](new ListConf(partitions = 1))
    for (i <- 1 to 16) {
      input.put(i, i)
    }
    val test = new ReduceTest(input)
    val output = mutator.run(test)

    output.read()._2 should be (136)
    input.remove(16)
    input.remove(7)
    mutator.propagate()

    output.read()._2 should be (113)
  }*/

  /*"SortTest" should "only reexecute the least possible" in {
    val mutator = new Mutator()
    val input = ListInput[Int, Double](new ListConf(chunkSize = 1, partitions = 1))
    for (i <- List(10, 5, 6, 1, 7, 4, 8, 3, 2, 9)) {
      input.put(i, i)
    }
    val output = mutator.run(new SortTest(input))
    println(output.toBuffer)

    println("\npropagating")
    input.update(1, 8.5)
    mutator.propagate()
    println(output.toBuffer)
  }*/

  /*"ChunkSortTest" should "only reexecute the least possible" in {
    val mutator = new Mutator()
    val input = ChunkListInput[Int, Double](new ListConf(chunkSize = 2, partitions = 1))
    for (i <- List(10, 5, 6, 1, 7, 4, 8, 3, 2, 9)) {
      input.put(i, i)
    }
    val output = mutator.run(new ChunkSortTest(input))
    println(output.toBuffer)

    println("\npropagating")
    input.update(1, 8.5)
    mutator.propagate()
    println(output.toBuffer)
  }*/

  /*"SplitTest" should " asdf" in {
    val mutator = new Mutator()
    val input = ListInput[Int, Int](new ListConf(chunkSize = 1, partitions = 1))
    input.put(5, 5)
    input.put(3, 3)
    input.put(6, 6)
    input.put(7, 7)
    input.put(9, 9)
    input.put(2, 2)
    input.put(1, 1)
    input.put(4, 4)
    input.put(10, 10)

    val input2 = TableInput[Int, Int]()
    input2.put(1, 4)
    val output = mutator.run(new SplitTest(input, input2))
    println(output.read()._1 + "\n" + output.read()._2)

    println("\npropagating")
    //input2.update(1, 5)
    input.update(5, -1)
    mutator.propagate()
    println(output.read()._1 + "\n" + output.read()._2)
  }*/

  /*"MergeTest" should "asdf" in {
    val mutator = new Mutator()
    val conf = new ListConf(partitions = 1, chunkSize = 1)

    val input = ListInput[Int, Int](conf)
    /*input.put(1, 1)
    input.put(5, 5)
    input.put(6, 6)
    input.put(7, 7)
    input.put(10, 10)*/
    input.put(1, 1)
    input.put(6, 6)

    val input2 = ListInput[Int, Int](conf)
    /*input2.put(2, 2)
    input2.put(3, 3)
    input2.put(4, 4)
    input2.put(8, 8)
    input2.put(9, 9)*/
    input2.put(7, 7)

    //println(input.getAdjustableList())
    //println(input2.getAdjustableList())
    val output = mutator.run(new MergeTest(input, input2))
    println(output)
    println(output.toBuffer)

    println("\npropagating")
    input.remove(1)
    input.put(1, 8)

    //println(input.getAdjustableList())
    //println(input2.getAdjustableList())
    mutator.propagate()
    println(output)
    println(output.toBuffer)
  }*/

  /*"ChunkMergeTest" should "asdf" in {
    val mutator = new Mutator()
    val conf = new ListConf(partitions = 1, chunkSize = 2)

    val input = ChunkListInput[Int, Int](conf)

    val input2 = ChunkListInput[Int, Int](conf)
    //input2.put(0, 0)
    //input2.put(2, 2)
    input.put(4, 4)
    input.put(8, 8)

    val input2 = mutator.createChunkList[Int, Int](conf)
    input2.put(1, 1)
    input2.put(3, 3)
    input2.put(5, 5)
    input2.put(10, 10)

    println(input.getChunkList())
    println(input2.getChunkList())
    val output = mutator.run(new MergeTest(input, input2))
    println(output)
    println(output.toBuffer)

    println("\npropagating")
    input2.remove(3)
    input2.put(3, 12)

    println(input.getChunkList())
    println(input2.getChunkList())
    mutator.propagate()
    println(output)
    println(output.toBuffer)
  }*/

  /*"MapReduceTest" should "only reexecute the necessary parts" in {
    val mutator = new Mutator()
    val input = ListInput[Int, Int](new ListConf(partitions = 1, chunkSize = 1))
    for (i <- List(6, 2, 7, 3, 8, 1, 5, 10)) {
      input.put(i, i)
    }

    val output = mutator.run(new MapReduceTest(input))
    println(output.read())

    println("propagate")
    input.update(7, 8)
    mutator.propagate()
  }*/
}