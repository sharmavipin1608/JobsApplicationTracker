package com.jobtracker.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClientCustomizer httpVersionCustomizer() {
        return builder -> builder.requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
        ));
    }
}
