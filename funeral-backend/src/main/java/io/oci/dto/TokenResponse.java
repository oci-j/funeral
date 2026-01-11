package io.oci.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TokenResponse {

    @JsonProperty("access_token")
    public String accessToken;

    @JsonProperty("token_type")
    public String tokenType;

    @JsonProperty("expires_in")
    public Integer expiresIn;

    public String token;

    public TokenResponse() {
    }

    public TokenResponse(String accessToken, String tokenType, Integer expiresIn) {
        this.accessToken = accessToken;
        this.token = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }
}
