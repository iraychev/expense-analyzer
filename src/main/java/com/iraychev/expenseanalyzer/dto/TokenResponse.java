package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TokenResponse {
    private String access;

    @JsonProperty("access_expires")
    private Long accessExpires;
}
