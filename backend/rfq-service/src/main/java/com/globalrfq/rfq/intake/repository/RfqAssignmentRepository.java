package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.AssignmentRole;
import com.globalrfq.rfq.intake.entity.RfqAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RfqAssignmentRepository extends JpaRepository<RfqAssignment, Long> {
    Optional<RfqAssignment> findByRfqIdAndAssignmentRoleAndActiveTrue(Long rfqId, AssignmentRole role);
    List<RfqAssignment> findByRfqIdAndActiveTrueOrderByAssignedAtAsc(Long rfqId);
    List<RfqAssignment> findByRfqIdInAndActiveTrueOrderByAssignedAtAsc(java.util.Collection<Long> rfqIds);
    List<RfqAssignment> findByRfqIdOrderByAssignedAtAsc(Long rfqId);
    boolean existsByRfqIdAndAssignedUserIdAndActiveTrue(Long rfqId, Long userId);
    boolean existsByRfqIdAndAssignedUserId(Long rfqId, Long userId);

    @Query("select a.assignmentRole as role, a.assignedUserId as userId, count(a.id) as activeRfqCount "
            + "from RfqAssignment a where a.active = true group by a.assignmentRole, a.assignedUserId "
            + "order by a.assignmentRole, a.assignedUserId")
    List<ActiveAssignmentWorkload> findActiveWorkload();

    interface ActiveAssignmentWorkload {
        AssignmentRole getRole();
        Long getUserId();
        Long getActiveRfqCount();
    }
}
