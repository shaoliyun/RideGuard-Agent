package com.fancy.taxiagent.agentbase.workflow;

import com.fancy.taxiagent.constant.RedisKeyConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderWorkflowService {
    public static final String STATE_FIELD = "orderWorkflowState";
    public static final String ORDER_ID_FIELD = "OrderId";

    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then current = ARGV[2] end
            if current ~= ARGV[2] then return 0 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public OrderWorkflowService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public OrderWorkflowState current(String chatId) {
        Object raw = redisTemplate.opsForHash().get(key(chatId), STATE_FIELD);
        return raw == null ? OrderWorkflowState.COLLECTING : OrderWorkflowState.valueOf(raw.toString());
    }

    public boolean transition(String chatId, OrderWorkflowState expected, OrderWorkflowState target) {
        if (!expected.canTransitionTo(target)) {
            throw new IllegalArgumentException("Illegal order workflow transition: " + expected + " -> " + target);
        }
        Long changed = redisTemplate.execute(
                TRANSITION_SCRIPT,
                List.of(key(chatId)),
                STATE_FIELD,
                expected.name(),
                target.name());
        boolean success = Long.valueOf(1L).equals(changed);
        Counter.builder("taxiagent.order.workflow.transitions")
                .tag("from", expected.name())
                .tag("to", target.name())
                .tag("result", success ? "success" : "rejected")
                .register(meterRegistry)
                .increment();
        return success;
    }

    public void resetToCollecting(String chatId) {
        redisTemplate.opsForHash().put(key(chatId), STATE_FIELD, OrderWorkflowState.COLLECTING.name());
    }

    public void markQuoted(String chatId) {
        redisTemplate.opsForHash().put(key(chatId), STATE_FIELD, OrderWorkflowState.QUOTED.name());
    }

    public String createdOrderId(String chatId) {
        Object orderId = redisTemplate.opsForHash().get(key(chatId), ORDER_ID_FIELD);
        return orderId == null ? null : orderId.toString();
    }

    private String key(String chatId) {
        return RedisKeyConstants.chatInfoKey(chatId);
    }
}
