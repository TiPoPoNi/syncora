package com.prs.integration.apikey;

import com.prs.entity.ApiKey;
import com.prs.entity.Organization;
import com.prs.entity.User;
import com.prs.integration.dto.ApiKeyResponse;
import com.prs.integration.dto.CreateApiKeyRequest;
import com.prs.repository.ApiKeyRepository;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    private static final String KEY_PREFIX = "prs_live_";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public ApiKeyResponse createApiKey(Long organizationId, Long userId, CreateApiKeyRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with id: " + organizationId));

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        // Generate raw key: prs_live_<32 hex chars>
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        String secretPart = bytesToHex(randomBytes);
        String rawKey = KEY_PREFIX + secretPart;

        String keyPrefix = rawKey.substring(0, Math.min(rawKey.length(), 15));
        String keyHash = sha256Hex(rawKey);

        List<String> scopesList = (request.getScopes() == null || request.getScopes().isEmpty())
                ? List.of("projects:read", "schedules:read")
                : request.getScopes();
        String scopesStr = String.join(",", scopesList);

        LocalDateTime expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getExpiresInDays());
        }

        ApiKey apiKey = ApiKey.builder()
                .organization(organization)
                .createdByUser(user)
                .name(request.getName().trim())
                .keyPrefix(keyPrefix)
                .keyHash(keyHash)
                .scopes(scopesStr)
                .expiresAt(expiresAt)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        return toResponse(saved, rawKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getApiKeys(Long organizationId) {
        return apiKeyRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(k -> toResponse(k, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeApiKey(Long organizationId, Long keyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndOrganizationId(keyId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("API Key not found or does not belong to active organization."));

        if (apiKey.getRevokedAt() == null) {
            apiKey.setRevokedAt(LocalDateTime.now());
            apiKeyRepository.save(apiKey);
        }
    }

    @Transactional
    public Optional<ApiKey> authenticateKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash = sha256Hex(rawKey);
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(hash);
        if (apiKeyOpt.isPresent()) {
            ApiKey key = apiKeyOpt.get();
            if (key.isValid()) {
                key.setLastUsedAt(LocalDateTime.now());
                apiKeyRepository.save(key);
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private ApiKeyResponse toResponse(ApiKey entity, String rawKey) {
        List<String> scopes = Arrays.stream(entity.getScopes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        return ApiKeyResponse.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganization() != null ? entity.getOrganization().getId() : null)
                .name(entity.getName())
                .keyPrefix(entity.getKeyPrefix())
                .scopes(scopes)
                .rawKey(rawKey)
                .expiresAt(entity.getExpiresAt())
                .revokedAt(entity.getRevokedAt())
                .lastUsedAt(entity.getLastUsedAt())
                .createdAt(entity.getCreatedAt())
                .valid(entity.isValid())
                .build();
    }
}
