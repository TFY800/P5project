package com.p5auction.auction.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
public class HttpClients {
    @Bean RestClient restClient() {
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var factory=new JdkClientHttpRequestFactory(client);factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(factory).build();
    }
}
