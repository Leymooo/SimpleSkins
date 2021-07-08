package ru.leymooo.simpleskins.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import ru.leymooo.simpleskins.SimpleSkins;
import ru.leymooo.simpleskins.utils.SkinApplier;
import ru.leymooo.simpleskins.utils.skinfetch.FetchResult;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author mikim
 */
public class SkinCommand implements SimpleCommand {

    private final SimpleSkins plugin;
    private final Set<CommandSource> working = new HashSet<>();

    public SkinCommand(SimpleSkins plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource cs = invocation.source();
        String[] args = invocation.arguments();
        if (plugin.getConfig().getNode("use-permission").getBoolean() && !cs.hasPermission("simpleskins.skin")) {
            cs.sendMessage(plugin.deserialize("messages", "no-permission"));
            return;
        }
        if (cs instanceof Player) {
            if (args.length == 0) {
                cs.sendMessage(plugin.deserialize("messages", "help"));
                return;
            }
            if (working.contains(cs)) {
                cs.sendMessage(plugin.deserialize("messages", "working"));
                return;
            }
            Player player = (Player) cs;
            working.add(cs);
            if (args[0].equalsIgnoreCase("update")) {
                plugin.getExecutorService().execute(() -> {
                    Optional<UUID> uuid = plugin.getDataBaseUtils().getUuid(player.getUsername());
                    if (uuid.isPresent()) {
                        cs.sendMessage(plugin.deserialize("messages", "fetching"));
                        Optional<FetchResult> newSkin = plugin.getSkinFetcher().fetchSkin(player, uuid.get());
                        newSkin.ifPresent(skin -> {
                            cs.sendMessage(plugin.deserialize("messages", "skin-changed"));
                            SkinApplier.applySkin(player, skin.getProperty());
                        });
                    } else {
                        cs.sendMessage(plugin.deserialize("messages", "skin-update-error"));
                    }
                    working.remove(cs);
                });
                return;
            }
            plugin.getExecutorService().execute(() -> {
                cs.sendMessage(plugin.deserialize("messages", "fetching"));
                Optional<FetchResult> newSkin = plugin.getSkinFetcher().fetchSkin(player, args[0].equalsIgnoreCase("reset") ? player.getUsername()
                        : args[0]);
                newSkin.ifPresent(skin -> {
                    plugin.getDataBaseUtils().saveUser(player.getUsername(), skin);
                    SkinApplier.applySkin(player, skin.getProperty());
                    cs.sendMessage(plugin.deserialize("messages", "skin-changed"));

                });
                working.remove(cs);
            });
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

}
