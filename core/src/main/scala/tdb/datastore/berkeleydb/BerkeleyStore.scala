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
package tdb.datastore.berkeleydb

import akka.actor.{ActorRef, ActorContext, Props}
import akka.pattern.{ask, pipe}
import com.sleepycat.je.{Environment, EnvironmentConfig}
import com.sleepycat.persist.{EntityStore, PrimaryIndex, StoreConfig}
import com.sleepycat.persist.model.{Entity, PrimaryKey, SecondaryKey}
import com.sleepycat.persist.model.Relationship
import java.io._
import scala.collection.mutable.{Buffer, Map}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success}

import tdb.datastore.{KVStore, MemoryUsage}
import tdb.messages._
import tdb.Mod
import tdb.Constants.ModId
import tdb.util._
import tdb.worker.WorkerConf

class LRUNode(
  val key: Any,
  var value: Any,
  val storeId: Int,
  var previous: LRUNode,
  var next: LRUNode
)

class BerkeleyStore(conf: WorkerConf)
    (implicit ec: ExecutionContext) extends KVStore {
  private var database = new BerkeleyDatabase(conf.envHomePath())

  private val tables = Map[Int, BerkeleyTable]()

  // LRU cache
  private val values = Map[Any, LRUNode]()
  private val tail = new LRUNode(null, null, -1, null, null)
  private var head = tail

  // Statistics
  private var readCount = 0
  private var writeCount = 0
  private var deleteCount = 0

  var cacheSize = 0L

  var n = 0

  private var nextStoreId = 0

  def createTable[T: TypeTag, U: TypeTag]
      (name: String, range: HashRange): Int = {
    val id = nextStoreId
    nextStoreId += 1

    typeOf[T] match {
      case s if typeOf[T] =:= typeOf[String] && typeOf[U] =:= typeOf[String] =>
        tables(id) = database.createInputStore(name, range)
      case m if typeOf[T] =:= typeOf[ModId] =>
        tables(id) = database.createModStore()
      case _ => ???
    }

    id
  }

  def load(id: Int, fileName: String) {
    tables(id).load(fileName)
  }

  def put(id: Int, key: Any, value: Any): Future[Any] = {
    val future = Future {
      tables(id).put(key, value)
    }

    cacheSize += MemoryUsage.getSize(value)
    if (values.contains(key)) {
      cacheSize -= MemoryUsage.getSize(values(key).value)
      values(key).value = value
    } else {
      val newNode = new LRUNode(key, value, id, null, head)
      values(key) = newNode

      head.previous = newNode
      head = newNode
    }

    val futures = Buffer[Future[Any]]()
    while ((cacheSize / 1024 / 1024) > conf.cacheSize()) {
      val toEvict = tail.previous

      val key = toEvict.key
      val value = toEvict.value

      values -= toEvict.key
      cacheSize -= MemoryUsage.getSize(value)

      tail.previous = toEvict.previous
      toEvict.previous.next = tail
    }

    /*if (n % 100000 == 0) {
      println("Current cache size: " + (cacheSize / 1024 / 1024))
    }
    n += 1*/

    future
  }

  def get(id: Int, key: Any) = {
    if (values.contains(key)) {
      val value = values(key).value
      Future { value }
    } else {
      val store = tables(id)
      Future {
        store.get(key)
      }
    }
  }

  def delete(id: Int, key: Any) {
    if (values.contains(key)) {
      values -= key
    }

    tables(id).delete(key)
  }

  def contains(id: Int, key: Any): Boolean = {
    tables(id).contains(key)
  }

  def count(id: Int): Int = {
    tables(id).count()
  }

  def clear() = {
    for ((id, table) <- tables) {
      table.close()
    }
    tables.clear()
    nextStoreId = 0

    values.clear()

    cacheSize = 0
    head = tail
    tail.previous = null
    tail.next = null

    database.close()
    database = new BerkeleyDatabase(conf.envHomePath())
  }

  def hashedForeach(id: Int)(process: (Int, Iterator[Any]) => Unit) {
    tables(id).hashedForeach(process)
  }

  def hashRange(id: Int) = {
    tables(id).hashRange
  }

  /*def shutdown() {
    println("Shutting down. writes = " + writeCount + ", reads = " +
            readCount + ", deletes = " + deleteCount)
    modStore.close()
    database.close()
  }*/
}
