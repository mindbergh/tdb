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
package tbd.master

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import scala.collection.mutable.{Buffer, Map}
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}

import tbd.{Adjustable, TBD}
import tbd.Constants._
import tbd.datastore.Datastore
import tbd.messages._
import tbd.worker.{Task, Worker}

object Master {
  def props(): Props = Props(classOf[Master])
}

class Master extends Actor with ActorLogging {
  import context.dispatcher

  log.info("Master launched.")

  private val workers = Buffer[ActorRef]()

  // Maps workerIds to Datastores.
  private val datastoreRefs = Map[String, ActorRef]()

  // Maps mutatorIds to the Task the mutator's computation was launched on.
  private val tasks = Map[Int, ActorRef]()

  private var nextMutatorId = 0

  private var nextWorkerId = 0

  // The next Worker to schedule a Task on.
  private var nextWorker = 0

  def receive = {
    // Worker
    case RegisterWorkerMessage(workerRef: ActorRef, datastoreRef: ActorRef) =>
      log.info("Registering worker at " + workerRef)

      workers += workerRef

      val workerId = nextWorkerId + ""
      datastoreRefs(workerId) = datastoreRef

      sender ! workerId
      nextWorkerId += 1

      for ((thatWorkerId, thatDatastoreRef) <- datastoreRefs) {
	thatDatastoreRef ! RegisterDatastoreMessage(workerId, datastoreRef)
	datastoreRef ! RegisterDatastoreMessage(thatWorkerId, thatDatastoreRef)
      }

    case ScheduleTaskMessage(parent: ActorRef) =>
      (workers(nextWorker) ? ScheduleTaskMessage(parent)) pipeTo sender
      nextWorker = (nextWorker + 1) % workers.size

    // Mutator
    case RegisterMutatorMessage =>
      log.info("Registering mutator " + nextMutatorId)
      sender ! nextMutatorId
      nextMutatorId += 1

    case RunMutatorMessage(adjust: Adjustable[_], mutatorId: Int) =>
      log.info("Starting initial run for mutator " + mutatorId)

      val taskRefFuture = workers(nextWorker) ?
        ScheduleTaskMessage(workers(nextWorker))
      nextWorker = (nextWorker + 1) % workers.size
      val taskRef = Await.result(taskRefFuture.mapTo[ActorRef], DURATION)

      (taskRef ? RunTaskMessage(adjust)) pipeTo sender

      tasks(mutatorId) = taskRef

    case PropagateMutatorMessage(mutatorId: Int) =>
      log.info("Initiating change propagation for mutator " + mutatorId)

      (tasks(mutatorId) ? PropagateTaskMessage) pipeTo sender

    case GetMutatorDDGMessage(mutatorId: Int) =>
      (tasks(mutatorId) ? GetTaskDDGMessage) pipeTo sender

    case ShutdownMutatorMessage(mutatorId: Int) =>
      log.info("Shutting down mutator " + mutatorId)
      (tasks(mutatorId) ? ShutdownTaskMessage) pipeTo sender

    // Datastore
    case CreateModMessage(value: Any) =>
      (workers(nextWorker) ? CreateModMessage(value)) pipeTo sender
      nextWorker = (nextWorker + 1) % workers.size

    case CreateModMessage(null) =>
      (workers(nextWorker) ? CreateModMessage(null)) pipeTo sender
      nextWorker = (nextWorker + 1) % workers.size

    case GetModMessage(modId: ModId, null) =>
      val workerId = modId.split(":")(0)
      (datastoreRefs(workerId) ? GetModMessage(modId, null)) pipeTo sender

    case UpdateModMessage(modId: ModId, value: Any, null) =>
      val workerId = modId.split(":")(0)
      (datastoreRefs(workerId) ? UpdateModMessage(modId, value, null)) pipeTo sender

    case UpdateModMessage(modId: ModId, null, null) =>
      val workerId = modId.split(":")(0)
      (datastoreRefs(workerId) ? UpdateModMessage(modId, null, null)) pipeTo sender

    case x =>
      log.warning("Master actor received unhandled message " +
		  x + " from " + sender + " " + x.getClass)
  }
}
