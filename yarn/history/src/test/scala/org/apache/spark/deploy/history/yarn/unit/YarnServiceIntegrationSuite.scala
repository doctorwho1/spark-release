/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.deploy.history.yarn.unit

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.spark.SparkConf
import org.apache.spark.deploy.history.yarn.testtools.AbstractYarnHistoryTests
import org.apache.spark.deploy.history.yarn.testtools.YarnTestUtils._
import org.apache.spark.scheduler.cluster.{YarnExtensionService, YarnExtensionServiceBinding, YarnExtensionServices}

/**
 * Test the integration with [[YarnExtensionServices]]
 */
class YarnServiceIntegrationSuite extends AbstractYarnHistoryTests {

  override def setupConfiguration(sparkConf: SparkConf): SparkConf = {
    super.setupConfiguration(sparkConf)
    sparkConf.set(YarnExtensionServices.SPARK_YARN_SERVICES,
                   "org.apache.spark.deploy.history.yarn.unit.SimpleService")
  }

  test("Instantiate") {
    val services = new YarnExtensionServices
    assertResult(Nil, "non-nil service list") {
      services.getServices
    }
    services.start(YarnExtensionServiceBinding(sparkCtx, applicationId))
    services.stop()
  }

  test("Contains History Service") {
    val services = new YarnExtensionServices()
    try {
      services.start(YarnExtensionServiceBinding(sparkCtx, applicationId))
      val serviceList = services.getServices
      assert(serviceList.nonEmpty, "empty service list")
      val (service :: Nil) = serviceList
      val simpleService = service.asInstanceOf[SimpleService]
      assert(simpleService.started.get(), "service not started")
      services.stop()
      assert(!simpleService.started.get(), "service not stopped")
    } finally {
      services.stop()
    }
  }
}

class SimpleService extends YarnExtensionService {

  val started = new AtomicBoolean(false)

  /**
   * Start the extension service. This should be a no-op if called more than once.
   *
   * @param binding binding to the spark application and YARN
   */
  override def start(binding: YarnExtensionServiceBinding): Unit = {
    started.set(true)
  }

  override def stop(): Unit = {
    started.set(false)
  }
}
