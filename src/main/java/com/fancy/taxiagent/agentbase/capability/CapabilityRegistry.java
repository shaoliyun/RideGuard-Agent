package com.fancy.taxiagent.agentbase.capability;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CapabilityRegistry {
    private final Map<String, AgentCapability<?, ?>> capabilities;

    public CapabilityRegistry(List<AgentCapability<?, ?>> discoveredCapabilities) {
        Map<String, AgentCapability<?, ?>> indexed = new LinkedHashMap<>();
        for (AgentCapability<?, ?> capability : discoveredCapabilities) {
            AgentCapability<?, ?> previous = indexed.putIfAbsent(capability.name(), capability);
            if (previous != null) {
                throw new IllegalStateException("Duplicate capability name: " + capability.name());
            }
        }
        this.capabilities = Map.copyOf(indexed);
    }

    public Optional<AgentCapability<?, ?>> find(String name) {
        return Optional.ofNullable(capabilities.get(name));
    }

    public Collection<AgentCapability<?, ?>> all() {
        return capabilities.values();
    }
}
