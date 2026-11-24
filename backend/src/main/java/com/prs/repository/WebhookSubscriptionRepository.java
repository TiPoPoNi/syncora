package com.prs.repository;

import com.prs.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findByOrganizationId(Long organizationId);
    Optional<WebhookSubscription> findByIdAndOrganizationId(Long id, Long organizationId);
    List<WebhookSubscription> findByOrganizationIdAndIsEnabledTrue(Long organizationId);
    List<WebhookSubscription> findByIsEnabledTrue();
}
