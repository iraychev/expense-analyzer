package com.iraychev.expenseanalyzer.domain.entity;

import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", uniqueConstraints = @UniqueConstraint(
        name = "uk_transactions_account_external_id", columnNames = {"bank_account_id", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The bank's transactionId (or internalTransactionId); null for rows stored before it was recorded. */
    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column
    private LocalDateTime valueDate;
    
    @Column(nullable = false)
    private LocalDateTime transactionDate;

    private String category;

    @Column
    private Integer otherRetryCount = 0;

    @Column
    private LocalDateTime otherNextRetryAt;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;
}
