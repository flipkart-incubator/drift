package com.drift.api.service.utils;

import com.flipkart.kloud.config.ConfigClient;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Singleton
public class Utility {
    private static final String NO_INCIDENT_WORKFLOWS = "no_incident_workflows";
    private static final String WORKFLOW_ID_PREFIX = "WF-";
    private static final DateTimeFormatter ID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");
    private static final int RANDOM_ID_BOUND = 100000;
    private static final String RANDOM_ID_FORMAT = "%05d";

    /**
     * Generates a workflow ID based on incident creation allowance
     * @param externalIncidentId incident id
     * @param isIncidentCreationAllowed whether incident creation is allowed
     * @return generated workflow ID
     */
    public String generateWorkflowId(String externalIncidentId, boolean isIncidentCreationAllowed) {
       
        if (isIncidentCreationAllowed) {
            return WORKFLOW_ID_PREFIX + externalIncidentId;
        }
        return generateRandomWorkflowId();
    }

    /**
     * Generates a random workflow ID using current timestamp and random number
     * @return random workflow ID
     */
    private String generateRandomWorkflowId() {
        String timestamp = LocalDateTime.now().format(ID_DATE_FORMAT);
        int randomNumber = ThreadLocalRandom.current().nextInt(RANDOM_ID_BOUND);
        return WORKFLOW_ID_PREFIX + timestamp + String.format(RANDOM_ID_FORMAT, randomNumber);
    }

    public static Long publishRedisEvent(JedisSentinelPool jedisSentinelPool, String channel, String message) {
        Jedis jedis = jedisSentinelPool.getResource();
        log.info("Publishing redis event to {}, msg: {}", channel, message);
        Long status = jedis.publish(channel, message);
        jedis.close();
        return status;
    }

}




