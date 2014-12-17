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

import akka.util.Timeout
import java.net.InetAddress
import scala.concurrent.duration._

object Constants {
  var DURATION = 10.seconds
  implicit var TIMEOUT = Timeout(DURATION)

  type WorkerId = Short

  // The first 16 bits of a TaskId represent the Worker the Task is running on.
  // This allows us to generate unique ids in parallel.
  type TaskId = Int

  // The size of TaskIds, in bytes.
  val taskIdSize = 4

  // The first 32 bits of a ModId represent the TaskId where the Mod was
  // created. We can use this to request the value of the Mod from the Worker
  // that owns it.
  type ModId = Long

  // The size of ModIds, in bytes.
  val modIdSize = 8

  def getWorkerId(modId: ModId): WorkerId = {
    (modId >> 48).toShort
  }

  def incrementWorkerId(workerId: WorkerId): WorkerId = {
    (workerId + 1).toShort
  }

  type InputId = Int

  type Pointer = Long

  // The size of Pointers, in bytes.
  val pointerSize = 8

  type ModizerId = Int

  // The size of ModizerIds, in bytes.
  val modizerIdSize = 4

  type MemoizerId = Int

  // The size of MemoizerIds, in bytes.
  val memoizerIdSize = 4

  val localhost = InetAddress.getLocalHost.getHostAddress

  val akkaConf = """
    akka.actor.provider = akka.remote.RemoteActorRefProvider

    akka.log-dead-letters = off

    akka.remote.enabled-transports = [akka.remote.netty.tcp]

    akka.remote.netty.tcp.maximum-frame-size = 80000000b
  """
}
