package com.prs.repository;

import com.prs.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    List<WebhookDelivery> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    Optional<WebhookDelivery> findByIdAndSubscriptionId(Long id, Long subscriptionId);
    List<WebhookDelivery> findBySubscriptionOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
