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

package com.hazelcast.jet.stream.impl.processor;

import com.hazelcast.jet.AbstractProcessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DistinctProcessor<T> extends AbstractProcessor {

    private Iterator<T> iterator;
    private final Map<T, Boolean> map = new HashMap<>();

    public DistinctProcessor() {
    }

    @Override
    protected boolean process(int ordinal, Object item) {
        map.put((T) item, true);
        return true;
    }

    @Override
    public boolean complete() {
        if (iterator == null) {
            iterator = map.keySet().iterator();
        }
        while (iterator.hasNext()) {
            T key = iterator.next();
            emit(key);
        }
        return true;
    }
}