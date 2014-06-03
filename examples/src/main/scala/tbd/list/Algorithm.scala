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

import scala.collection.{GenIterable, GenMap}
import scala.collection.mutable.Map

import tbd.{Adjustable, Mutator}

abstract class Algorithm(parallel: Boolean, memoized: Boolean) extends Adjustable {
  def initialRun(mutator: Mutator)

  def traditionalRun(input: GenIterable[String])

  def checkOutput(answer: GenMap[Int, String]): Boolean

  def prepareTraditionalRun(input: Map[Int, String]): GenIterable[String] = {
    if(parallel) Vector[String](input.values.toSeq: _*).par else Vector[String](input.values.toSeq: _*)
  }
}