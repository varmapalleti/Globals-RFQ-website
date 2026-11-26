package com.globalrfq.identity.user.repository;

import com.globalrfq.identity.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByOfficialEmailIgnoreCase(String officialEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u left join fetch u.roles where lower(u.officialEmail) = lower(:email)")
    Optional<User> findForLoginByOfficialEmail(@Param("email") String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByPersonalEmailIgnoreCase(String personalEmail);

    boolean existsByOfficialEmailIgnoreCase(String officialEmail);

    boolean existsByPersonalEmailIgnoreCase(String personalEmail);

    @EntityGraph(attributePaths = "roles")
    java.util.List<User> findWithRolesByIdIn(java.util.Collection<Long> ids);

    @Query("select u.status from User u where u.id = :id")
    java.util.Optional<com.globalrfq.identity.user.entity.UserStatus> findStatusById(@Param("id") Long id);

    @Query("select distinct u from User u join u.roles r where u.status = :status and r.name = :roleName order by u.fullName asc")
    java.util.List<User> findAllActiveByRoleName(@Param("status") com.globalrfq.identity.user.entity.UserStatus status,
                                                 @Param("roleName") com.globalrfq.identity.role.entity.RoleName roleName);

}
