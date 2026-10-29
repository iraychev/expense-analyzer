package com.iraychev.expenseanalyzer.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A user's own "this merchant is always that category" rule: layer 1 of the categorization pipeline. */
@Entity
@Table(name = "user_category_rules", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_category_rules_user_merchant", columnNames = {"user_id", "merchant"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCategoryRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Canonical merchant key (same as {@code Transaction.merchant}). */
    @Column(nullable = false)
    private String merchant;

    @Column(nullable = false)
    private String category;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
