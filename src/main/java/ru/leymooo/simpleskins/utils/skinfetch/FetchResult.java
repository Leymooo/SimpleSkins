package ru.leymooo.simpleskins.utils.skinfetch;

import com.velocitypowered.api.util.GameProfile.Property;

import java.util.UUID;

public interface FetchResult {

    UUID getId();

    Property getProperty();

}
