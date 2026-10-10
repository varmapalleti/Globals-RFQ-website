package com.globalrfq.identity.role.repository;


import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.role.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);

}
