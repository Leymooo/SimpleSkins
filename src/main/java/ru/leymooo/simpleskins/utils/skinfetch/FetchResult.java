package ru.leymooo.simpleskins.utils.skinfetch;

import com.velocitypowered.api.util.GameProfile.Property;
import java.util.UUID;

public interface FetchResult {

    public UUID getId();

    public Property getProperty();

}
