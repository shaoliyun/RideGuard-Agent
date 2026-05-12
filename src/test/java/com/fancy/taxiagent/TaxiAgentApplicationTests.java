package com.fancy.taxiagent;

import com.fancy.taxiagent.agentbase.amap.pojo.route.AmapRoute;
import com.fancy.taxiagent.agentbase.amap.service.AmapRouteService;
import com.fancy.taxiagent.agentbase.memory.MessageMemory;
import com.fancy.taxiagent.agentbase.qweather.pojo.weather.QweatherDaily;
import com.fancy.taxiagent.agentbase.qweather.pojo.weather.QweatherNow;
import com.fancy.taxiagent.agentbase.qweather.pojo.weather.QweatherHourly;
import com.fancy.taxiagent.agentbase.qweather.pojo.weather.QweatherMinutely;
import com.fancy.taxiagent.agentbase.qweather.pojo.weather.QweatherMinutelyResponse;
import com.fancy.taxiagent.agentbase.qweather.service.QweatherWeatherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Slf4j
class TaxiAgentApplicationTests {
    @Resource
    private MessageMemory messageMemory;

    @Test
    public void getLastN(){
        log.info(messageMemory.get("1877239021464317952", "13adc039-b1ba-4fbf-8e64-fe00372021ad", 100).toString());
    }
}
