package com.java.wallet.repository;

import com.java.wallet.model.entity.LedgerEntry;
import com.java.wallet.model.entity.Wallet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByWalletOrderByIdDesc(Wallet wallet, Pageable pageable);
}
