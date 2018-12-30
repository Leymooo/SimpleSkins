package ru.leymooo.simpleskins.command;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.text.serializer.ComponentSerializers;
import ru.leymooo.simpleskins.SimpleSkins;
import ru.leymooo.simpleskins.utils.SkinUtils;

/**
 *
 * @author mikim
 */
public class SkinCommand implements Command {

    private final SimpleSkins plugin;
    private final Set<CommandSource> working = new HashSet<>();

    public SkinCommand(SimpleSkins plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSource cs, String[] args) {
        if (plugin.getConfig().getBoolean("use-permission") && !cs.hasPermission("simpleskins.skin")) {
            cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.no-permission"), '&'));
            return;
        }
        if (cs instanceof Player) {
            if (args.length == 0) {
                cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.help"), '&'));
                return;
            }
            if (working.contains(cs)) {
                cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.working"), '&'));
                return;
            }
            Player player = (Player) cs;
            working.add(cs);
            if (args[0].equalsIgnoreCase("update")) {
                plugin.getExecutorService().execute(() -> {
                    Optional<UUID> uuid = plugin.getDataBaseUtils().getUuid(player.getUsername());
                    if (uuid.isPresent()) {
                        cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.fetching"), '&'));
                        Optional<SkinUtils.FetchResult> newSkin = plugin.fetchSkin(Optional.of(player), uuid.get().toString(), false, false);
                        newSkin.ifPresent(skin -> {
                            cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.skin-changed"), '&'));
                            plugin.getSkinUtils().applySkin(player, skin.getProperty());
                        });
                    } else {
                        cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.skin-update-error"), '&'));
                    }
                    working.remove(cs);
                });
                return;
            }
            plugin.getExecutorService().execute(() -> {
                cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.fetching"), '&'));
                Optional<SkinUtils.FetchResult> newSkin = plugin.fetchSkin(Optional.of(player),
                        args[0].equalsIgnoreCase("reset") ? player.getUsername() : args[0], false, false);
                newSkin.ifPresent(skin -> {
                    cs.sendMessage(ComponentSerializers.LEGACY.deserialize(plugin.getConfig().getString("messages.skin-changed"), '&'));
                    plugin.getDataBaseUtils().saveUser(player.getUsername(), skin);
                    plugin.getSkinUtils().applySkin(player, skin.getProperty());

                });
                working.remove(cs);
            });
        }
    }

    @Override
    public boolean hasPermission(CommandSource cs, String[] strings) {
        return true;
    }

}
