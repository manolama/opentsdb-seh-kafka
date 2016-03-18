// This file is part of OpenTSDB.
// Copyright (C) 2016  The OpenTSDB Authors.
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 2.1 of the License, or (at your
// option) any later version.  This program is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
// General Public License for more details.  You should have received a copy
// of the GNU Lesser General Public License along with this program.  If not,
// see <http://www.gnu.org/licenses/>.
package net.opentsdb.tsd;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import net.opentsdb.core.IncomingDataPoint;
import net.opentsdb.core.TSDB;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.utils.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stumbleupon.async.Deferred;

/**
 * Handles requeing the data points in Kafka when there is an error storing it
 * in the underlying database
 */
public class KafkaSEH extends StorageExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaSEH.class);
  
  /** The number of data points pushed to the producer */
  final AtomicLong requeued = new AtomicLong();
  
  /** The number of data points that failed serialization */
  final AtomicLong requeued_exceptions = new AtomicLong();
  
  /** The configuration object loaded from the TSDB config file */
  KafkaConfig config;
  
  /** A Kafka producer configuration object */
  ProducerConfig producer_config;
  
  /** A Kafka producer */
  Producer<String, byte[]> producer;
  
  /** The topic we'll publish to */
  String kafka_topic;
  
  /**
   * Default ctor that must be empty to satisfy the Java plugin loader
   */
  public KafkaSEH() {
  }

  @Override
  public void collectStats(final StatsCollector stats) {
    stats.record("kafka.requeued.datapoints", requeued.get());
    stats.record("kafka.requeued.exceptions", requeued_exceptions.get());
    
    final Snapshot snapshot = producer_block_time.getSnapshot();
    stats.record("kafka.requeued.write_time.1pct", snapshot.getValue(0.01));
    stats.record("kafka.requeued.write_time.75pct", snapshot.get75thPercentile());
    stats.record("kafka.requeued.write_time.95pct", snapshot.get95thPercentile());
    stats.record("kafka.requeued.write_time.99pct", snapshot.get99thPercentile());
    stats.record("kafka.requeued.write_time.999pct", snapshot.get999thPercentile());
    stats.record("kafka.requeued.write_time.median", snapshot.getMedian());
  }

  @Override
  public void handleError(final IncomingDataPoint dp, final Exception e) {
    // don't bubble up standard exceptions here, only major issues.
    try {
      int hash = dp.getMetric().hashCode() + dp.getTags().hashCode();
      final KeyedMessage<String, byte[]> data = 
          new KeyedMessage<String, byte[]>(kafka_topic, 
              Integer.toString(Math.abs(hash)),
              JSON.serializeToBytes(dp));
      final long start = System.nanoTime();
      producer.send(data);
      
      requeued.incrementAndGet();
    } catch (final Exception ex) {
      LOG.error("Unexpected exception publishing data", ex);
      requeued_exceptions.incrementAndGet();
    }
  }

  @Override
  public void initialize(final TSDB tsdb) {
    config = new KafkaConfig(tsdb.getConfig());
    setKafkaConfig();
    producer = new Producer<String, byte[]>(producer_config);
    LOG.info("Initialized kafka publisher writing to topic: " + kafka_topic);
  }

  @Override
  public Deferred<Object> shutdown() {
    LOG.warn("Shutting down Kafka requeue plugin");
    if (producer != null) {
      producer.close();
    }
    return Deferred.fromResult(null);
  }

  @Override
  public String version() {
    return "2.2.0-SNAPSHOT";
  }
  
  /**
   * Configures the properties object by running through all of the entries in
   * the config class prefixed with the {@code CONFIG_PREFIX} and adding them
   * to the properties list.
   */
  private void setKafkaConfig() {
    if (!config.hasProperty(KafkaConfig.KAFKA_BROKERS) ||
        config.getString(KafkaConfig.KAFKA_BROKERS) == null ||
        config.getString(KafkaConfig.KAFKA_BROKERS).isEmpty()) {
      throw new IllegalArgumentException("Missing required config object: " + 
          KafkaConfig.KAFKA_BROKERS);
    }
    if (!config.hasProperty(KafkaConfig.KAFKA_TOPIC) ||
        config.getString(KafkaConfig.KAFKA_TOPIC) == null ||
        config.getString(KafkaConfig.KAFKA_TOPIC).isEmpty()) {
      throw new IllegalArgumentException("Missing required config object: " + 
          KafkaConfig.KAFKA_TOPIC);
    }
    kafka_topic = config.getString(KafkaConfig.KAFKA_TOPIC);

    final Properties properties = new Properties();
    for (Map.Entry<String, String> entry : config.getMap().entrySet()) {
      final String key = entry.getKey();
      if (entry.getKey().startsWith(KafkaConfig.KAFKA_CONFIG_PREFIX)) {
        properties.put(key.substring(
            key.indexOf(KafkaConfig.KAFKA_CONFIG_PREFIX) + 
              KafkaConfig.KAFKA_CONFIG_PREFIX.length()), 
            entry.getValue());
      }
    }
    
    producer_config = new ProducerConfig(properties);
  }
  
}
