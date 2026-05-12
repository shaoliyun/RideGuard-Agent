package com.fancy.taxiagent.agentbase.qweather.pojo.weather;

import java.util.List;

public record QweatherRefer(
        List<String> sources,
        List<String> license
) {
}
