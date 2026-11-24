package com.globalrfq.rfq.common.auth;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Set;

@Component
public class IdentityAuthorizationClient {
    private static final String SERVICE_TOKEN_HEADER = "X-Identity-Service-Token";
    private static final ParameterizedTypeReference<IdentityEnvelope<IdentityContextResponse>> CONTEXT_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<IdentityEnvelope<UserEligibilityResponse>> ELIGIBILITY_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final IdentityClientProperties properties;
    private final LoadBalancerClient loadBalancerClient;

    public IdentityAuthorizationClient(IdentityClientProperties properties, LoadBalancerClient loadBalancerClient) {
        this.restClient = RestClient.create();
        this.properties = properties;
        this.loadBalancerClient = loadBalancerClient;
    }

    public IdentityPrincipal validate(String accessToken) {
        try {
            IdentityEnvelope<IdentityContextResponse> response = restClient.get()
                    .uri(identityUri("/internal/identity/validate"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(SERVICE_TOKEN_HEADER, properties.internalServiceToken())
                    .retrieve()
                    .body(CONTEXT_TYPE);
            if (response == null || !response.success() || response.data() == null || response.data().userId() == null) {
                throw new IdentityUnavailableException("Identity returned an invalid validation response");
            }
            return new IdentityPrincipal(response.data().userId(), response.data().email(), Set.copyOf(response.data().roles()));
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 401) {
                throw new InvalidIdentityTokenException();
            }
            if (exception.getStatusCode().value() == 403) {
                throw new IdentityUnavailableException("Identity rejected the RFQ service request");
            }
            throw new IdentityUnavailableException("Identity authorization is temporarily unavailable");
        } catch (ResourceAccessException exception) {
            throw new IdentityUnavailableException("Identity authorization is temporarily unavailable");
        }
    }

    public boolean isEligible(long userId, String role) {
        try {
            URI eligibilityUri = UriComponentsBuilder.fromUri(identityUri(
                            "/internal/identity/users/" + userId + "/eligibility"))
                    .queryParam("role", role)
                    .build()
                    .toUri();
            IdentityEnvelope<UserEligibilityResponse> response = restClient.get()
                    .uri(eligibilityUri)
                    .header(SERVICE_TOKEN_HEADER, properties.internalServiceToken())
                    .retrieve()
                    .body(ELIGIBILITY_TYPE);
            return response != null && response.success() && response.data() != null && response.data().eligible();
        } catch (HttpStatusCodeException | ResourceAccessException exception) {
            throw new IdentityUnavailableException("Identity user eligibility could not be verified");
        }
    }

    private URI identityUri(String path) {
        try {
            ServiceInstance instance = loadBalancerClient.choose(properties.serviceName());
            if (instance == null) {
                throw new IdentityUnavailableException("Identity service is not registered with Eureka");
            }
            URI logicalUri = URI.create("http://" + properties.serviceName() + path);
            return loadBalancerClient.reconstructURI(instance, logicalUri);
        } catch (IdentityUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdentityUnavailableException("Identity service discovery is temporarily unavailable");
        }
    }
}
