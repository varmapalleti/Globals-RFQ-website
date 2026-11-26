package com.globalrfq.rfq.config;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class RfqDataSourceConfiguration {

    @Bean
    static BeanFactoryPostProcessor validateRfqJdbcUrl(Environment environment) {
        return beanFactory -> {
            String url = environment.getProperty("spring.datasource.url");
            if (url == null || !url.startsWith("jdbc:")) {
                throw new BeanDefinitionStoreException(
                        "Invalid RFQ datasource URL: the effective spring.datasource.url must start with 'jdbc:' "
                                + "(for MySQL, use jdbc:mysql://<host>:3306/<database>). Set RFQ_DB_URL and check "
                                + "the IntelliJ run configuration for SPRING_DATASOURCE_URL, which overrides the "
                                + "application YAML value.");
            }
        };
    }
}
