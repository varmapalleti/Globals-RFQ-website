package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.auth.IdentityAuthorizationClient;
import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.dto.AssignmentRequest;
import com.globalrfq.rfq.intake.dto.RfqResponse;
import org.springframework.stereotype.Service;

@Service
public class RfqAssignmentCoordinator {
    private final IdentityAuthorizationClient identityClient;
    private final RfqCommandService commands;

    public RfqAssignmentCoordinator(IdentityAuthorizationClient identityClient, RfqCommandService commands) {
        this.identityClient = identityClient;
        this.commands = commands;
    }

    public RfqResponse assign(String publicId, AssignmentRequest request, IdentityPrincipal actor) {
        commands.assertAssignmentAuthority(publicId, request.assignmentRole(), actor);
        boolean eligible;
        try {
            eligible = identityClient.isEligible(request.assignedUserId(), request.assignmentRole().name());
        } catch (IllegalArgumentException exception) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "Assignment role is not supported by Identity");
        }
        if (!eligible) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "The selected account must be active and have the requested Identity role");
        }
        return commands.assign(publicId, request, actor);
    }
}
