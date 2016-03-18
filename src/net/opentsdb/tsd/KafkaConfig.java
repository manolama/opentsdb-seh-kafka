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

import net.opentsdb.utils.Config;

public class KafkaConfig extends Config {
  static final String CONFIG_PREFIX = "seh_kafka";
  static final String KAFKA_CONFIG_PREFIX = CONFIG_PREFIX + ".kafka.";
  
  static final String KAFKA_TOPIC = CONFIG_PREFIX + ".topic";
  
  // Kafka pass through values
  static final String KAFKA_BROKERS = KAFKA_CONFIG_PREFIX + "metadata.broker.list";
  static final String REQUIRED_ACKS = KAFKA_CONFIG_PREFIX + "request.required.acks";
  static final String REQUEST_TIMEOUT = KAFKA_CONFIG_PREFIX + "request.timeout.ms";
  static final String MAX_RETRIES = KAFKA_CONFIG_PREFIX + "send.max_retries";
  static final String PARTITIONER_CLASS = KAFKA_CONFIG_PREFIX + "partitioner.class";
  static final String PRODUCER_TYPE = KAFKA_CONFIG_PREFIX + "producer.type";
  static final String KEY_SERIALIZER = KAFKA_CONFIG_PREFIX + "key.serializer.class";
  
  public KafkaConfig(final Config parent) {
    super(parent);
  }
  
  protected void setDefaults() {
    default_map.put(REQUIRED_ACKS,  "0");
    default_map.put(REQUEST_TIMEOUT, "10000");
    default_map.put(MAX_RETRIES, "1000");
    default_map.put(PRODUCER_TYPE, "async");
    default_map.put(KEY_SERIALIZER, "kafka.serializer.StringEncoder");
    
    for (Map.Entry<String, String> entry : default_map.entrySet()) {
      if (!properties.containsKey(entry.getKey()))
        properties.put(entry.getKey(), entry.getValue());
    }
  }
}
