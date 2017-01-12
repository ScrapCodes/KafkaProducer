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
package com.github.scrapcodes.kafka

import java.io.InputStream
import java.util.Properties

import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest._

class SingleNodeLatencyTest extends FunSuite with BeforeAndAfterAll {
  @transient private var _sc: SparkContext = _
  var conf = new SparkConf(false)

  protected def withFixture(test: Nothing): Outcome = {
    Succeeded
  }

  val prop = new Properties()

  override def beforeAll(): Unit = {
    _sc = new SparkContext("local[4]", "test", conf)
    val filename = "test.properties"
    val input: InputStream = this.getClass.getClassLoader.getResourceAsStream(filename)
    prop.load(input)
  }

  test("Sanity test run a short trial.") {
    val a: Array[Int] = _sc.parallelize(1 to 10).collect()
    assert(a sameElements (1 to 10))
  }

  test("Loading configuration for test.") {
    assert(prop.getProperty("kb.brokerUrl") != null)
    println(prop.getProperty("kb.brokerUrl"))
  }

  override def afterAll() {
    _sc.stop()
    _sc = null
  }
}
