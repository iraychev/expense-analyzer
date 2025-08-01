package com.iraychev.expenseanalyzer.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bank_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reference;

    @Column(name = "institution_id", nullable = false)
    private String institutionId;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "requisition_id", nullable = false, unique = true)
    private String requisitionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "bankConnection", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BankAccount> accounts = new ArrayList<>();

}