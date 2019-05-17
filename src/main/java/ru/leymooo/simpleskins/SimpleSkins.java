package ru.leymooo.simpleskins;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.text.Component;
import net.kyori.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.slf4j.Logger;
import ru.leymooo.simpleskins.command.SkinCommand;
import ru.leymooo.simpleskins.utils.DataBaseUtils;
import ru.leymooo.simpleskins.utils.RoundIterator;
import ru.leymooo.simpleskins.utils.SkinApplier;
import ru.leymooo.simpleskins.utils.UuidFetchCache;
import ru.leymooo.simpleskins.utils.skinfetch.FetchResult;
import ru.leymooo.simpleskins.utils.skinfetch.SkinFetcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

@Plugin(id = "simpleskins", name = "SimpleSkins", version = "1.4",
        description = "Simple skins restorer plugin for velocity",
        authors = "Leymooo")
public class SimpleSkins {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final DataBaseUtils dataBaseUtils;
    private SkinFetcher skinFetcher;
    private RoundIterator<FetchResult> defaultSkins;
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
        } catch (InterruptedException ignored) {
        }
        logger.info("SimpleSkins disabled");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        boolean hasSkin = SkinApplier.hasSkin(player);
        boolean onlineMode = server.getConfiguration().isOnlineMode();
        Optional<FetchResult> maybeCached = skinFetcher.getPlayerSkinFromDatabase(player.getUsername());
        if ((hasSkin || onlineMode) && maybeCached.isPresent()) {
            SkinApplier.applySkin(player, maybeCached.get().getProperty());
            return;
        }
        if (!onlineMode) {
            Optional<FetchResult> toSet = maybeCached.isPresent()
                    ? maybeCached : skinFetcher.fetchSkin(player.getUsername(), true);
            FetchResult skin = toSet.orElse(defaultSkins.next());
            if (skin != null) {
                SkinApplier.applySkin(player, skin.getProperty());
                if (!maybeCached.isPresent()) {
                    service.execute(() -> dataBaseUtils.saveUser(player.getUsername(), skin));
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
        List<FetchResult> skins = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (String user : config.getStringList("default-skins")) {
            futures.add(service.submit(() -> skinFetcher.fetchSkin(user, false)
                    .ifPresent(skins::add)));
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
        return LegacyComponentSerializer.INSTANCE.deserialize(config.getString(configKey), '&');
    }
}
