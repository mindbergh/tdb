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
package tbd.ddg

import akka.actor.ActorRef
import akka.pattern.ask
import java.io._
import scala.concurrent.Await

import tbd._
import tbd.Constants._

object Node {
  var id = 0

  def getId(): Int = {
    id = id + 1
    id
  }
}

object ReadNode {
  private val modIdOffset = 0

  def create(modId: ModId, updated: Boolean): Pointer = {
    val ptr = MemoryAllocator.allocate(modIdSize)

    MemoryAllocator.unsafe.putLong(ptr + modIdOffset, modId)

    ptr
  }

  def getModId(ptr: Pointer): ModId = {
    MemoryAllocator.unsafe.getLong(ptr)
  }
}

object ModNode {
  private val modId1Offset = 0
  private val modId2Offset = modId1Offset + modIdSize
  private val modizerIdOffset = modId2Offset + modIdSize
  private val keySizeOffset = modizerIdOffset + modizerIdSize
  private val keyOffset = keySizeOffset + 4

  def create
      (modId1: ModId,
       modId2: ModId,
       modizerId: ModizerId,
       key: Any): Pointer = {
    val byteOutput = new ByteArrayOutputStream()
    val objectOutput = new ObjectOutputStream(byteOutput)
    objectOutput.writeObject(key)
    val serializedKey = byteOutput.toByteArray

    // Two modIds + modizerId + key + key size
    val size = modIdSize * 2 + modizerIdSize + 4 + serializedKey.size
    val ptr = MemoryAllocator.allocate(size)

    MemoryAllocator.unsafe.putLong(ptr + modId1Offset, modId1)

    MemoryAllocator.unsafe.putLong(ptr + modId2Offset, modId2)

    MemoryAllocator.unsafe.putInt(ptr + modizerIdOffset, modizerId)

    MemoryAllocator.unsafe.putInt(ptr + keySizeOffset, serializedKey.size)
    for (i <- 0 until serializedKey.size) {
      MemoryAllocator.unsafe.putByte(ptr + keyOffset + i, serializedKey(i))
    }

    ptr
  }

  def getModId1(ptr: Pointer): ModId = {
    MemoryAllocator.unsafe.getLong(ptr + modId1Offset)
  }

  def getModId2(ptr: Pointer): ModId = {
    MemoryAllocator.unsafe.getLong(ptr + modId2Offset)
  }

  def getModizerId(ptr: Pointer): ModizerId = {
    MemoryAllocator.unsafe.getInt(ptr + modizerIdOffset)
  }

  def getKey(ptr: Pointer): Any = {
    val keySize = MemoryAllocator.unsafe.getInt(ptr + keySizeOffset)

    val byteArray = new Array[Byte](keySize)

    for (i <- 0 until keySize) {
      byteArray(i) = MemoryAllocator.unsafe.getByte(ptr + keyOffset + i)
    }

    val byteInput = new ByteArrayInputStream(byteArray)
    val objectInput = new ObjectInputStream(byteInput)
    objectInput.readObject()    
  }
}

abstract class Node {
  var currentModId: ModId = -1

  var currentModId2: ModId = -1
}

class MemoNode
    (val signature: Seq[Any],
     val memoizer: Memoizer[_]) extends Node {

  var value: Any = null
}

class ModNode() extends Node

class ParNode
    (val taskRef1: ActorRef,
     val taskRef2: ActorRef) extends Node {
  var pebble1 = false
  var pebble2 = false
}

class ReadNode(val reader: Any => Changeable[Any]) extends Node

class Read2Node
    (val modId1: ModId,
     val modId2: ModId,
     val reader: (Any, Any) => Changeable[Any]) extends Node

class RootNode extends Node

class WriteNode(val modId: ModId, val modId2: ModId) extends Node
