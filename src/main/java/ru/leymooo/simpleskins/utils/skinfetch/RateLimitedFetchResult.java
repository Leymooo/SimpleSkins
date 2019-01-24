package ru.leymooo.simpleskins.utils.skinfetch;

import com.velocitypowered.api.util.GameProfile;
import java.util.UUID;

public class RateLimitedFetchResult implements FetchResult {

    private final UUID id;

    public RateLimitedFetchResult(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public GameProfile.Property getProperty() {
        return null;
    }

}
