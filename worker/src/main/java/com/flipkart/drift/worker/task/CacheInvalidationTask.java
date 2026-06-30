package com.flipkart.drift.worker.task;

import com.flipkart.drift.worker.bootstrap.DslCacheManager;
import com.google.inject.Inject;
import io.dropwizard.servlets.tasks.Task;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Dropwizard admin task for DNS-fanout based cache invalidation.
 * Registered on the admin port so the API can POST to each worker pod directly
 * when Redis pub-sub is unavailable.
 *
 * Usage:
 *   POST /tasks/cache-invalidate?type=NODE&key=<rowKey>
 *   POST /tasks/cache-invalidate?type=WORKFLOW&key=<rowKey>
 *   POST /tasks/cache-invalidate?type=NODE&key=ALL   (invalidates entire cache)
 */
@Slf4j
public class CacheInvalidationTask extends Task {

    private final DslCacheManager dslCacheManager;

    @Inject
    public CacheInvalidationTask(DslCacheManager dslCacheManager) {
        super("cache-invalidate");
        this.dslCacheManager = dslCacheManager;
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
        String type = getFirst(parameters, "type");
        String key = getFirst(parameters, "key");

        if (type == null || key == null) {
            output.println("ERROR: required params: type (NODE|WORKFLOW), key (<rowKey> or ALL)");
            return;
        }

        log.info("Cache invalidation task invoked: type={}, key={}", type, key);
        boolean found = dslCacheManager.invalidate(type, key);
        if (found) {
            output.println("OK: invalidated type=" + type + " key=" + key);
        } else {
            output.println("ERROR: unknown cache type: " + type + " (expected NODE or WORKFLOW)");
        }
    }

    private String getFirst(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
