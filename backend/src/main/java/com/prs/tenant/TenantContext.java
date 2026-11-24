package com.prs.tenant;

import com.prs.entity.Organization;
import com.prs.entity.OrganizationMembership;

public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Organization> CURRENT_ORGANIZATION = new ThreadLocal<>();
    private static final ThreadLocal<OrganizationMembership> CURRENT_MEMBERSHIP = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static void setOrganization(Organization organization) {
        CURRENT_ORGANIZATION.set(organization);
        if (organization != null) {
            CURRENT_TENANT_ID.set(organization.getId());
        }
    }

    public static Organization getOrganization() {
        return CURRENT_ORGANIZATION.get();
    }

    public static void setMembership(OrganizationMembership membership) {
        CURRENT_MEMBERSHIP.set(membership);
    }

    public static OrganizationMembership getMembership() {
        return CURRENT_MEMBERSHIP.get();
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
        CURRENT_ORGANIZATION.remove();
        CURRENT_MEMBERSHIP.remove();
    }
}
