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
  val MemoNodeType: Byte = 0
  val Memo1NodeType: Byte = 8
  val ModizerNodeType: Byte = 1
  val ModNodeType: Byte = 2
  val ParNodeType: Byte = 3
  val ReadNodeType: Byte = 4
  val Read2NodeType: Byte = 5
  val RootNodeType: Byte = 6
  val WriteNodeType: Byte = 7

  val typeOffset = 0
  val currentModId1Offset = typeOffset + 1
  val currentModId2Offset = currentModId1Offset + modIdSize
  val nodeOffset = currentModId2Offset + modIdSize

  def create
      (size: Int,
       nodeType: Byte,
       currentModId1: ModId,
       currentModId2: ModId): Pointer = {
    val ptr = MemoryAllocator.allocate(size + 1 + modIdSize * 2)

    MemoryAllocator.putByte(ptr + typeOffset, nodeType)
    MemoryAllocator.putModId(ptr + currentModId1Offset, currentModId1)
    MemoryAllocator.putModId(ptr + currentModId2Offset, currentModId2)

    ptr
  }

  def getType(ptr: Pointer): Byte = {
    MemoryAllocator.getByte(ptr + typeOffset)
  }

  def getCurrentModId1(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + currentModId1Offset)
  }

  def setCurrentModId1(ptr: Pointer, newModId: ModId) {
    MemoryAllocator.putModId(ptr + currentModId1Offset, newModId)
  }

  def getCurrentModId2(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + currentModId2Offset)
  }

  def setCurrentModId2(ptr: Pointer, newModId: ModId) {
    MemoryAllocator.putModId(ptr + currentModId2Offset, newModId)
  }

  var id = 0

  def getId(): Int = {
    id = id + 1
    id
  }
}

object MemoNode {
  private val memoizerIdOffset = Node.nodeOffset
  private val signatureSizeOffset = memoizerIdOffset + memoizerIdSize
  private val signatureOffset = signatureSizeOffset + 4

  def create
      (memoizerId: MemoizerId,
       signature: Seq[_],
       currentModId1: ModId,
       currentModId2: ModId): Pointer = {
    val byteOutput = new ByteArrayOutputStream()
    val objectOutput = new ObjectOutputStream(byteOutput)
    objectOutput.writeObject(signature)
    val serializedSignature = byteOutput.toByteArray

    val size = memoizerIdSize + 4 + serializedSignature.size
    val ptr = Node.create(size, Node.MemoNodeType, currentModId1, currentModId2)

    MemoryAllocator.putInt(ptr + memoizerIdOffset, memoizerId)
    MemoryAllocator.putInt(
      ptr + signatureSizeOffset, serializedSignature.size)

    for (i <- 0 until serializedSignature.size) {
      MemoryAllocator.putByte(
        ptr + signatureOffset + i, serializedSignature(i))
    }

    ptr
  }

  def getMemoizerId(ptr: Pointer): MemoizerId = {
    MemoryAllocator.getInt(ptr + memoizerIdOffset)
  }

  def getSignature(ptr: Pointer): Seq[Any] = {
    val signatureSize = MemoryAllocator.getInt(ptr + signatureSizeOffset)

    val byteArray = new Array[Byte](signatureSize)

    for (i <- 0 until signatureSize) {
      byteArray(i) = MemoryAllocator.getByte(ptr + signatureOffset + i)
    }

    val byteInput = new ByteArrayInputStream(byteArray)
    val objectInput = new ObjectInputStream(byteInput)
    objectInput.readObject().asInstanceOf[Seq[Any]]
  }
}

object Memo1Node {
  private val memoizerIdOffset = Node.nodeOffset
  private val modId1Offset = memoizerIdOffset + memoizerIdSize

  def create
      (memoizerId: MemoizerId,
       modId1: ModId,
       currentModId1: ModId,
       currentModId2: ModId): Pointer = {
    val size = memoizerIdSize + modIdSize
    val ptr = Node.create(
      size, Node.Memo1NodeType, currentModId1, currentModId2)

    MemoryAllocator.putInt(ptr + memoizerIdOffset, memoizerId)
    MemoryAllocator.putModId(ptr + modId1Offset, modId1)

    ptr
  }

  def getMemoizerId(ptr: Pointer): MemoizerId = {
    MemoryAllocator.getInt(ptr + memoizerIdOffset)
  }

  def getSignature(ptr: Pointer): Seq[Any] = {
    val memoizerId = getMemoizerId(ptr)
    val modId1 = MemoryAllocator.getModId(ptr + modId1Offset)

    Seq(memoizerId, new Mod(modId1))
  }
}

object ModizerNode {
  private val modId1Offset = Node.nodeOffset
  private val modId2Offset = modId1Offset + modIdSize
  private val modizerIdOffset = modId2Offset + modIdSize
  private val keySizeOffset = modizerIdOffset + modizerIdSize
  private val keyOffset = keySizeOffset + 4

  def create
      (modId1: ModId,
       modId2: ModId,
       modizerId: ModizerId,
       key: Any,
       currentModId1: ModId,
       currentModId2: ModId): Pointer = {
    val byteOutput = new ByteArrayOutputStream()
    val objectOutput = new ObjectOutputStream(byteOutput)
    objectOutput.writeObject(key)
    val serializedKey = byteOutput.toByteArray

    // Two modIds + modizerId + key + key size
    val size = modIdSize * 2 + modizerIdSize + 4 + serializedKey.size
    val ptr = Node.create(
      size, Node.ModizerNodeType, currentModId1, currentModId2)

    MemoryAllocator.putModId(ptr + modId1Offset, modId1)
    MemoryAllocator.putModId(ptr + modId2Offset, modId2)
    MemoryAllocator.putInt(ptr + modizerIdOffset, modizerId)
    MemoryAllocator.putInt(ptr + keySizeOffset, serializedKey.size)

    for (i <- 0 until serializedKey.size) {
      MemoryAllocator.putByte(ptr + keyOffset + i, serializedKey(i))
    }

    ptr
  }

  def getModId1(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId1Offset)
  }

  def getModId2(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId2Offset)
  }

  def getModizerId(ptr: Pointer): ModizerId = {
    MemoryAllocator.getInt(ptr + modizerIdOffset)
  }

  def getKey(ptr: Pointer): Any = {
    val keySize = MemoryAllocator.getInt(ptr + keySizeOffset)

    val byteArray = new Array[Byte](keySize)

    for (i <- 0 until keySize) {
      byteArray(i) = MemoryAllocator.getByte(ptr + keyOffset + i)
    }

    val byteInput = new ByteArrayInputStream(byteArray)
    val objectInput = new ObjectInputStream(byteInput)
    objectInput.readObject()
  }
}

object ModNode {
  private val modId1Offset = Node.nodeOffset
  private val modId2Offset = modId1Offset + modIdSize

  def create
      (modId1: ModId,
       modId2: ModId,
       currentModId1: ModId,
       currentModId2: ModId): Pointer = {
    // Two modIds + modizerId + key + key size
    val size = modIdSize * 2 + modizerIdSize
    val ptr = Node.create(size, Node.ModNodeType, currentModId1, currentModId2)

    MemoryAllocator.putModId(ptr + modId1Offset, modId1)
    MemoryAllocator.putModId(ptr + modId2Offset, modId2)

    ptr
  }

  def getModId1(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId1Offset)
  }

  def getModId2(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId2Offset)
  }
}

object ParNode {
  private val taskId1Offset = Node.nodeOffset
  private val taskId2Offset = taskId1Offset + taskIdSize
  private val pebble1Offset = taskId2Offset + taskIdSize
  private val pebble2Offset = pebble1Offset + 1

  def create(taskId1: TaskId, taskId2: TaskId): Pointer = {
    val size = taskIdSize * 2 + 2
    val ptr = Node.create(size, Node.ParNodeType, -1, -1)

    MemoryAllocator.putInt(ptr + taskId1Offset, taskId1)
    MemoryAllocator.putInt(ptr + taskId2Offset, taskId2)
    MemoryAllocator.putByte(ptr + pebble1Offset, 0)
    MemoryAllocator.putByte(ptr + pebble2Offset, 0)

    ptr
  }

  def getTaskId1(ptr: Pointer): TaskId = {
    MemoryAllocator.getInt(ptr + taskId1Offset)
  }

  def getTaskId2(ptr: Pointer): TaskId = {
    MemoryAllocator.getInt(ptr + taskId2Offset)
  }

  def getPebble1(ptr: Pointer): Boolean = {
    MemoryAllocator.getByte(ptr + pebble1Offset) == 1
  }

  def setPebble1(ptr: Pointer, value: Boolean) {
    val byte: Byte = if (value) 1 else 0

    MemoryAllocator.putByte(ptr + pebble1Offset, byte)
  }

  def getPebble2(ptr: Pointer): Boolean = {
    MemoryAllocator.getByte(ptr + pebble2Offset) == 1
  }

  def setPebble2(ptr: Pointer, value: Boolean) {
    val byte: Byte = if (value) 1 else 0

    MemoryAllocator.putByte(ptr + pebble2Offset, byte)
  }
}

object ReadNode {
  private val modIdOffset = Node.nodeOffset
  private val readerIdOffset = modIdOffset + modIdSize

  def create
      (modId: ModId,
       readerId: Int,
       currentModId1: ModId,
       currentModId2: ModId): Pointer = {
    val size = modIdSize + 4
    val ptr = Node.create(size, Node.ReadNodeType, currentModId1, currentModId2)

    MemoryAllocator.putModId(ptr + modIdOffset, modId)
    MemoryAllocator.putInt(ptr + readerIdOffset, readerId)

    ptr
  }

  def getModId(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modIdOffset)
  }

  def getReaderId(ptr: Pointer): Int = {
    MemoryAllocator.getInt(ptr + readerIdOffset)
  }
}

object Read2Node {
  private val modId1Offset = Node.nodeOffset
  private val modId2Offset = modId1Offset + modIdSize
  private val readerIdOffset = modId2Offset + modIdSize

  def create
      (modId1: ModId,
       modId2: ModId,
       readerId: Int,
       currentModId1: ModId,
       currentModId2: ModId): Pointer = {
    val size = modIdSize * 2 + 4
    val ptr = Node.create(
      size, Node.Read2NodeType, currentModId1, currentModId2)

    MemoryAllocator.putModId(ptr + modId1Offset, modId1)
    MemoryAllocator.putModId(ptr + modId2Offset, modId2)
    MemoryAllocator.putInt(ptr + readerIdOffset, readerId)

    ptr
  }

  def getModId1(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId1Offset)
  }

  def getModId2(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId2Offset)
  }

  def getReaderId(ptr: Pointer): Int = {
    MemoryAllocator.getInt(ptr + readerIdOffset)
  }
}

object RootNode {
  def create(): Pointer = {
    val size = 0
    Node.create(size, Node.RootNodeType, -1, -1)
  }
}

object WriteNode {
  val modId1Offset = Node.nodeOffset
  val modId2Offset = modId1Offset + modIdSize

  def create(modId1: ModId, modId2: ModId): Pointer = {
    val size = modIdSize * 2
    val ptr = Node.create(size, Node.WriteNodeType, -1, -1)

    MemoryAllocator.putModId(ptr + modId1Offset, modId1)
    MemoryAllocator.putModId(ptr + modId2Offset, modId2)

    ptr
  }

  def getModId1(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId1Offset)
  }

  def getModId2(ptr: Pointer): ModId = {
    MemoryAllocator.getModId(ptr + modId2Offset)
  }
}
