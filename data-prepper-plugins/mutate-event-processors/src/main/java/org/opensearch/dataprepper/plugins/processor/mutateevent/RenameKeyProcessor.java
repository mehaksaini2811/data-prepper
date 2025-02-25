/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.mutateevent;

import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;

import java.util.Collection;
import java.util.List;

@DataPrepperPlugin(name = "rename_keys", pluginType = Processor.class, pluginConfigurationType = RenameKeyProcessorConfig.class)
public class RenameKeyProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private final List<RenameKeyProcessorConfig.Entry> entries;

    @DataPrepperPluginConstructor
    public RenameKeyProcessor(final PluginMetrics pluginMetrics, final RenameKeyProcessorConfig config) {
        super(pluginMetrics);
        this.entries = config.getEntries();
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
        for(final Record<Event> record : records) {
            final Event recordEvent = record.getData();

            for(RenameKeyProcessorConfig.Entry entry : entries) {
                if(entry.getFromKey().equals(entry.getToKey()) || !recordEvent.containsKey(entry.getFromKey())) {
                    continue;
                }

                if (!recordEvent.containsKey(entry.getToKey()) || entry.getOverwriteIfToKeyExists()) {
                    final Object source = recordEvent.get(entry.getFromKey(), Object.class);
                    recordEvent.put(entry.getToKey(), source);
                    recordEvent.delete(entry.getFromKey());
                }
            }
        }

        return records;
    }

    @Override
    public void prepareForShutdown() {
    }

    @Override
    public boolean isReadyForShutdown() {
        return true;
    }

    @Override
    public void shutdown() {
    }
}
