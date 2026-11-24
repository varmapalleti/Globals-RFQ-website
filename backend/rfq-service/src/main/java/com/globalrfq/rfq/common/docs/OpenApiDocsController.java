package com.globalrfq.rfq.common.docs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves offline-compatible OpenAPI 3.0.3 documentation and interactive explorer when enabled.
 */
@RestController
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiDocsController {

    @GetMapping(value = {"/v3/api-docs", "/v3/api-docs/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Resource openApiSpec() {
        return new ClassPathResource("openapi/openapi.json");
    }

    @GetMapping(value = {"/swagger-ui.html", "/swagger-ui", "/swagger-ui/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public Resource swaggerUi() {
        return new ClassPathResource("openapi/swagger-ui.html");
    }
}
