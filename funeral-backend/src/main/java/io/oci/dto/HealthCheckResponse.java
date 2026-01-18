package io.oci.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(
    JsonInclude.Include.NON_NULL
)
public class HealthCheckResponse {

    @JsonProperty(
        "status"
    )
    public String status;

    @JsonProperty(
        "services"
    )
    public Services services;

    @JsonProperty(
        "version"
    )
    public String version;

    @JsonProperty(
        "uptime"
    )
    public long uptime;

    public HealthCheckResponse() {
    }

    public HealthCheckResponse(
            String status,
            Services services,
            String version,
            long uptime
    ) {
        this.status = status;
        this.services = services;
        this.version = version;
        this.uptime = uptime;
    }

    @RegisterForReflection
    @JsonInclude(
        JsonInclude.Include.NON_NULL
    )
    public static class Services {
        @JsonProperty(
            "mongodb"
        )
        public ServiceStatus mongodb;

        @JsonProperty(
            "storage"
        )
        public ServiceStatus storage;

        public Services() {
        }

        public Services(
                ServiceStatus mongodb,
                ServiceStatus storage
        ) {
            this.mongodb = mongodb;
            this.storage = storage;
        }
    }

    @RegisterForReflection
    @JsonInclude(
        JsonInclude.Include.NON_NULL
    )
    public static class ServiceStatus {
        @JsonProperty(
            "status"
        )
        public String status;

        @JsonProperty(
            "message"
        )
        public String message;

        @JsonProperty(
            "responseTime"
        )
        public long responseTime;

        public ServiceStatus() {
        }

        public ServiceStatus(
                String status,
                String message,
                long responseTime
        ) {
            this.status = status;
            this.message = message;
            this.responseTime = responseTime;
        }
    }
}
