package com.drift.worker.Utility;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.drift.commons.utils.Constants.PerfConstants.PERF_FLAG;
import static org.apache.commons.lang3.BooleanUtils.TRUE;

@Slf4j
public class WorkerUtility {

    public static boolean shouldAddPerfFlags(Map<String, String> threadContext) {
        if (threadContext == null) {
            return false;
        }

        String perfFlagValue = threadContext.get(PERF_FLAG);
        return TRUE.equalsIgnoreCase(perfFlagValue);
    }
}