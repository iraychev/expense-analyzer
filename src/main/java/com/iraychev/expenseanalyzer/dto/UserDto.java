package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String password;
    private String email;
    private String name;

    private List<BankConnectionDto> bankConnections;

    /** Only set on sync responses: true when every connection was throttled (no remote fetch made). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean throttled;

    /** Only set on throttled sync responses: earliest time a remote fetch is allowed again. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant nextSyncAvailableAt;
}