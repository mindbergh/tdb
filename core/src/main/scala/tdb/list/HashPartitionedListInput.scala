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
package tdb.list

import akka.actor.ActorRef
import akka.pattern.ask
import scala.collection.mutable.{Buffer, Map}
import scala.concurrent.{Await, Future}

import tdb.Constants._
import tdb.messages._
import tdb.util.ObjHasher

trait HashPartitionedListInput[T, U]
  extends Dataset[T, U] with java.io.Serializable {

  def hasher: ObjHasher[(String, ActorRef)]

  def put(key: T, value: U) = {
    Await.result(asyncPut(key, value), DURATION)
  }

  def asyncPut(key: T, value: U) = {
    val (listId, datastoreRef) = hasher.getObj(key)
    datastoreRef ? PutMessage(listId, key, value)
  }

  def asyncPutAll(values: Iterable[(T, U)]): Future[_] = {
    val hashedPut = hasher.hashAll(values)

    val futures = Buffer[Future[Any]]()
    for ((hash, buf) <- hashedPut) {
      if (buf.size > 0) {
        val (listId, datastoreRef) = hasher.objs(hash)
        futures += datastoreRef ? PutAllMessage(listId, buf)
      }
    }

    import scala.concurrent.ExecutionContext.Implicits.global
    Future.sequence(futures)
  }

  def remove(key: T, value: U) = {
    Await.result(asyncRemove(key, value), DURATION)
  }

  def removeAll(values: Iterable[(T, U)]) {
    val futures = Buffer[Future[Any]]()
    val hashedRemove = hasher.hashAll(values)

    for ((hash, buf) <- hashedRemove) {
      if (buf.size > 0) {
        val (listId, datastoreRef) = hasher.objs(hash)
        futures += datastoreRef ? RemoveAllMessage(listId, buf)
      }
    }
    import scala.concurrent.ExecutionContext.Implicits.global
    Await.result(Future.sequence(futures), DURATION)
  }

  def asyncRemove(key: T, value: U): Future[_] = {
    val (listId, datastoreRef) = hasher.getObj(key)
    datastoreRef ? RemoveMessage(listId, key, value)
  }

  def load(data: Map[T, U]) = {
    for ((key, value) <- data) {
      put(key, value)
    }
  }

  def getPartitions = ???

  def getBuffer(): InputBuffer[T, U] = new HashBuffer(this)
}

class HashBuffer[T, U](input: ListInput[T, U]) extends InputBuffer[T, U] {

  private val toPut = Map[T, U]()

  private val toRemove = Map[T, U]()

  def putAll(values: Iterable[(T, U)]) {
    for ((key, value) <- values) {
      if (toRemove.contains(key)) {
        toRemove -= key
      } else {
        toPut += ((key, value))
      }
    }
  }

  def removeAll(values: Iterable[(T, U)]) {
    for ((key, value) <- values) {
      if (toPut.contains(key)) {
        toPut -= key
      } else {
        toRemove += ((key, value))
      }
    }
  }

  def flush() {
    val futures = Buffer[Future[Any]]()

    futures += input.asyncPutAll(toPut)
    input.removeAll(toRemove)

    toRemove.clear()
    toPut.clear()

    import scala.concurrent.ExecutionContext.Implicits.global
    Await.result(Future.sequence(futures), DURATION)
  }
}
