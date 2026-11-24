package com.prs.repository;

import com.prs.entity.CalendarEventMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarEventMappingRepository extends JpaRepository<CalendarEventMapping, Long> {
    Optional<CalendarEventMapping> findByIntegrationIdAndEntityTypeAndEntityId(Long integrationId, String entityType, Long entityId);
    List<CalendarEventMapping> findByIntegrationId(Long integrationId);
    void deleteByIntegrationIdAndEntityTypeAndEntityId(Long integrationId, String entityType, Long entityId);
}
