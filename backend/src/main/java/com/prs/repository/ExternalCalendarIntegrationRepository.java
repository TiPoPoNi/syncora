package com.prs.repository;

import com.prs.entity.CalendarProvider;
import com.prs.entity.ExternalCalendarIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalCalendarIntegrationRepository extends JpaRepository<ExternalCalendarIntegration, Long> {
    Optional<ExternalCalendarIntegration> findByOrganizationIdAndProvider(Long organizationId, CalendarProvider provider);
    Optional<ExternalCalendarIntegration> findByOrganizationIdAndUserIdAndProvider(Long organizationId, Long userId, CalendarProvider provider);
    List<ExternalCalendarIntegration> findByOrganizationId(Long organizationId);
}
