package com.opsconsole.auth.repository;

import com.opsconsole.auth.domain.AccountStatus;
import com.opsconsole.auth.domain.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByAzureAdId(String azureAdId);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    List<AppUser> findAllByOrderByDisplayNameAsc();

    long countByRole_Code(String roleCode);

    long countByRole_CodeAndAccountStatus(String roleCode, AccountStatus accountStatus);
}
