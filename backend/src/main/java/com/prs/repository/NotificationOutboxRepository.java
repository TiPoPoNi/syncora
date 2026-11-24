package com.prs.repository;

import com.prs.entity.NotificationDeliveryStatus;
import com.prs.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findByStatus(NotificationDeliveryStatus status);
    List<NotificationOutbox> findByStatusAndRetryCountLessThan(NotificationDeliveryStatus status, Integer maxRetries);
}
