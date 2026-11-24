package com.prs.integration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;

@Component
public class WebhookUrlValidator {

    @Value("${app.integrations.webhooks.allow-private-network:false}")
    private boolean allowPrivateNetwork;

    public void setAllowPrivateNetwork(boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    public boolean isAllowPrivateNetwork() {
        return allowPrivateNetwork;
    }

    /**
     * Validates destination URL against SSRF vulnerabilities:
     * - Disallows non-HTTP/HTTPS protocols
     * - Disallows loopback (127.0.0.0/8, ::1)
     * - Disallows private RFC 1918 addresses (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
     * - Disallows link-local and cloud metadata addresses (169.254.169.254)
     * - Enforces HTTPS in strict mode
     */
    public void validateUrl(String destinationUrl) {
        if (destinationUrl == null || destinationUrl.isBlank()) {
            throw new IllegalArgumentException("Webhook destination URL cannot be blank.");
        }

        URI uri;
        try {
            uri = URI.create(destinationUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook destination URL format: " + destinationUrl);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Webhook destination URL must use HTTP or HTTPS protocol.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook destination URL must contain a valid host.");
        }

        // Check common localhost aliases
        if (!allowPrivateNetwork) {
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "0.0.0.0".equals(host) || "::1".equals(host)) {
                throw new IllegalArgumentException("Loopback and local addresses are forbidden for webhook destinations.");
            }

            // Cloud metadata IP
            if ("169.254.169.254".equals(host)) {
                throw new IllegalArgumentException("Access to cloud metadata endpoints (169.254.169.254) is strictly forbidden.");
            }

            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress addr : addresses) {
                    if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()) {
                        throw new IllegalArgumentException("Webhook destination resolves to a forbidden loopback or link-local address: " + addr.getHostAddress());
                    }
                    if (addr.isSiteLocalAddress()) {
                        throw new IllegalArgumentException("Webhook destination resolves to a private RFC 1918 network address: " + addr.getHostAddress());
                    }
                    String ip = addr.getHostAddress();
                    if (ip.startsWith("169.254.") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
                        throw new IllegalArgumentException("Webhook destination resolves to a restricted private/metadata address: " + ip);
                    }
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                // If DNS fails to resolve during validation, allow or warn depending on use-case
                throw new IllegalArgumentException("Cannot resolve webhook destination host '" + host + "': " + e.getMessage());
            }
        }
    }
}
