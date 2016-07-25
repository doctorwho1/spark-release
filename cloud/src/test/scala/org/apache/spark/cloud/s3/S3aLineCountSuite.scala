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

package org.apache.spark.cloud.s3

import org.apache.hadoop.fs.Path

import org.apache.spark.cloud.CloudSuite
import org.apache.spark.cloud.s3.examples.{S3FileGenerator, S3LineCount}

/**
 * Test the `S3LineCount` entry point.
 */
private[cloud] class S3aLineCountSuite extends CloudSuite with S3aTestSetup {

  init()

  def init(): Unit = {
    // propagate S3 credentials
    if (enabled) {
      initFS()
    }
  }

  override def enabled: Boolean = super.enabled && hasCSVTestFile

  ctest("S3ALineCount",
    "S3A Line count",
    "Execute the S3ALineCount example") {
    val sourceFile = CSV_TESTFILE.get
    val conf = newSparkConf(sourceFile)
    conf.setAppName("S3LineCount")
    val destDir = filesystem.makeQualified(new Path(TestDir, "s3alinecount"))
    assert(0 === S3LineCount.action(conf,
      Array(sourceFile.toString, destDir.toString)))
    val status = filesystem.getFileStatus(destDir)
    assert(status.isDirectory, s"Not a directory: $status")
    val files = filesystem.listStatus(destDir,
      pathFilter(p => p.getName != "_SUCCESS"))
    var size = 0L
    var filenames = ""
    files.foreach { f =>
      size += f.getLen
      filenames = filenames + " " + f.getPath.getName
    }
    logInfo(s"total size = $size bytes from ${files.length} files: $filenames")
    val sourceInfo = filesystem.getFileStatus(sourceFile)
    assert (size >= sourceInfo.getLen, s"output data $size smaller than source $sourceFile")

  }

}
