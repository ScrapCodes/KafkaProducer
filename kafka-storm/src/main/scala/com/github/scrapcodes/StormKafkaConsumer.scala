/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.github.scrapcodes

import java.util
import java.util.Properties

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.storm.kafka.spout._
import org.apache.storm.task.TopologyContext
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer, TopologyBuilder}
import org.apache.storm.tuple.{Fields, Tuple}
import org.apache.storm.{Config, LocalCluster}

import scala.collection.JavaConverters._

object StormKafkaConsumer {
  var brokerUrl: String = _

  class TupleBuilder[K, V](topic: String) extends KafkaSpoutTupleBuilder[K, V](topic) {
    override def buildTuple(consumerRecord: ConsumerRecord[K, V]): util.List[Object] = {
      util.Arrays.asList[Object](consumerRecord.key().asInstanceOf[java.lang.Object],
        consumerRecord.value().asInstanceOf[java.lang.Object])
    }
  }

  def main(args: Array[String]) {
    brokerUrl = args(0)
    val builder: TopologyBuilder = new TopologyBuilder

    val props: util.Map[String, Object] =
      Map[String, Object]("group.id" -> "kafka-storm-bench",
        "bootstrap.servers" -> brokerUrl,
        "key.deserializer" -> "org.apache.kafka.common.serialization.LongDeserializer",
        "enable.auto.commit" -> "true",
        "compression.type" -> "snappy",
        "acks" -> "0",
        "retries"-> "0",
        "linger.ms" -> "1",
        "buffer.memory" -> 4096000l.toString,
        // "zookeeper.connect" -> "localhost:2181",
        "value.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer").asJava
    val kafkaProps = new util.HashMap[String, Object](props)

    val tupleBuilder = new KafkaSpoutTuplesBuilderNamedTopics
    .Builder[Long, String](Seq(new TupleBuilder[Long, String]("test")): _*)
      .build()
    val kafkaSpoutStreams = new KafkaSpoutStreamsNamedTopics.Builder(new Fields("key", "value"),
      Seq("test"): _*).build()

    val kafkaSpoutConfig: KafkaSpoutConfig[Long, String] =
      new KafkaSpoutConfig.Builder[Long, String](kafkaProps, kafkaSpoutStreams, tupleBuilder)
        .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.LATEST)
        .setPollTimeoutMs(200)
        .setMaxUncommittedOffsets(10)
        .setMaxRetries(1)
        .build()

    builder.setSpout("spout", new KafkaSpout[Long, String](kafkaSpoutConfig), 2)
    builder.setBolt("bolt", new KafkaCustomBolt, 2)
      .fieldsGrouping("spout", new Fields("key", "value"))
    val conf = new Config()
    conf.setDebug(true)
    conf.setNumWorkers(4)
    conf.setMaxTaskParallelism(4)
    val cluster: LocalCluster = new LocalCluster()
    cluster.submitTopology("kafka-latency-bench", conf, builder.createTopology)
    Thread.sleep(10000000)
    cluster.shutdown()
  }
}

object KafkaCustomBolt {
  private lazy val kafkaProducer = initialize(StormKafkaConsumer.brokerUrl)

  def initialize(brokerUrl: String): (KafkaProducer[Long, String]) = {
    val props = new Properties()
    props.put("bootstrap.servers", brokerUrl)
    props.put("acks", "0")
    props.put("retries", "0")
    props.put("batch.size", "100000")
    props.put("linger.ms", "5")
    props.put("group.id", "kafka-storm-out-test")
    props.put("compression.type", "snappy")
    props.put("buffer.memory", "4096000")
    props.put("key.serializer", "org.apache.kafka.common.serialization.LongSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[Long, String](props)
  }

  def close(): Unit = {
    kafkaProducer.close()
  }
}

class KafkaCustomBolt extends BaseBasicBolt {

  override def prepare(stormConf: util.Map[_, _], context: TopologyContext): Unit = {
    kafkaProducer = KafkaCustomBolt.kafkaProducer
  }

  var kafkaProducer: KafkaProducer[Long, String] = _

  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {
    var key = input.getLong(0)
    val value = input.getString(1)
    if (key == null) key = 1L // To prevent storm from emitting NPE.
    kafkaProducer.send(new ProducerRecord[Long, String]("output", key, value))
    kafkaProducer.flush()
  }

  override def cleanup(): Unit = {
    val start = System.currentTimeMillis()
    kafkaProducer.flush()
    val end = System.currentTimeMillis()
    if (end - start > 30) {
      println(s"$start Flush took ${end - start}ms to flush.")
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    // This does not produce a stream.
  }
}

