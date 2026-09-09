package com.fancy.taxiagent.agentbase.capability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityRegistryTest {
    @Test
    void indexesCapabilitiesByName() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of(capability("route.search")));
        assertEquals("route.search", registry.find("route.search").orElseThrow().name());
    }

    @Test
    void rejectsDuplicateNames() {
        assertThrows(IllegalStateException.class,
                () -> new CapabilityRegistry(List.of(capability("route.search"), capability("route.search"))));
    }

    private AgentCapability<String, String> capability(String name) {
        return new AgentCapability<>() {
            public String name() { return name; }
            public Class<String> inputType() { return String.class; }
            public Class<String> outputType() { return String.class; }
            public CapabilityResult<String> execute(String input, ExecutionContext context) {
                return CapabilityResult.success(input);
            }
        };
    }
}
