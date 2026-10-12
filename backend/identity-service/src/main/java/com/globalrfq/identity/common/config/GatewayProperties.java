package com.globalrfq.identity.common.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gateway")
public record GatewayProperties (
        boolean secretEnabled,
        String sharedSecret,
        String headerName){

}
