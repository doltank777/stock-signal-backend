package com.stockapp.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Query("""
            SELECT u
            FROM User u
            WHERE (
                :keyword IS NULL
                OR u.email LIKE CONCAT('%', :keyword, '%')
                OR u.nickname LIKE CONCAT('%', :keyword, '%')
            )
            """)
    Page<User> findAdminUsers(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT u FROM User u
            WHERE u.membershipType = com.stockapp.domain.user.MembershipType.FREE
            AND (:keyword IS NULL OR u.email LIKE CONCAT('%', :keyword, '%') OR u.nickname LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<User> findAdminFreeUsers(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.membershipType = com.stockapp.domain.user.MembershipType.PAID
            AND u.membershipStartedAt > :now
            AND (:keyword IS NULL OR u.email LIKE CONCAT('%', :keyword, '%') OR u.nickname LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<User> findAdminScheduledPaidUsers(
            @Param("keyword") String keyword,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            SELECT u FROM User u
            WHERE u.membershipType = com.stockapp.domain.user.MembershipType.PAID
            AND u.membershipStartedAt <= :now
            AND u.membershipExpiredAt > :now
            AND (:keyword IS NULL OR u.email LIKE CONCAT('%', :keyword, '%') OR u.nickname LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<User> findAdminActivePaidUsers(
            @Param("keyword") String keyword,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            SELECT u FROM User u
            WHERE u.membershipType = com.stockapp.domain.user.MembershipType.PAID
            AND u.membershipExpiredAt <= :now
            AND (:keyword IS NULL OR u.email LIKE CONCAT('%', :keyword, '%') OR u.nickname LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<User> findAdminExpiredPaidUsers(
            @Param("keyword") String keyword,
            @Param("now") Instant now,
            Pageable pageable
    );
}
