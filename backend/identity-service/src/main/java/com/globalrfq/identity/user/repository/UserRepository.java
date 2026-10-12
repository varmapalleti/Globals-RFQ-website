package com.globalrfq.identity.user.repository;

import com.globalrfq.identity.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByOfficialEmailIgnoreCase(String officialEmail);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByPersonalEmailIgnoreCase(String personalEmail);

    boolean existsByOfficialEmailIgnoreCase(String officialEmail);

    boolean existsByPersonalEmailIgnoreCase(String personalEmail);

}
