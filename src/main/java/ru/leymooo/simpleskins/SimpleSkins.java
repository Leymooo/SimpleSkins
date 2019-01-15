package ru.leymooo.simpleskins;

import ru.leymooo.simpleskins.utils.UuidFetchCache;
import ru.leymooo.simpleskins.utils.RoundIterator;
import ru.leymooo.simpleskins.utils.DataBaseUtils;
import ru.leymooo.simpleskins.utils.SkinFetcher;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.kyori.text.Component;

import net.kyori.text.serializer.ComponentSerializers;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import ru.leymooo.simpleskins.command.SkinCommand;
import ru.leymooo.simpleskins.utils.SkinApplier;
import ru.leymooo.simpleskins.utils.SkinFetcher.FetchResult;

@Plugin(id = "simpleskins", name = "SimpleSkins", version = "1.2",
        description = "Simple skins restorer plugin for velocity",
        authors = "Leymooo")
public class SimpleSkins {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private SkinFetcher skinFetcher;
    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final DataBaseUtils dataBaseUtils;
    private RoundIterator<SkinFetcher.FetchResult> defaultSkins;
    private Configuration config;

    @Inject
    public SimpleSkins(ProxyServer server, Logger logger, @DataDirectory Path userConfigDirectory) {
        logger.info("Loading SimpleSkins");
        this.server = server;
        this.logger = logger;
        this.dataDirectory = userConfigDirectory;
        this.dataBaseUtils = new DataBaseUtils(this);
        logger.info("SimpleSkins loaded");
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Enabling SimpleSkins");

        if (!loadConfig()) {
            server.getEventManager().unregisterListeners(this);
            logger.error("Config is not loaded. Plugin will be inactive");
            return;
        }
        this.skinFetcher = new SkinFetcher(this, dataBaseUtils, new UuidFetchCache(this));

        initDefaultSkins();
        this.server.getCommandManager().register(new SkinCommand(this), "skin");
        logger.info("SimpleSkins enabled");
    }

    @Subscribe
    public void onShutDown(ProxyShutdownEvent ev) {
        logger.info("Disabling SimpleSkins");
        this.dataBaseUtils.closeConnection();
        this.service.shutdownNow();
        try {
            this.service.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
        }
        logger.info("SimpleSkins disabled");
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        Optional<FetchResult> maybeCached = skinFetcher.getPlayerSkinFromDatabase(event.getUsername());

        if (maybeCached.isPresent() && event.isOnlineMode()) {
            event.setGameProfile(SkinApplier.createProfileWithSkin(event.getGameProfile(), maybeCached.get().getProperty()));
            return;
        }
        if (!event.isOnlineMode()) {
            Optional<FetchResult> toSet = maybeCached.isPresent()
                    ? maybeCached : skinFetcher.fetchSkin(event.getUsername(), true);
            FetchResult skin = toSet.orElse(defaultSkins.next());
            if (skin != null) {
                event.setGameProfile(SkinApplier.createProfileWithSkin(event.getGameProfile(), skin.getProperty()));
                if (!maybeCached.isPresent()) {
                    service.execute(() -> {
                        dataBaseUtils.saveUser(event.getUsername(), skin);
                    });
                }
            }
        }
    }

    private boolean loadConfig() {
        File config = new File(dataDirectory.toFile(), "config.yml");
        config.getParentFile().mkdir();
        try {
            if (!config.exists()) {
                try (InputStream in = SimpleSkins.class.getClassLoader().getResourceAsStream("config.yml")) {
                    Files.copy(in, config.toPath());
                }
            }
            this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(config);
        } catch (IOException ex) {
            logger.error("Can not load or save config", ex);
            return false;
        }
        return true;
    }

    private void initDefaultSkins() {
        logger.info("Loading default skins");
        List<SkinFetcher.FetchResult> skins = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (String user : config.getStringList("default-skins")) {
            futures.add(service.submit(() -> skinFetcher.fetchSkin(user, false)
                    .ifPresent(skin -> skins.add(new SkinFetcher.FetchResult(null, skin.getProperty())))));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        }
        this.defaultSkins = new RoundIterator<>(skins);
        logger.info("Default skins loaded");
    }

    public Logger getLogger() {
        return logger;
    }

    public ProxyServer getProxyServer() {
        return server;
    }

    public Configuration getConfig() {
        return config;
    }

    public ExecutorService getExecutorService() {
        return service;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public DataBaseUtils getDataBaseUtils() {
        return dataBaseUtils;
    }

    public SkinFetcher getSkinFetcher() {
        return skinFetcher;
    }

    public Component deserialize(String configKey) {
        return ComponentSerializers.LEGACY.deserialize(config.getString(configKey), '&');
    }
}
