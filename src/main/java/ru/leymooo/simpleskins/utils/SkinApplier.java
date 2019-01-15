package ru.leymooo.simpleskins.utils;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import java.util.ArrayList;
import java.util.List;

public class SkinApplier {

    public static GameProfile createProfileWithSkin(GameProfile original, GameProfile.Property toApply) {
        List<GameProfile.Property> properties = createProperties(original.getProperties(), toApply);
        return new GameProfile(original.getId(), original.getName(), properties);
    }

    public static void applySkin(Player player, GameProfile.Property property) {
        player.setGameProfileProperties(createProperties(player.getGameProfileProperties(), property));
    }

    private static List<GameProfile.Property> createProperties(List<GameProfile.Property> original, GameProfile.Property property) {
        List<GameProfile.Property> properties = new ArrayList<>(original);
        boolean applied = false;
        for (int i = 0; i < properties.size(); i++) {
            GameProfile.Property lproperty = properties.get(i);
            if ("textures".equals(lproperty.getName())) {
                properties.set(i, property);
                applied = true;
            }
        }
        if (!applied) {
            properties.add(property);
        }
        return properties;
    }
}
