/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.impl.connector.kafka;

import com.hazelcast.jet.JetTestSupport;
import kafka.admin.AdminUtils;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.MockTime;
import kafka.utils.TestUtils;
import kafka.utils.Time;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import kafka.zk.EmbeddedZookeeper;
import org.I0Itec.zkclient.ZkClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Future;

import static kafka.admin.RackAwareMode.Disabled$.MODULE$;

public class KafkaTestSupport extends JetTestSupport {

    private static final String ZK_HOST = "127.0.0.1";
    private static final String BROKER_HOST = "127.0.0.1";
    private static final int SESSION_TIMEOUT = 30000;
    private static final int CONNECTION_TIMEOUT = 30000;

    private EmbeddedZookeeper zkServer;
    private ZkUtils zkUtils;
    private KafkaServer kafkaServer;
    private KafkaProducer<Integer, String> producer;
    private int brokerPort = -1;

    @After
    public void shutdownKafkaCluster() {
        if (kafkaServer != null) {
            if (producer != null) {
                producer.close();
            }
            kafkaServer.shutdown();
            zkUtils.close();
            zkServer.shutdown();

            producer = null;
            kafkaServer = null;
            zkUtils = null;
            zkServer = null;
        }
    }

    final String createKafkaCluster() throws IOException {
        zkServer = new EmbeddedZookeeper();
        String zkConnect = ZK_HOST + ':' + zkServer.port();
        ZkClient zkClient = new ZkClient(zkConnect, SESSION_TIMEOUT, CONNECTION_TIMEOUT, ZKStringSerializer$.MODULE$);
        zkUtils = ZkUtils.apply(zkClient, false);
        brokerPort = getRandomPort();

        Properties brokerProps = new Properties();
        brokerProps.setProperty("zookeeper.connect", zkConnect);
        brokerProps.setProperty("broker.id", "0");
        brokerProps.setProperty("log.dirs", Files.createTempDirectory("kafka-").toAbsolutePath().toString());
        brokerProps.setProperty("listeners", "PLAINTEXT://" + BROKER_HOST + ':' + brokerPort);
        KafkaConfig config = new KafkaConfig(brokerProps);
        Time mock = new MockTime();
        kafkaServer = TestUtils.createServer(config, mock);

        return BROKER_HOST + ':' + brokerPort;
    }

    void createTopic(String topicId) {
        AdminUtils.createTopic(zkUtils, topicId, 1, 1, new Properties(), MODULE$);
    }

    Future<RecordMetadata> produce(String topic, Integer key, String value) {
        return getProducer().send(new ProducerRecord<>(topic, key, value));
    }

    private KafkaProducer<Integer, String> getProducer() {
        if (producer == null) {
            Properties producerProps = new Properties();
            producerProps.setProperty("bootstrap.servers", BROKER_HOST + ':' + brokerPort);
            producerProps.setProperty("key.serializer", IntegerSerializer.class.getCanonicalName());
            producerProps.setProperty("value.serializer", StringSerializer.class.getCanonicalName());
            producer = new KafkaProducer<>(producerProps);
        }
        return producer;
    }

    static KafkaConsumer<String, String> createConsumer(String brokerConnectionString, String... topicIds) {
        Properties consumerProps = new Properties();
        consumerProps.setProperty("bootstrap.servers", brokerConnectionString);
        consumerProps.setProperty("group.id", randomString());
        consumerProps.setProperty("client.id", "consumer0");
        consumerProps.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
        consumerProps.setProperty("value.deserializer", StringDeserializer.class.getCanonicalName());
        // to make sure the consumer starts from the beginning of the topic:
        consumerProps.put("auto.offset.reset", "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Arrays.asList(topicIds));
        return consumer;
    }

    private static int getRandomPort() throws IOException {
        ServerSocket server = null;
        try {
            server = new ServerSocket(0);
            return server.getLocalPort();
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }
}
