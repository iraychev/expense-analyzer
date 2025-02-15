package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {
    List<BankConnection> findByUser_Id(Long userId);
    BankConnection findByRequisitionId(String requisitionId);
}