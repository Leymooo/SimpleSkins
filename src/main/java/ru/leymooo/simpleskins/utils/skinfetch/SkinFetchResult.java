package ru.leymooo.simpleskins.utils.skinfetch;

import com.velocitypowered.api.util.GameProfile;
import java.util.Objects;
import java.util.UUID;

public class SkinFetchResult implements FetchResult {

    private final UUID id;
    private final GameProfile.Property property;

    public SkinFetchResult(UUID id, GameProfile.Property property) {
        this.id = id;
        this.property = property;
    }

    public UUID getId() {
        return id;
    }

    public GameProfile.Property getProperty() {
        return property;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + Objects.hashCode(this.id);
        hash = 17 * hash + Objects.hashCode(this.property);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FetchResult other = (FetchResult) obj;
        if (!Objects.equals(this.id, other.getId())) {
            return false;
        }
        if (!Objects.equals(this.property, other.getProperty())) {
            return false;
        }
        return true;
    }

}
