// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.pubsub.clients.producer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Assert;
import org.junit.rules.ExpectedException;

import java.util.Properties;

import com.google.common.collect.ImmutableMap;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class ConfigTest {

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Test
  public void testSuccessAllConfigsProvided() {
    Properties props = new Properties();
    props.putAll(new ImmutableMap.Builder<>()
        .put("acks", "-1")
        .put("project", "unit-test-project")
        .put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        .put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        .build()
    );

    ProducerConfig testConfig = new ProducerConfig(props);

    Assert.assertEquals("Project config equals unit-test-project.",
        "unit-test-project", testConfig.getString(ProducerConfig.PROJECT_CONFIG));

    Assert.assertNotNull(
        "Key serializer must not be null.", testConfig
            .getConfiguredInstance(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serializer.class));
    Assert.assertEquals(
        "Key serializer config must equal StringSerializer.", StringSerializer.class,
        testConfig.getClass(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));

    Assert.assertNotNull(
        "Value serializer must not be null.", testConfig.
            getConfiguredInstance(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, Serializer.class));
    Assert.assertEquals(
        "Value serializer config must equal StringSerializer.", StringSerializer.class,
        testConfig.getClass(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
  }

  @Test
  public void testNoSerializerProvided() {
    Properties props = new Properties();
    props.putAll(new ImmutableMap.Builder<>()
        .put("topic", "unit-test-topic")
        .put("project", "unit-test-project")
        .build()
    );

    exception.expect(ConfigException.class);
    new ProducerConfig(props);
  }

  @Test
  public void testNoTopicProvided() {
    Properties props = new Properties();
    props.putAll(new ImmutableMap.Builder<>()
        .put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        .put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        .build()
    );

    exception.expect(ConfigException.class);
    new ProducerConfig(props);
  }
}