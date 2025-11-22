package com.drift.worker.stringResolver;

import java.util.Map;

public interface StringResolver {
    String resolve(String template, Map<String, Object> context);
}
