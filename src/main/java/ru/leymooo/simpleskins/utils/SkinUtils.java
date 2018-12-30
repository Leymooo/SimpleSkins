/*
 * This file is part of ChangeSkin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://github.com/games647/ChangeSkin>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ru.leymooo.simpleskins.utils;

import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import ru.leymooo.simpleskins.SimpleSkins;

/**
 *
 * @author mikim
 */
public class SkinUtils {

    private static final String PROFILE_URL = "https://api.ashcon.app/mojang/v2/user/";
    private static final JsonParser JSON_PARSER = new JsonParser();
    private final SimpleSkins plugin;

    public SkinUtils(SimpleSkins plugin) {
        this.plugin = plugin;
    }

    public GameProfile applySkin(GameProfile original, GameProfile.Property property) {
        List<GameProfile.Property> properties = createProperties(original.getProperties(), property);
        return new GameProfile(original.getId(), original.getName(), properties);
    }

    public void applySkin(Player player, GameProfile.Property property) {
        player.setGameProfileProperties(createProperties(player.getGameProfileProperties(), property));
    }

    private List<GameProfile.Property> createProperties(List<GameProfile.Property> list, GameProfile.Property property) {
        List<GameProfile.Property> properties = new ArrayList<>(list);
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

    public FetchResult fetchSkin(String username) throws IOException, UserNotFoundExeption, JsonSyntaxException {
        HttpURLConnection connection = getConnection(PROFILE_URL + username);
        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new UserNotFoundExeption(username);
        } else if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject paresed = JSON_PARSER.parse(reader).getAsJsonObject();
                JsonObject skin = paresed.get("textures").getAsJsonObject().get("raw").getAsJsonObject();
                return new FetchResult(UUID.fromString(paresed.get("uuid").getAsString()),
                        new GameProfile.Property("textures", skin.get("value").getAsString(), skin.get("signature").getAsString()));
            }
        } else {
            printErrorStream(connection, responseCode);
        }
        throw new UserNotFoundExeption(username);

    }

    private static final int TIMEOUT = 5000;

    private HttpURLConnection getConnection(String url) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) new URL(url).openConnection();

        httpConnection.setConnectTimeout(TIMEOUT);
        httpConnection.setReadTimeout(2 * TIMEOUT);

        httpConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
        httpConnection.setRequestProperty(HttpHeaders.USER_AGENT, "SimpleSkins-velocity-plugin");
        return httpConnection;
    }

    private void printErrorStream(HttpURLConnection connection, int responseCode) throws IOException {
        boolean proxy = connection.usingProxy();
        Logger logger = plugin.getLogger();
        //this necessary, because we cannot access input stream if the response code is something like 404
        try (InputStream in = responseCode < HttpURLConnection.HTTP_BAD_REQUEST
                ? connection.getInputStream() : connection.getErrorStream()) {
            logger.error("Received response: {} for {}", responseCode, connection.getURL(), proxy);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                logger.error("Error stream: {}", CharStreams.toString(reader));
            }
        }
    }

    public static class UserNotFoundExeption extends Exception {

        public UserNotFoundExeption(String userName) {
            super(userName);
        }
    }

    public static class FetchResult {

        private final UUID id;
        private final GameProfile.Property property;

        public FetchResult(UUID id, GameProfile.Property property) {
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
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            if (!Objects.equals(this.property, other.property)) {
                return false;
            }
            return true;
        }

    }
}
