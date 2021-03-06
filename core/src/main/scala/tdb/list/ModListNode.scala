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

import java.io.Serializable
import scala.collection.mutable.Map

import tdb._
import tdb.TDB._

object ModListNode {
  type ChangeableTuple[T, U] = (Changeable[ModListNode[T, U]], Changeable[ModListNode[T, U]])
}

class ModListNode[T, U](var value: (T, U),
  val nextMod: Mod[ModListNode[T, U]]) extends Serializable {

  def filter(pred: ((T, U)) => Boolean, memo: Memoizer[Mod[ModListNode[T, U]]])(implicit c: Context): Changeable[ModListNode[T, U]] = {
    def readNext = {
      read(nextMod) {
        case null => write[ModListNode[T, U]](null)
        case next => next.filter(pred, memo)
      }
    }

    if (pred(value)) {
      val newNextMod = memo(List(nextMod)) {
        mod {
          readNext
        }
      }
      write(new ModListNode(value, newNextMod))
    } else {
      readNext
    }
  }

  def loopJoin[V](that: ModList[T, V],
    memo: Memoizer[Changeable[ModListNode[T, (U, V)]]],
    condition: ((T, V), (T, U)) => Boolean)(implicit c: Context): Changeable[ModListNode[T, (U, V)]] = {
    val newNextMod = mod {
      read(nextMod) {
        case null =>
          write[ModListNode[T, (U, V)]](null)
        case node =>
          memo(node) {
            node.loopJoin(that, memo, condition)
          }
      }
    }

    val memo2 = new Memoizer[Changeable[ModListNode[T, (U, V)]]]()
    read(that.head) {
      case null =>
        read(newNextMod) { write(_) }
      case node =>
        node.joinHelper(value, newNextMod, memo2, condition)
    }
  }

  // Iterates over the second join list, testing each element for equality
  // with a single element from the first list.
  private def joinHelper[V](thatValue: (T, V),
    tail: Mod[ModListNode[T, (V, U)]],
    memo: Memoizer[Changeable[ModListNode[T, (V, U)]]],
    condition: ((T, U), (T, V)) => Boolean)(implicit c: Context): Changeable[ModListNode[T, (V, U)]] = {
    if (condition(value, thatValue)) {
      val newValue = (value._1, (thatValue._2, value._2))

      // Terminating early here only works under the assumption that the keys
      // are unique. We'll want to define alternate version of join that work
      // when this assumption isn't true.
      read(nextMod) {
        case null =>
          write(new ModListNode[T, (V, U)](newValue, tail))
        case node =>
          val newNextMod = mod {
            memo(node) {
              node.joinHelper(thatValue, tail, memo, condition)
            }
          }

          write(new ModListNode[T, (V, U)](newValue, newNextMod))
      }
      //write(new ModListNode[T, (V, U)](newValue, tail))
    } else {
      read(nextMod) {
        case null =>
          read(tail) { write(_) }
        case node =>
          memo(node) {
            node.joinHelper(thatValue, tail, memo, condition)
          }
      }
    }
  }

  def mapValues[V](f: U => V,
    memo: Memoizer[Changeable[ModListNode[T, V]]],
    modizer: Modizer1[ModListNode[T, V]])(implicit c: Context): Changeable[ModListNode[T, V]] = {
    val newNextMod = modizer(nextMod.id) {
      read(nextMod) {
        case null =>
          write[ModListNode[T, V]](null)
        case next =>
          memo(next) {
            next.mapValues(f, memo, modizer)
          }
      }
    }

    write(new ModListNode((value._1, f(value._2)), newNextMod))
  }

  def merge(that: ModListNode[T, U],
    memo: Memoizer[Changeable[ModListNode[T, U]]],
    modizer: Modizer1[ModListNode[T, U]],
    comparator: ((T, U), (T, U)) => Int)(implicit c: Context): Changeable[ModListNode[T, U]] = {
    if (comparator(value, that.value) < 0) {
      //if (ordering.lt(value._1, that.value._1)) {
      val newNextMod =
        modizer(value) {
          read(nextMod) {
            case null =>
              memo(null, that) {
                that.mergeTail(memo, modizer)
              }
            case node =>
              memo(node, that) {
                node.merge(that, memo, modizer, comparator)
              }
          }
        }

      write(new ModListNode(value, newNextMod))
    } else {
      val newNextMod =
        modizer(that.value) {
          read(that.nextMod) {
            case null =>
              memo(null, this) {
                this.mergeTail(memo, modizer)
              }
            case node =>
              memo(this, node) {
                this.merge(node, memo, modizer, comparator)
              }
          }
        }

      write(new ModListNode(that.value, newNextMod))
    }
  }

  private def mergeTail(memo: Memoizer[Changeable[ModListNode[T, U]]],
    modizer: Modizer1[ModListNode[T, U]])(implicit c: Context): Changeable[ModListNode[T, U]] = {
    val newNextMod = modizer(value) {
      read(nextMod) {
        case null =>
          write[ModListNode[T, U]](null)
        case next =>
          memo(next) {
            next.mergeTail(memo, modizer)
          }
      }
    }

    write(new ModListNode[T, U](value, newNextMod))
  }

  def reduceBy(f: (U, U) => U,
    comparator: ((T, U), (T, U) ) => Int,
    previousKey: T,
    runningValue: U,
    memo: Memoizer[Changeable[ModListNode[T, U]]])(implicit c: Context): Changeable[ModListNode[T, U]] = {
    if (comparator(value, (previousKey, null.asInstanceOf[U])) == 0) {
      val newRunningValue =
        if (runningValue == null)
          value._2
        else
          f(runningValue, value._2)

      read(nextMod) {
        case null =>
          val tail = mod { write[ModListNode[T, U]](null) }
          write(new ModListNode((value._1, newRunningValue), tail))
        case node =>
          memo(node, value._1, newRunningValue) {
            node.reduceBy(f, comparator, value._1, newRunningValue, memo)
          }
      }
    } else {
        val newNextMod = mod {
        read(nextMod) {
          case null =>
            val tail = mod { write[ModListNode[T, U]](null) }
            write(new ModListNode(value, tail))
          case node =>
            memo(node, value._1, value._2) {
              node.reduceBy(f, comparator, value._1, value._2, memo)
            }
        }
      }

      write(new ModListNode((previousKey, runningValue), newNextMod))
    }
  }

  def sortJoinMerge[V](that: ModListNode[T, V],
    memo: Memoizer[Changeable[ModListNode[T, (U, V)]]])(implicit c: Context, ordering: Ordering[T]): Changeable[ModListNode[T, (U, V)]] = {
    if (value._1 == that.value._1) {
      val newNext = mod {
        read(nextMod) {
          case null => write[ModListNode[T, (U, V)]](null)
          case next =>
            read(that.nextMod) {
              case null => write[ModListNode[T, (U, V)]](null)
              case thatNext =>
                memo(next, thatNext) {
                  next.sortJoinMerge(thatNext, memo)
                }
            }
        }
      }

      write(new ModListNode((value._1, (value._2, that.value._2)), newNext))
    } else if (ordering.lt(value._1, that.value._1)) {
      read(nextMod) {
        case null => write[ModListNode[T, (U, V)]](null)
        case next =>
          memo(next, that) {
            next.sortJoinMerge(that, memo)
          }
      }
    } else {
      read(that.nextMod) {
        case null => write[ModListNode[T, (U, V)]](null)
        case thatNext =>
          memo(this, thatNext) {
            this.sortJoinMerge(thatNext, memo)
          }
      }
    }
  }

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[ModListNode[T, U]]) {
      false
    } else {
      val that = obj.asInstanceOf[ModListNode[T, U]]
      that.value == value && that.nextMod == nextMod
    }
  }

  override def hashCode() = value.hashCode() * nextMod.hashCode()

  override def toString = "Node(" + value + ", " + nextMod + ")"
}
