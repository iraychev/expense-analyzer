package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(Long userId);
    List<Transaction> findByUserIdAndBookingDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}