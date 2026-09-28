package com.example.webtalk.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    // FR04 사용자 검색: 아이디 또는 닉네임 일부로 검색하고 본인 계정은 제외한다.
    @Query("""
            SELECT u FROM User u
            WHERE u.id <> :excludeUserId
              AND (LOWER(u.loginId) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY u.nickname
            """)
    List<User> searchByQueryExcludingSelf(@Param("query") String query, @Param("excludeUserId") Long excludeUserId);
}
