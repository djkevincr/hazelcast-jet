/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hazelcast.util.Preconditions.checkNotNull;

/**
 * Represents vertex of the Direct Acyclic Graph
 */
public class Vertex implements Serializable {
    private String name;
    private String processorClass;
    private Object[] processorArgs;
    private int parallelism = 1;

    private List<Edge> inputEdges = new ArrayList<Edge>();
    private List<Edge> outputEdges = new ArrayList<Edge>();
    private List<Sink> sinks = new ArrayList<Sink>();
    private List<Vertex> inputVertices = new ArrayList<Vertex>();
    private List<Source> sources = new ArrayList<Source>();
    private List<Vertex> outputVertices = new ArrayList<Vertex>();

    /**
     * Constructs a new vertex
     *
     * @param name name of the vertex
     */
    public Vertex(String name) {
        checkNotNull(name);
        this.name = name;
    }

    /**
     * Constructs a new vertex
     *
     * @param name           name of the vertex
     * @param processorClass class name of the processor
     * @param processorArgs  constructor arguments of the processor
     */
    public Vertex(String name, Class<? extends Processor> processorClass, Object... processorArgs) {
        checkNotNull(name);
        this.name = name;
        this.processorClass = processorClass.getName();
        this.processorArgs = processorArgs;
    }

    /**
     * @return name of the vertex
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return number of parallel instances of this vertex
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Sets the number of parallel instances of this vertex
     */
    public Vertex parallelism(int parallelism) {
        this.parallelism = parallelism;
        return this;
    }

    /**
     * @return constructor arguments for the processor
     */
    public Object[] getProcessorArgs() {
        return processorArgs;
    }

    /**
     * Sets the constructor arguments for the processor
     */
    public Vertex processorArgs(Object[] processorArgs) {
        this.processorArgs = processorArgs;
        return this;
    }

    /**
     * @return name of the processor class
     */
    public String getProcessorClass() {
        return processorClass;
    }

    /**
     * Sets the name of the processor class
     */
    public Vertex processorClass(Class processorClass) {
        this.processorClass = processorClass.getName();
        return this;
    }

    /**
     * Add abstract source source object to the vertex
     *
     * @param source corresponding source
     */
    public void addSource(Source source) {
        this.sources.add(source);
    }

    /**
     * Add abstract sink object to the vertex
     *
     * @param sink corresponding sink
     */
    public void addSink(Sink sink) {
        this.sinks.add(sink);
    }

    /**
     * Add outputVertex as  output vertex for the corresponding edge and this vertex
     *
     * @param outputVertex next output vertex
     * @param edge         corresponding edge
     */
    public void addOutputVertex(Vertex outputVertex, Edge edge) {
        this.outputVertices.add(outputVertex);
        this.outputEdges.add(edge);
    }

    /**
     * Add inputVertex as inout  vertex for the corresponding edge and this vertex
     *
     * @param inputVertex previous inout vertex
     * @param edge        corresponding edge
     */
    public void addInputVertex(Vertex inputVertex, Edge edge) {
        this.inputVertices.add(inputVertex);
        this.inputEdges.add(edge);
    }

    /**
     * @return list of the input edges
     */
    public List<Edge> getInputEdges() {
        return Collections.unmodifiableList(this.inputEdges);
    }

    /**
     * @return list of the output edges
     */
    public List<Edge> getOutputEdges() {
        return Collections.unmodifiableList(this.outputEdges);
    }

    /**
     * @return list of the input vertices
     */
    public List<Vertex> getInputVertices() {
        return Collections.unmodifiableList(this.inputVertices);
    }

    /**
     * @return list of the output vertices
     */
    public List<Vertex> getOutputVertices() {
        return Collections.unmodifiableList(this.outputVertices);
    }

    /**
     * @return list of the input sources
     */
    public List<Source> getSources() {
        return Collections.unmodifiableList(this.sources);
    }

    /**
     * @return list of the output sinks
     */
    public List<Sink> getSinks() {
        return Collections.unmodifiableList(this.sinks);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Vertex vertex = (Vertex) o;
        return !(this.name != null ? !this.name.equals(vertex.name) : vertex.name != null);
    }

    @Override
    public int hashCode() {
        return this.name != null ? this.name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Vertex{"
                + "name='" + name + '\''
                + '}';
    }
}