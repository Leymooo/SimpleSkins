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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkinFetchResult that = (SkinFetchResult) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(property, that.property);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, property);
    }

    @Override
    public String toString() {
        return "SkinFetchResult{" +
                "id=" + id +
                ", property=" + property +
                '}';
    }
}
