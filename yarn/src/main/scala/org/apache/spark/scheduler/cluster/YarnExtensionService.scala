/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.scheduler.cluster

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.hadoop.yarn.api.records.{ApplicationAttemptId, ApplicationId}

import org.apache.spark.util.Utils
import org.apache.spark.{Logging, SparkContext}

/**
 * An extension service that can be loaded into a Spark YARN application.
 * A Service that can be started and stopped
 *
 * The `stop()` operation MUST be idempotent, and succeed even if `start()` was
 * never invoked.
 */
trait YarnExtensionService {

  /**
   * Start the extension service. This should be a no-op if
   * called more than once.
   * @param binding binding to the spark application and YARN
   */
  def start(binding: YarnExtensionServiceBinding): Unit

  /**
   * Stop the service
   * The `stop()` operation MUST be idempotent, and succeed even if `start()` was
   * never invoked.
   */
  def stop(): Unit
}

/**
 * Binding information for a [[YarnExtensionService]]
 * @param sparkContext current spark context
 * @param applicationId YARN application ID
 * @param attemptId optional AttemptID.
 */
case class YarnExtensionServiceBinding(
    sparkContext: SparkContext,
    applicationId: ApplicationId,
    attemptId: Option[ApplicationAttemptId] = None)

/**
 * Container for [[YarnExtensionService]] instances.
 *
 * Loads child Yarn extension Services from the configuration property
 * `"spark.yarn.services"`, instantiates and starts them.
 * When stopped, it stops all child entries.
 *
 * The order in which child extension services are started and stopped
 * is undefined.
 *
 */
private[spark] class YarnExtensionServices extends YarnExtensionService
    with Logging {
  private var services: List[YarnExtensionService] = Nil
  private var sparkContext: SparkContext = _
  private var appId: ApplicationId = _
  private var attemptId: Option[ApplicationAttemptId] = _
  private val started = new AtomicBoolean(false)
  private var binding: YarnExtensionServiceBinding = _

  /**
   * Binding operation will load the named services and call bind on them too; the
   * entire set of services are then ready for `init()` and `start()` calls

   * @param binding binding to the spark application and YARN
   */
  def start(binding: YarnExtensionServiceBinding): Unit = {
    if (started.getAndSet(true)) {
      logWarning("Ignoring re-entrant start operation")
      return
    }
    require(binding.sparkContext != null, "Null context parameter")
    require(binding.applicationId != null, "Null appId parameter")
    this.binding = binding
    sparkContext = binding.sparkContext
    appId = binding.applicationId
    attemptId = binding.attemptId
    logInfo(s"Starting Yarn extension services with app ${binding.applicationId}" +
        s" and attemptId $attemptId")

    services = sparkContext.getConf.getOption(YarnExtensionServices.SPARK_YARN_SERVICES)
        .map { s =>
      s.split(",").map(_.trim()).filter(!_.isEmpty)
        .map { sClass =>
            val instance = Utils.classForName(sClass)
                .newInstance()
                .asInstanceOf[YarnExtensionService]
            // bind this service
            instance.start(binding)
            logInfo(s"Service $sClass started")
            instance
          }
    }.map(_.toList).getOrElse(Nil)
  }

  /**
   * Get the list of services
   * @return a list of services; Nil until the service is started
   */
  def getServices: List[YarnExtensionService] = {
    services
  }

  override def stop(): Unit = {
    logInfo(s"Stopping $this")
    services.foreach(_.stop())
  }
}

private[spark] object YarnExtensionServices {

  /**
   * Configuration option to contain a list of comma separated classes to instantiate in the AM
   */
  val SPARK_YARN_SERVICES = "spark.yarn.services"
}
