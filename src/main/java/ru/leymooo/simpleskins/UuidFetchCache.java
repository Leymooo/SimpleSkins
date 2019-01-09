package ru.leymooo.simpleskins;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import ru.leymooo.simpleskins.utils.SkinUtils.FetchResult;

public class UuidFetchCache {

    private final Set<UUID> working = new HashSet<>();
    private final Cache<UUID, FetchResult> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public boolean isWorking(UUID id) {
        return working.contains(id);
    }

    public void addWorking(UUID id) {
        working.add(id);
    }

    public void removeWorking(UUID id) {
        working.remove(id);
    }

    public Optional<FetchResult> getIfCached(UUID id) {
        return Optional.ofNullable(cache.getIfPresent(id));
    }

    public void cache(FetchResult result) {
        if (result.getId() != null) {
            cache.put(result.getId(), result);
        }
    }
}
