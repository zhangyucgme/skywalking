/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.IModelSetter;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * none streaming is designed for user operation configuration in UI interface. It uses storage (synchronization)
 * similar to Inventory and supports TTL deletion mode similar to the record.
 */
public class NoneStreamingProcessor implements StreamProcessor<NoneStream> {

    private static final NoneStreamingProcessor PROCESSOR = new NoneStreamingProcessor();

    private Map<Class<? extends NoneStream>, NoneStreamPersistentWorker> workers = new HashMap<>();

    public static NoneStreamingProcessor getInstance() {
        return PROCESSOR;
    }

    @Override
    public void in(NoneStream noneStream) {
        final NoneStreamPersistentWorker worker = workers.get(noneStream.getClass());
        if (worker != null) {
            worker.in(noneStream);
        }
    }

    @Override
    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends NoneStream> streamClass) {
        if (DisableRegister.INSTANCE.include(stream.name())) {
            return;
        }

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        INoneStreamDAO noneStream;
        try {
            noneStream = storageDAO.newNoneStreamDao(stream.builder().newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.builder()
                                                            .getSimpleName() + " none stream record DAO failure.", e);
        }

        IModelSetter modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(IModelSetter.class);
        Model model = modelSetter.putIfAbsent(streamClass, stream.scopeId(), new Storage(stream.name(), true, true, Downsampling.Second), true);

        final NoneStreamPersistentWorker persistentWorker = new NoneStreamPersistentWorker(moduleDefineHolder, model, noneStream);
        workers.put(streamClass, persistentWorker);
    }
}
