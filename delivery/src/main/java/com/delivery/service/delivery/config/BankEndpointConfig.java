package com.delivery.service.delivery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "banks")
@PropertySource(value = "classpath:banks.yml", factory = YamlPropertySourceFactory.class)
public class BankEndpointConfig {

    private List<BankEndpoint> endpoints;
}
