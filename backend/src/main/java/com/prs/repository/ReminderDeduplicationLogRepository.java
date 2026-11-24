package com.prs.repository;

import com.prs.entity.ReminderDeduplicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ReminderDeduplicationLogRepository extends JpaRepository<ReminderDeduplicationLog, Long> {
    boolean existsByTargetTypeAndTargetIdAndReminderTypeAndEventDateAndRecipientUserId(
            String targetType, Long targetId, String reminderType, LocalDate eventDate, Long recipientUserId);
}
