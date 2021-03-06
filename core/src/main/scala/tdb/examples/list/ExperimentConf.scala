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
package tdb.examples.list

import org.rogach.scallop._

class ExperimentConf(_args: Array[String]) extends ScallopConf(_args) {
  version("TDB 0.1 (c) 2014 Carnegie Mellon University")
  banner("Usage: experiment.sh [options]")
  val algorithms = opt[List[String]]("algorithms", 'a',
    default = Some(List("wc")), descr = "Algorithms to run, where s " +
    "could be: filter, flatMap, join, map, msort, pgrank, qsort, rbk, " +
    "sjoin, split, or wc.")
  val cacheSizes = opt[List[String]]("cacheSizes", 'h',
    default = Some(List("10000")),
    descr = "The size of the cache.")
  val check = opt[Boolean]("check", 'c', default = Some(false),
    descr = "Turns output checking on, for debugging.")
  val chunkSizes = opt[List[String]]("chunkSizes", 's',
    default = Some(List("1")),
    descr = "Size of each chunk, by number of elements")
  val counts = opt[List[String]]("counts",
    default = Some(List("1000")),
    descr = "Number of elements to load initially.")
  val envHomePath = opt[String]("envHomePath",
    default = Some("/tmp/tdb_berkeleydb"), descr = "If using berkeleydb," +
    "the path to where the database should be stored.")
  val files = opt[List[String]]("files", 'f', default = Some(List("")),
    descr = "The files to load the input from.")
  val displayGC = opt[Boolean]("displayGC", default = Some(false),
    descr = "Should gc times be included in the output.")
  val displayLoad = opt[Boolean]("displayLoad", default = Some(false),
    descr = "If true, loading times will be included in output.")
  val master = opt[String]("master", default = Some(""),
    descr = "The master Akka url to connect to. If unspecified, we'll " +
    "launch a master.")
  val mutations = opt[List[String]]("mutations",
    default = Some(List("insert", "update", "remove")),
    descr = "Mutations to perform on the input data. Must be one of " +
    "'update', 'insert', or 'remove'.")
  val naive = opt[Boolean]("naive", 'n', default = Some(false),
    descr = "If true, run a non-incremental version of the algorithm for" +
    " comparison")
  val output = opt[List[String]]("output", 'o',
    default = Some(List("algorithms", "runs", "counts")),
    descr = "How to format the printed results - each of 'chart', " +
    "'line', and 'x' must be one of 'algorithms', 'chunkSizes', " +
    "'counts', 'partitons', or 'runs', with one required to be 'runs'.")
  val partitions = opt[List[String]]("partitions", 'p',
    default = Some(List("8")),
    descr = "Number of partitions to divide the input into.")
  val repeat = opt[Int]("repeat", 'q', default = Some(3),
    descr = "The number of times to repeat the test.")
  val runs = opt[List[String]]("runs", 'r',
    default = Some(List("1", "10")),
    descr = "What test runs to execute. 'naive' and 'initial' are " +
    "included automatically, so this is a list of update sizes (f >= 1) " +
    "or update percentages (0 < f < 1).")
  val store = opt[String]("store", 'w', default = Some("memory"),
    descr = "The data store type to use - memory or berkeleydb.")
  val timeout = opt[Int]("timeout", 't', default = Some(1000))
  val updateFile = opt[String]("updateFile", 'u', default = Some("updates.txt"),
    descr = "The file to read the updates from, if needed.")
  val verbosity = opt[Int]("verbosity", 'v', default = Some(1),
    descr = "Adjusts the amount of output, with 0 indicating no output.")
}
