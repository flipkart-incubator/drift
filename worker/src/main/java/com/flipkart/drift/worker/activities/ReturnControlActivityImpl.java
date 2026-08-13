package com.flipkart.drift.worker.activities;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolAbstract;

import static com.flipkart.drift.commons.utils.Constants.Workflow.ASYNC_AWAIT_CHANNEL;

@Slf4j
public class ReturnControlActivityImpl implements ReturnControlActivity {

    @Inject(optional = true)
    private JedisPoolAbstract jedisSentinelPool;

    @Inject
    public ReturnControlActivityImpl() {
    }

    public Long exec(String workflowId) {
        Jedis jedis = jedisSentinelPool.getResource();
        log.info("Publishing redis event to {}", ASYNC_AWAIT_CHANNEL + workflowId);
        Long status = jedis.publish(ASYNC_AWAIT_CHANNEL + workflowId, "return");
        jedis.close();
        return status;
    }
}
