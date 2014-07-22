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
package tbd

import akka.event.Logging
import scala.collection.mutable.{ListBuffer, Set}
import scala.concurrent.{Await, Future}

import tbd.Constants._
import tbd.ddg.{Node, Timestamp}
import tbd.master.Main
import tbd.mod.{Dest, Mod}
import tbd.worker.Worker

class Context(val id: String, val worker: Worker) {
  import worker.context.dispatcher

  var initialRun = true

  // The Node representing the currently executing reader.
  var currentParent: Node = worker.ddg.root

  val log = Logging(worker.context.system, "TBD" + id)

  // Contains a list of mods that have been updated since the last run of change
  // propagation, to determine when memo matches can be made.
  val updatedMods = Set[ModId]()

  // The timestamp of the read currently being reexecuting during change
  // propagation.
  var reexecutionStart: Timestamp = _

  // The timestamp of the node immediately after the end of the read being
  // reexecuted.
  var reexecutionEnd: Timestamp = _

  /* readN - Read n mods. Experimental function.
   *
   * Usage Example:
   *
   *  mod {
   *    val a = createMod("Hello");
   *    val b = createMod(12);
   *    val c = createMod("Bla");
   *
   *    readN(a, b, c)(x => x match {
   *      case Seq(a:String, b:Int, c:String) => {
   *        println(a + b + c)
   *        write(dest, null)
   *      }
   *    })
   *  }
   */
  def readN[U](args: Mod[U]*)
              (reader: (Seq[_]) => (Changeable[U])) : Changeable[U] = {

    readNHelper(args, ListBuffer(), reader)
  }

  private def readNHelper[U](mods: Seq[Mod[_]],
                     values: ListBuffer[AnyRef],
                     reader: (Seq[_]) => (Changeable[U])) : Changeable[U] = {
    val tail = mods.tail
    val head = mods.head


    TBD.read(head)((value) => {
      values += value.asInstanceOf[AnyRef]
      if(tail.isEmpty) {
        reader(values.toSeq)
      } else {
        readNHelper(tail, values, reader)
      }
    })(this)
  }

  def writeNoDestLeft[T, U](value: T, mod: Mod[U]): Changeable2[T, U] = {
    if (mod != currentDest2.mod) {
      println("WARNING - mod parameter to write2(0) doesn't match " +
	      "currentDest2")
    }

    val awaiting = currentDest.mod.update(value)
    Await.result(Future.sequence(awaiting), DURATION)

    val changeable = new Changeable2(currentDest.mod, currentDest2.mod)
    if (Main.debug) {
      val writeNode = worker.ddg.addWrite(changeable.mod.asInstanceOf[Mod[Any]],
                                          currentParent)
      writeNode.mod2 = currentDest2.mod
      writeNode.endTime = worker.ddg.nextTimestamp(writeNode)
    }

    changeable.asInstanceOf[Changeable2[T, U]]
  }

  def writeNoDestRight[T, U](mod: Mod[T], value2: U): Changeable2[T, U] = {
    if (mod != currentDest.mod) {
      println("WARNING - mod parameter to writeNoDestRight doesn't match " +
	      "currentDest " + mod + " " + currentDest.mod)
    }

    val awaiting = currentDest2.mod.update(value2)
    Await.result(Future.sequence(awaiting), DURATION)

    val changeable = new Changeable2(currentDest.mod, currentDest2.mod)
    if (Main.debug) {
      val writeNode = worker.ddg.addWrite(changeable.mod.asInstanceOf[Mod[Any]],
                                          currentParent)
      writeNode.mod2 = currentDest2.mod
      writeNode.endTime = worker.ddg.nextTimestamp(writeNode)
    }

    changeable.asInstanceOf[Changeable2[T, U]]
  }

  // The destination created by the most recent (in scope) call to mod. This is
  // what a call to write will write to.
  var currentDest: Dest[Any] = _

  // The second destination created by the most recent call to mod2, if there
  // is one.
  var currentDest2: Dest[Any] = _

  // A unique id to assign to workers forked from this context.
  var workerId = 0

  // A unique id to assign to memo objects created from this context.
  var nextMemoId = 0
}
