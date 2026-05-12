package com.fancy.taxiagent.agentbase.qweather.pojo.life;

import java.util.List;

public record QweatherAirPollutant(
        String code,
        String name,
        String fullName,
        QweatherAirConcentration concentration,
        List<QweatherAirSubIndex> subIndexes
) {
}
