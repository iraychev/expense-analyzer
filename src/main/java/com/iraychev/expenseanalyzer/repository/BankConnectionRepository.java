package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {
    List<BankConnection> findByUser_Id(Long userId);
    Optional<BankConnection> findByRequisitionId(String requisitionId);
}