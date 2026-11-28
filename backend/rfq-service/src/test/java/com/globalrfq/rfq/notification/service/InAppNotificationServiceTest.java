package com.globalrfq.rfq.notification.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.notification.entity.InAppNotification;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.repository.InAppNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock
    private InAppNotificationRepository notifications;

    @Mock
    private RfqWorkflowEmailDispatcher emailDispatcher;

    @InjectMocks
    private InAppNotificationService service;

    @Test
    void sendPersistsNotification() {
        service.send(42L, NotificationType.RFQ_ASSIGNED, "Assigned RFQ-101", "Assigned to you", "pub-101");

        ArgumentCaptor<InAppNotification> captor = ArgumentCaptor.forClass(InAppNotification.class);
        verify(notifications).save(captor.capture());
        InAppNotification saved = captor.getValue();
        assertEquals(42L, saved.getUserId());
        assertEquals(NotificationType.RFQ_ASSIGNED, saved.getType());
        assertEquals("Assigned RFQ-101", saved.getTitle());
        assertEquals("pub-101", saved.getRfqPublicId());
        assertFalse(saved.isRead());
        verify(emailDispatcher).dispatch(42L, NotificationType.RFQ_ASSIGNED, "Assigned RFQ-101", "Assigned to you", "pub-101");
    }

    @Test
    void markReadThrowsWhenNotificationBelongsToAnotherUser() {
        InAppNotification notification = new InAppNotification(99L, NotificationType.GENERAL, "Title", "Body", null);
        ReflectionTestUtils.setField(notification, "id", 5L);
        when(notifications.findById(5L)).thenReturn(Optional.of(notification));

        IdentityPrincipal otherUser = new IdentityPrincipal(10L, "other@globalrfq.test", Set.of("VIEWER"));
        RfqException ex = assertThrows(RfqException.class, () -> service.markRead(5L, otherUser));
        assertEquals(RfqErrorCode.ACCESS_DENIED, ex.code());
    }

    @Test
    void markReadUpdatesStateWhenOwnedByActor() {
        InAppNotification notification = new InAppNotification(10L, NotificationType.RED_FLAG_RAISED, "Red flag", "Blocked", "rfq-1");
        ReflectionTestUtils.setField(notification, "id", 7L);
        when(notifications.findById(7L)).thenReturn(Optional.of(notification));
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IdentityPrincipal owner = new IdentityPrincipal(10L, "owner@globalrfq.test", Set.of("QUOTATION_MANAGER"));
        var response = service.markRead(7L, owner);

        assertTrue(response.read());
        assertNotNull(response.readAt());
    }

    @Test
    void listAndUnreadCountAndMarkAllReadDelegateCleanly() {
        IdentityPrincipal actor = new IdentityPrincipal(10L, "owner@globalrfq.test", Set.of("ADMIN"));
        InAppNotification n = new InAppNotification(10L, NotificationType.GENERAL, "Hi", "Details", null);
        when(notifications.findByUserIdOrderByCreatedAtDesc(10L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(n)));
        when(notifications.countByUserIdAndReadFalse(10L)).thenReturn(3L);
        when(notifications.markAllReadForUser(10L)).thenReturn(3);

        var page = service.listForCurrentUser(actor, 0, 20);
        assertEquals(1, page.content().size());
        assertEquals(3L, service.unreadCount(actor).get("unreadCount"));
        assertEquals(3, service.markAllRead(actor).get("markedRead"));
    }
}
