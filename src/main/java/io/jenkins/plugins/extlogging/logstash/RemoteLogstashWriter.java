
package io.jenkins.plugins.extlogging.logstash;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.extlogging.api.Event;
import io.jenkins.plugins.extlogging.api.impl.ExternalLoggingEventWriter;
import jenkins.model.Jenkins;
import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import net.sf.json.JSONObject;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RemoteLogstashWriter extends ExternalLoggingEventWriter {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(RemoteLogstashWriter.class.getName());

    @CheckForNull
    private final String prefix;
    private final BuildData buildData;
    private final String jenkinsUrl;
    private final LogstashIndexerDao dao;
    private boolean connectionBroken;

    public RemoteLogstashWriter(Run run, TaskListener listener, String prefix, LogstashIndexerDao dao) {
        this.prefix = prefix;
        this.jenkinsUrl = Jenkins.get().getRootUrl();
        this.buildData = new BuildData(run, new Date(), listener);
        this.dao = dao;
    }

    @Override
    public void writeMessage(String message) throws IOException {
        super.writeMessage(prefix != null ? prefix + message : message);
    }

    @Override
    public void writeEvent(Event event) {
        JSONObject payload = dao.buildPayload(buildData, jenkinsUrl,
                Collections.singletonList(event.getMessage()));
        // TODO: replace Dao implementation by an independent one
        JSONObject data = payload.getJSONObject("data");
        for (Map.Entry<String, Object> entry : event.getData().entrySet()) {
            Object value = entry.getValue();
            data.put(entry.getKey(), value != null ? value.toString() : null);
        }

        try {
            dao.push(payload.toString());
        } catch (IOException e) {
            String msg = "[logstash-plugin]: Failed to send log data to " + dao.getDescription() + ".\n"
                    + "[logstash-plugin]: No Further logs will be sent to " + dao.getDescription() + ".\n"
                    + ExceptionUtils.getStackTrace(e);
            logErrorMessage(msg);
        }
    }

    /**
     * @return True if errors have occurred during initialization or write.
     */
    public boolean isConnectionBroken() {
        return connectionBroken || dao == null || buildData == null;
    }

    /**
     * Write error message to errorStream and set connectionBroken to true.
     */
    private void logErrorMessage(String msg) {
        connectionBroken = true;
        LOGGER.log(Level.WARNING, msg);
    }

    @Override
    public void flush() throws IOException {
        // no caching, nothing to do here
    }

    @Override
    public void close() throws IOException {
        // dao handles it
    }
}
