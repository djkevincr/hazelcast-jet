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

package com.hazelcast.jet;

import com.hazelcast.jet.function.DistributedFunction;
import com.hazelcast.jet.config.EdgeConfig;
import com.hazelcast.jet.impl.SerializationConstants;
import com.hazelcast.jet.impl.execution.init.CustomClassLoadedObject;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.util.UuidUtil;

import java.io.IOException;
import java.io.Serializable;

import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;
import static com.hazelcast.jet.Partitioner.defaultPartitioner;
import static com.hazelcast.jet.impl.util.Util.checkSerializable;

/**
 * Represents an edge between two {@link Vertex vertices} in a {@link DAG}.
 * Conceptually, data travels over the edge from the source vertex to the
 * destination vertex. Practically, since the vertex is distributed across
 * the cluster and across threads in each cluster member, the edge is
 * implemented by a number of concurrent queues and network sender/receiver
 * pairs.
 * <p>
 * It is often desirable to arrange that all items belonging to the same
 * collation key are received by the same processing unit (instance of
 * {@link Processor}). This is achieved by configuring an appropriate
 * {@link Partitioner} on the edge. The partitioner will determine the
 * partition ID of each item and all items with the same partition ID will
 * be routed to the same {@code Processor} instance. Depending on the value
 * of edge's <em>distributed</em> property, the processor will be unique
 * cluster-wide, or only within each member.
 * <p>
 * A newly instantiated Edge is non-distributed with a {@link
 * RoutingPolicy#UNICAST UNICAST} routing policy.
 */
public class Edge implements IdentifiedDataSerializable {

    private Vertex source; // transient field
    private String sourceName;
    private int sourceOrdinal;

    private Vertex destination; // transient field
    private String destName;
    private int destOrdinal;

    private int priority;
    private boolean isBuffered;
    private boolean isDistributed;
    private Partitioner<?> partitioner;
    private RoutingPolicy routingPolicy = RoutingPolicy.UNICAST;

    private EdgeConfig config;

    Edge() {
    }

    private Edge(Vertex source, int sourceOrdinal, Vertex destination, int destOrdinal) {
        this.source = source;
        this.sourceName = source.getName();
        this.sourceOrdinal = sourceOrdinal;

        this.destination = destination;
        this.destName = destination != null ? destination.getName() : null;
        this.destOrdinal = destOrdinal;
    }

    /**
     * Returns an edge between two vertices. The ordinal of the edge
     * is 0 at both ends. Equivalent to {@code from(source).to(destination)}.
     *
     * @param source        the source vertex
     * @param destination   the destination vertex
     */
    public static Edge between(Vertex source, Vertex destination) {
        return new Edge(source, 0, destination, 0);
    }

    /**
     * Returns an edge with the given source vertex and no destination vertex.
     * The ordinal of the edge is 0. Typically followed by one of the
     * {@code to()} method calls.
     */
    public static Edge from(Vertex source) {
        return from(source, 0);
    }

    /**
     * Returns an edge with the given source vertex at the given ordinal
     * and no destination vertex. Typically follewed by a call to one of
     * the {@code to()} methods.
     */
    public static Edge from(Vertex source, int ordinal) {
        return new Edge(source, ordinal, null, 0);
    }

    /**
     * Sets the destination vertex of this edge, with ordinal 0.
     */
    public Edge to(Vertex destination) {
        this.destination = destination;
        this.destName = destination.getName();
        return this;
    }

    /**
     * Sets the destination vertex and ordinal of this edge.
     */
    public Edge to(Vertex destination, int ordinal) {
        this.destination = destination;
        this.destName = destination.getName();
        this.destOrdinal = ordinal;
        return this;
    }

    /**
     * Returns this edge's source vertex.
     */
    public Vertex getSource() {
        return source;
    }

    /**
     * Returns this edge's destination vertex.
     */
    public Vertex getDestination() {
        return destination;
    }

    /**
     * Returns the name of the source vertex.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Returns the ordinal of the edge at the source vertex.
     */
    public int getSourceOrdinal() {
        return sourceOrdinal;
    }

    /**
     * Returns the name of the destination vertex.
     */
    public String getDestName() {
        return destName;
    }

    /**
     * Returns the ordinal of the edge at the destination vertex.
     */
    public int getDestOrdinal() {
        return destOrdinal;
    }

    /**
     * Sets the priority of the edge. A lower number means higher priority
     * and the default is 0.
     * <p>
     * Example: there two incoming edges on a vertex, with priorities 1 and 2.
     * The data from the edge with priority 1 will be processed in full before
     * accepting any data from the edge with priority 2.
     */
    public Edge priority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Returns the value of edge's <em>priority</em>, as explained on
     * {@link #priority(int)}.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Activates unbounded buffering on this edge. Normally this should be
     * avoided, but at some points the logic of the DAG requires it. This is
     * one scenario: a vertex sends output to two edges, creating a fork in the
     * DAG. The branches later rejoin at a downstream vertex which assigns
     * different priorities to its two inbound edges. The one with the lower
     * priority won't be consumed until the higher-priority one is consumed in
     * full. However, since the data for both edges is generated simultaneously,
     * and since the lower-priority input will apply backpressure while waiting
     * for the higher-priority input to be consumed, this will result in a
     * deadlock. The deadlock is resolved by activating unbounded buffering on
     * the lower-priority edge.
     * <p>
     * <strong>NOTE:</strong> when this feature is activated, the
     * {@link EdgeConfig#setOutboxCapacity(int) outbox capacity} property of
     * {@code EdgeConfig} is ignored and the maximum value is used.
     */
    public Edge buffered() {
        isBuffered = true;
        return this;
    }

    /**
     * Returns whether {@link #buffered() unbounded buffering} is activated for
     * this edge.
     */
    public boolean isBuffered() {
        return isBuffered;
    }

    /**
     * Activates the {@link RoutingPolicy#PARTITIONED PARTITIONED} routing
     * policy and applies the {@link Partitioner#defaultPartitioner() default}
     * Hazelcast partitioning strategy. The strategy is applied to the result of
     * the {@code extractKeyF} function.
     */
    public <T> Edge partitioned(DistributedFunction<T, ?> extractKeyF) {
        return partitioned(extractKeyF, defaultPartitioner());
    }

    /**
     * Activates the {@link RoutingPolicy#PARTITIONED PARTITIONED} routing
     * policy and applies the provided partitioning strategy. The strategy
     * is applied to the result of the {@code extractKeyF} function.
     */
    public <T, K> Edge partitioned(DistributedFunction<T, K> extractKeyF, Partitioner<? super K> partitioner) {
        checkSerializable(extractKeyF, "extractKeyF");
        checkSerializable(partitioner, "partitioner");
        this.routingPolicy = RoutingPolicy.PARTITIONED;
        this.partitioner = new KeyPartitioner<>(extractKeyF, partitioner);
        return this;
    }

    /**
     * Activates a special-cased {@link RoutingPolicy#PARTITIONED PARTITIONED}
     * routing policy where all items will be assigned the same, randomly
     * chosen partition ID. Therefore all items will be directed to the same
     * processor.
     */
    public Edge allToOne() {
        return partitioned(wholeItem(), new Single());
    }

    /**
     * Activates the {@link RoutingPolicy#BROADCAST BROADCAST} routing policy.
     */
    public Edge broadcast() {
        routingPolicy = RoutingPolicy.BROADCAST;
        return this;
    }

    /**
     * Activates the {@link RoutingPolicy#ISOLATED ISOLATED} routing policy
     * which establishes isolated paths from upstream to downstream processors.
     * Each downstream processor is assigned exactly one upstream processor and
     * each upstream processor is assigned a disjoint subset of downstream
     * processors. This allows the selective application of backpressure to
     * just one source processor that feeds a given downstream processor.
     * <p>
     * These restrictions imply that the downstream's local parallelism
     * cannot be less than upstream's. Since all traffic will be local, this
     * policy doesn't make sense on a distributed edge.
     */
    public Edge isolated() {
        routingPolicy = RoutingPolicy.ISOLATED;
        return this;
    }

    /**
     * Returns the instance encapsulating the partitioning strategy in effect
     * on this edge.
     */
    public Partitioner<?> getPartitioner() {
        return partitioner;
    }

    /**
     * Returns the {@link RoutingPolicy} in effect on the edge.
     */
    public RoutingPolicy getRoutingPolicy() {
        return routingPolicy;
    }

    /**
     * Declares that the edge is distributed. A non-distributed edge only
     * transfers data within the same member. If the data source running on
     * local member is distributed (produces only a slice of all the data on
     * any given member), the local processors will not observe all the data.
     * The same holds true when the data originates from an upstream
     * distributed edge.
     * <p>
     * A <em>distributed</em> edge allows all the data to be observed by all
     * the processors (using the {@link RoutingPolicy#BROADCAST BROADCAST}
     * routing policy) and, more attractively, all the data with a given
     * partition ID to be observed by the same unique processor, regardless of
     * whether it is running on the local or a remote member (using the {@link
     * RoutingPolicy#PARTITIONED PARTITIONED} routing policy).
     */
    public Edge distributed() {
        isDistributed = true;
        return this;
    }

    /**
     * Says whether this edge is <em>distributed</em>. The effects of this
     * property are discussed in {@link #distributed()}.
     */
    public boolean isDistributed() {
        return isDistributed;
    }

    /**
     * Returns the {@code EdgeConfig} instance associated with this edge.
     */
    public EdgeConfig getConfig() {
        return config;
    }

    /**
     * Assigns an {@code EdgeConfig} to this edge.
     */
    public Edge setConfig(EdgeConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        if (sourceOrdinal == 0 && destOrdinal == 0) {
            b.append("between(\"").append(sourceName).append("\", \"").append(destName).append("\")");
        } else {
            b.append("from(\"").append(sourceName).append('"');
            if (sourceOrdinal != 0) {
                b.append(", ").append(sourceOrdinal);
            }
            b.append(").to(\"").append(destName).append('"');
            if (destOrdinal != 0) {
                b.append(", ").append(destOrdinal);
            }
            b.append(')');
        }
        switch (getRoutingPolicy()) {
            case UNICAST:
                break;
            case ISOLATED:
                b.append(".isolated()");
                break;
            case PARTITIONED:
                b.append(getPartitioner() instanceof Single ? ".allToOne()" : ".partitioned(?)");
                break;
            case BROADCAST:
                b.append(".broadcast()");
                break;
            default:
        }
        if (isDistributed()) {
            b.append(".distributed()");
        }
        return b.toString();
    }

    @Override
    public boolean equals(Object obj) {
        final Edge that;
        return this == obj
                || obj instanceof Edge
                    && this.sourceName.equals((that = (Edge) obj).sourceName)
                    && this.destName.equals(that.destName);
    }

    @Override
    public int hashCode() {
        return 37 * sourceName.hashCode() + destName.hashCode();
    }


    // Implementation of IdentifiedDataSerializable

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeUTF(sourceName);
        out.writeInt(sourceOrdinal);
        out.writeUTF(destName);
        out.writeInt(destOrdinal);
        out.writeInt(priority);
        out.writeBoolean(isBuffered);
        out.writeBoolean(isDistributed);
        out.writeObject(routingPolicy);
        CustomClassLoadedObject.write(out, partitioner);
        out.writeObject(config);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        sourceName = in.readUTF();
        sourceOrdinal = in.readInt();
        destName = in.readUTF();
        destOrdinal = in.readInt();
        priority = in.readInt();
        isBuffered = in.readBoolean();
        isDistributed = in.readBoolean();
        routingPolicy = in.readObject();
        partitioner = CustomClassLoadedObject.read(in);
        config = in.readObject();
    }

    @Override
    public int getFactoryId() {
        return SerializationConstants.FACTORY_ID;
    }

    @Override
    public int getId() {
        return SerializationConstants.EDGE;
    }

    // END Implementation of IdentifiedDataSerializable


    /**
     * An edge describes a connection from many upstream processors to many
     * downstream processors. The routing policy decides where exactly to route
     * each particular item emitted from an upstream processor. To simplify
     * the reasoning we introduce the concept of the <em>set of candidate
     * downstream processors</em>, or the <em>candidate set</em> for short. On
     * a local edge the candidate set contains only local processors and on a
     * distributed edge it contain all the processors.
     */
    public enum RoutingPolicy implements Serializable {
        /**
         * For each item a single destination processor is chosen from the
         * candidate set, with no restriction on the choice.
         */
        UNICAST,
        /**
         * Like {@link #UNICAST}, but guarantees that any given downstream
         * processor receives data from exactly one upstream processor. This is
         * needed in some DAG setups to apply selective backpressure to individual
         * upstream source processors.
         * <p>
         * The downstream's local parallelism must not be less than the upstream's.
         * This policy is only available on a local edge.
         */
        ISOLATED,
        /**
         * Each item is sent to the one processor responsible for the item's
         * partition ID. On a distributed edge the processor is unique across the
         * cluster; on a non-distributed edge the processor is unique only within a
         * member.
         */
        PARTITIONED,
        /**
         * Each item is sent to all candidate processors.
         */
        BROADCAST
    }

    private static class Single implements Partitioner<Object> {

        private static final long serialVersionUID = 1L;

        private final String key;
        private int partition;

        Single() {
            key = UuidUtil.newUnsecureUuidString();
        }

        @Override
        public void init(DefaultPartitionStrategy strat) {
            partition = strat.getPartition(key);
        }

        @Override
        public int getPartition(Object item, int partitionCount) {
            return partition;
        }
    }

    private static final class KeyPartitioner<T, K> implements Partitioner<T> {

        private static final long serialVersionUID = 1L;

        private final DistributedFunction<T, K> keyExtractor;
        private final Partitioner<? super K> partitioner;

        KeyPartitioner(DistributedFunction<T, K> keyExtractor, Partitioner<? super K> partitioner) {
            this.keyExtractor = keyExtractor;
            this.partitioner = partitioner;
        }

        @Override
        public void init(DefaultPartitionStrategy strat) {
            partitioner.init(strat);
        }

        @Override
        public int getPartition(T item, int partitionCount) {
            return partitioner.getPartition(keyExtractor.apply(item), partitionCount);
        }
    }
}
