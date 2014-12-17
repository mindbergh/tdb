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
package tbd.reef;

import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.task.Task;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import tbd.reef.param.*;

/**
 * A worker node.
 */
public final class TBDWorkerTask implements Task {
  private static final Logger LOG =
      Logger.getLogger(TBDWorkerTask.class.getName());
  private final int timeout;
  private final String hostIP;
  private final String hostPort;
  private final String masterAkka;

  @Inject
  TBDWorkerTask(@Parameter(TBDDriver.HostIP.class) final String ip,
      @Parameter(TBDDriver.HostPort.class) final String port,
      @Parameter(TBDDriver.MasterAkka.class) final String master,
      @Parameter(TBDLaunch.Timeout.class) final int tout
      ) {
    hostIP = ip;
    hostPort = port;
    masterAkka = master;
    timeout = tout;
  }

  @Override
  public final byte[] call(final byte[] memento) {
    LOG.log(Level.INFO, "start worker");
    LOG.log(Level.INFO, "IP: {0}", hostIP);
    LOG.log(Level.INFO, "port: {0}", hostPort);
    LOG.log(Level.INFO, "master akka: {0}", masterAkka);

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      LOG.log(Level.INFO, "worker sleep interrupted");
    }

    String[] args = {"-i", hostIP, "-p", hostPort, masterAkka};
    tbd.worker.Main.main(args);

    LOG.log(Level.INFO, "worker sleep");
    try {
      Thread.sleep(timeout*60*1000);
    } catch (InterruptedException e) {
      LOG.log(Level.INFO, "worker sleep interrupted");
    }

    LOG.log(Level.INFO, "end worker");
    return null;
  }
}
