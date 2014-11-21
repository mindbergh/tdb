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

import akka.actor.ActorRef
import akka.pattern.ask
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await

import tbd.Constants._
import tbd.messages._
import tbd.TBD._

object TBD {
  def read[T, U](mod: Mod[T])
      (reader: T => Changeable[U])
      (implicit c: Context): Changeable[U] = {
    val value = c.read(mod, c.task.self)

    val timestamp = c.ddg.addRead(
      mod.asInstanceOf[Mod[Any]],
      value,
      reader.asInstanceOf[Any => Changeable[Any]],
      c.currentModId,
      -1,
      c)

    val changeable = reader(value)

    timestamp.end = c.ddg.nextTimestamp(null, c)

    changeable
  }

  def read2[T, U, V](mod: Mod[T])
      (reader: T => (Changeable[U], Changeable[V]))
      (implicit c: Context): (Changeable[U], Changeable[V]) = {
    val value = c.read(mod, c.task.self)

    val timestamp = c.ddg.addRead(
      mod.asInstanceOf[Mod[Any]],
      value,
      reader.asInstanceOf[Any => Changeable[Any]],
      c.currentModId,
      c.currentModId2,
      c)

    val changeables = reader(value)

    timestamp.end = c.ddg.nextTimestamp(null, c)

    changeables
  }

  def read_2[T, U, V](mod1: Mod[T], mod2: Mod[U])
      (reader: (T, U) => Changeable[V])
      (implicit c: Context): Changeable[V] = {
    val value1 = c.read(mod1, c.task.self)
    val value2 = c.read(mod2, c.task.self)

    val timestamp = c.ddg.addRead2(
      mod1.asInstanceOf[Mod[Any]],
      mod2.asInstanceOf[Mod[Any]],
      value1,
      value2,
      reader.asInstanceOf[(Any, Any) => Changeable[Any]],
      c.currentModId,
      -1,
      c)

    val changeable = reader(value1, value2)

    timestamp.end = c.ddg.nextTimestamp(null, c)

    changeable
  }

  def mod[T](initializer: => Changeable[T])
     (implicit c: Context): Mod[T] = {
    val mod1 = new Mod[T](c.newModId())

    modInternal(initializer, mod1, -1, null, c)
  }

  def modInternal[T]
      (initializer: => Changeable[T],
       mod1: Mod[T],
       modizerId: ModizerId,
       key: Any,
       c: Context): Mod[T] = {
    val oldCurrentModId = c.currentModId

    c.currentModId = mod1.id

    val timestamp = c.ddg.addMod(mod1.id, -1, modizerId, key, -1, -1, c)

    initializer

    timestamp.end = c.ddg.nextTimestamp(null, c)

    c.currentModId = oldCurrentModId

    mod1
  }

  def mod2[T, U](initializer: => (Changeable[T], Changeable[U]))
      (implicit c: Context): (Mod[T], Mod[U]) = {
    val mod1 = new Mod[T](c.newModId())
    val mod2 = new Mod[U](c.newModId())

    mod2Internal(initializer, mod1, mod2, -1, null, c)
  }

  def mod2Internal[T, U]
      (initializer: => (Changeable[T], Changeable[U]),
       modLeft: Mod[T],
       modRight: Mod[U],
       modizerId: ModizerId,
       key: Any,
       c: Context): (Mod[T], Mod[U]) = {
    val oldCurrentModId = c.currentModId
    c.currentModId = modLeft.id

    val oldCurrentModId2 = c.currentModId2
    c.currentModId2 = modRight.id

    val timestamp = c.ddg.addMod(
      modLeft.id, modRight.id, modizerId, key, -1, -1, c)

    initializer

    timestamp.end = c.ddg.nextTimestamp(null, c)

    c.currentModId = oldCurrentModId
    c.currentModId2 = oldCurrentModId2

    (modLeft, modRight)
  }

  def modLeft[T, U](initializer: => (Changeable[T], Changeable[U]))
      (implicit c: Context): (Mod[T], Changeable[U]) = {
    modLeftInternal(initializer, new Mod[T](c.newModId()), -1, null, c)
  }

  def modLeftInternal[T, U]
      (initializer: => (Changeable[T], Changeable[U]),
       modLeft: Mod[T],
       modizerId: ModizerId,
       key: Any,
       c: Context): (Mod[T], Changeable[U]) = {

    val oldCurrentModId = c.currentModId
    c.currentModId = modLeft.id

    val timestamp = c.ddg.addMod(
      modLeft.id, -1, modizerId, key, -1, c.currentModId2, c)

    initializer

    timestamp.end = c.ddg.nextTimestamp(null, c)

    c.currentModId = oldCurrentModId

    (modLeft, new Changeable[U](c.currentModId2))
  }

  def modRight[T, U](initializer: => (Changeable[T], Changeable[U]))
      (implicit c: Context): (Changeable[T], Mod[U]) = {
    modRightInternal(initializer, new Mod[U](c.newModId()), -1, null, c)
  }

  def modRightInternal[T, U]
      (initializer: => (Changeable[T], Changeable[U]),
       modRight: Mod[U],
       modizerId: ModizerId,
       key: Any,
       c: Context): (Changeable[T], Mod[U]) = {
    val oldCurrentModId2 = c.currentModId2
    c.currentModId2 = modRight.id

    val timestamp = c.ddg.addMod(
      -1, modRight.id, modizerId, key, c.currentModId, -1, c)

    initializer

    timestamp.end = c.ddg.nextTimestamp(null, c)

    c.currentModId2 = oldCurrentModId2

    (new Changeable[T](c.currentModId), modRight)
  }

  def write[T](value: T)(implicit c: Context): Changeable[T] = {
    c.update(c.currentModId, value)

    (new Changeable[T](c.currentModId))
  }

  def write2[T, U](value: T, value2: U)
      (implicit c: Context): (Changeable[T], Changeable[U]) = {
    c.update(c.currentModId, value)

    c.update(c.currentModId2, value2)

    (new Changeable[T](c.currentModId),
     new Changeable[U](c.currentModId2))
  }

  def writeLeft[T, U](value: T, changeable: Changeable[U])
      (implicit c: Context): (Changeable[T], Changeable[U]) = {
    c.update(c.currentModId, value)

    (new Changeable[T](c.currentModId),
     new Changeable[U](c.currentModId2))
  }

  def writeRight[T, U](changeable: Changeable[T], value2: U)
      (implicit c: Context): (Changeable[T], Changeable[U]) = {
    c.update(c.currentModId2, value2)

    (new Changeable[T](c.currentModId),
     new Changeable[U](c.currentModId2))
  }

  def parWithHint[T, U](one: Context => T, workerId1: WorkerId = -1)
      (two: Context => U, workerId2: WorkerId = -1)
      (implicit c: Context): (T, U) = {
    val future1 = c.masterRef ? ScheduleTaskMessage(c.task.self, workerId1)
    val taskRef1 = Await.result(future1.mapTo[ActorRef], DURATION)

    val adjust1 = new Adjustable[T] { def run(implicit c: Context) = one(c) }
    val oneFuture = taskRef1 ? RunTaskMessage(adjust1)

    val future2 = c.masterRef ? ScheduleTaskMessage(c.task.self, workerId2)
    val taskRef2 = Await.result(future2.mapTo[ActorRef], DURATION)

    val adjust2 = new Adjustable[U] { def run(implicit c: Context) = two(c) }
    val twoFuture = taskRef2 ? RunTaskMessage(adjust2)

    val parNode = c.ddg.addPar(taskRef1, taskRef2, c)

    val oneRet = Await.result(oneFuture, DURATION).asInstanceOf[T]
    val twoRet = Await.result(twoFuture, DURATION).asInstanceOf[U]
    (oneRet, twoRet)
  }

  def par[T](one: Context => T): Parizer[T] = {
    new Parizer(one)
  }
}
