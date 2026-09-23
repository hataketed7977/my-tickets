package com.bytedance.tickets.security;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link RegisteredClientRepository} backed by in-memory maps that starts empty and
 * supports clients added dynamically through DCR.
 */
public final class MapRegisteredClientRepository implements RegisteredClientRepository {

    private final ConcurrentMap<String, RegisteredClient> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> clientIdIndex = new ConcurrentHashMap<>();

    @Override
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        byId.put(registeredClient.getId(), registeredClient);
        clientIdIndex.put(registeredClient.getClientId(), registeredClient.getId());
    }

    @Override
    public RegisteredClient findById(String id) {
        return byId.get(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        String id = clientIdIndex.get(clientId);
        return id == null ? null : byId.get(id);
    }
}
