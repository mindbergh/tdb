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

import scala.collection.mutable.Buffer

import tbd.Constants._

class Ordering(basePointer: Pointer = -1) {
  private val maxSize = Int.MaxValue / 2
  val base = new Sublist(0, null)
  base.next = new Sublist(1, base, basePointer)
  base.previous = base.next

  base.next.base.end = base.base

  def after(t: Timestamp, ptr: Pointer): Timestamp = {
    val previousSublist =
      if (t == null) {
        base.next
      } else {
        t.sublist
      }

    val newTimestamp = previousSublist.after(t, ptr)
    if (previousSublist.size > 63) {
      val newSublist = sublistAfter(previousSublist)
      assert(previousSublist.id != newSublist.id)
      previousSublist.split(newSublist)
    }

    newTimestamp
  }

  def append(ptr: Pointer): Timestamp = {
    val newTimestamp =
      if (base.previous.size > 31) {
        val newSublist = sublistAppend()
        newSublist.append(ptr)
      } else {
        base.previous.append(ptr)
      }

    newTimestamp
  }

  def remove(t: Timestamp) {
    t.sublist.remove(t)

    if (t.sublist.size == 0) {
      t.sublist.previous.next = t.sublist.next
      t.sublist.next.previous = t.sublist.previous
    }
  }

  private def sublistAppend(): Sublist = {
    val previous = base.previous
    val newSublist = new Sublist(previous.id + 1, base)
    newSublist.previous = previous

    previous.next = newSublist
    base.previous = newSublist

    newSublist
  }

  private def sublistAfter(s: Sublist): Sublist = {
    var node = s.next
    while (node != base) {
      node.id += 1
      node = node.next
    }
    val newSublist = new Sublist(s.id + 1, s.next)
    newSublist.previous = s

    s.next = newSublist
    newSublist.next.previous = newSublist

    newSublist
    /*val previous =
      if (s == null) {
        base
      } else {
        s
      }
    val v0 = previous.id

    var j = 1
    var vj = previous.next
    var wj =
      if (vj == base) {
        maxSize
      } else {
        (vj.id - v0) % maxSize
      }
    while (wj <= j * j) {
      vj = vj.next
      j += 1
      wj =
        if (vj == base) {
          maxSize
        } else {
          (vj.id - v0) % maxSize
        }
    }

    var sx = previous.next
    for (i <- 1 to j - 1) {
      sx.id = (wj * (i / j) + v0) % maxSize
      sx = sx.next
    }

    val nextId =
      if (previous.next == base) {
        maxSize
      } else {
        previous.next.id
      }

    val newSublist = new Sublist((v0 + nextId) / 2, previous.next)
    previous.next = newSublist

    newSublist*/
  }

  override def toString = {
    var node = base.next
    var ret = base.toString

    while (node != base) {
      print(node + " ")
      ret += ", " + node
      node = node.next
    }
    ret
  }
}

