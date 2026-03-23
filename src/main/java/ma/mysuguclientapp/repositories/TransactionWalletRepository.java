package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.TransactionWallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionWalletRepository extends JpaRepository<TransactionWallet, Long> {

    Page<TransactionWallet> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);
}
