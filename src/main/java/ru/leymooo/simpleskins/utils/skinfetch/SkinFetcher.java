/*
 * This file is part of ChangeSkin, licensed under the MIT License (MIT).
 *
 * Copyright (c) games647 <https://github.com/games647/ChangeSkin>
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
package ru.leymooo.simpleskins.utils.skinfetch;

import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import org.slf4j.Logger;
import ru.leymooo.simpleskins.SimpleSkins;
import ru.leymooo.simpleskins.utils.DataBaseUtils;
import ru.leymooo.simpleskins.utils.UuidFetchCache;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public class SkinFetcher {

    private static final String UUID_URL = "https://api.ashcon.app/mojang/v2/uuid/";
    //private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String ALTERNATIVE_SKIN_URL = "https://api.ashcon.app/mojang/v2/user/";

    private static final JsonParser JSON_PARSER = new JsonParser();
    private static final int TIMEOUT = 6000;
    private final SimpleSkins plugin;
    private final DataBaseUtils dataBaseUtils;
    private final UuidFetchCache uuidFetchCache;

    public SkinFetcher(SimpleSkins plugin, DataBaseUtils db, UuidFetchCache uuidCache) {
        this.plugin = plugin;
        this.dataBaseUtils = db;
        this.uuidFetchCache = uuidCache;
    }

    public Optional<FetchResult> getPlayerSkinFromDatabase(String userName) {
        return dataBaseUtils.getProperty(userName);
    }

    public Optional<FetchResult> fetchSkin(Player player, UUID uuid) {
        return fetchSkin(player, (Object) uuid);
    }

    public Optional<FetchResult> fetchSkin(Player player, String name) {
        return fetchSkin(player, (Object) name);
    }

    private Optional<FetchResult> fetchSkin(Player player, Object object) {
        try {
            Optional<FetchResult> result = object instanceof String ? getSkin((String) object) : getSkin((UUID) object);
            if (!result.isPresent()) {
                player.sendMessage(plugin.deserialize("messages", "working"));
                return Optional.empty();
            }
            return result;
        } catch (UserNotFoundException ignored) {

        } catch (IOException | JsonSyntaxException ex) {
            plugin.getLogger().error("Can not fetch skin", ex);
        }
        player.sendMessage(plugin.deserialize("messages", "skin-not-found"));
        return Optional.empty();
    }

    /**
     * Fetch skin. Will print error to console.
     *
     * @param name   - Name or UUID in string
     * @param silent - if false error will be printed to console
     * @return Optional of FetchResult
     */
    public Optional<FetchResult> fetchSkin(String name, boolean silent) {
        try {
            return getSkin(name);
        } catch (IOException | JsonSyntaxException | UserNotFoundException ex) {
            if (!silent) {
                plugin.getLogger().error("Can not fetch skin for {}", name, ex);
            }
        }
        return Optional.empty();
    }

    private Optional<FetchResult> getSkin(String name) throws UserNotFoundException, IOException, JsonSyntaxException {
        UUID uuid = (uuid = getUuidIfValid(name)) == null ? fetchUUID(name) : uuid;
        return getSkin(uuid);
    }

    private Optional<FetchResult> getSkin(UUID uuid) throws UserNotFoundException, IOException, JsonSyntaxException {
        if (uuidFetchCache.isWorking(uuid)) {
            return Optional.empty();
        }
        uuidFetchCache.addWorking(uuid);
        try {
            Optional<FetchResult> result = uuidFetchCache.getIfCached(uuid);
            if (result.isPresent()) {
                checkFetchResult(result.get());
                return result;
            }
            result = Optional.of(fetchSkinAlternative(uuid));
            uuidFetchCache.cache(result.get());
            checkFetchResult(result.get());
            return result;
        } finally {
            uuidFetchCache.removeWorking(uuid);
        }
    }

    private UUID fetchUUID(String username) throws IOException, UserNotFoundException, JsonSyntaxException {
        if (username.length() > 16) {
            throw new UserNotFoundException(username);
        }
        HttpURLConnection connection = getConnection(UUID_URL + username);
        int responseCode = connection.getResponseCode();

        if (validate(responseCode)) {
            return UUID.fromString(getJson(connection).getAsJsonPrimitive().getAsString());
        } else if (responseCode != HttpURLConnection.HTTP_NO_CONTENT && responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
            printErrorStream(connection, responseCode);
        }
        throw new UserNotFoundException(username);
    }

    private void checkFetchResult(FetchResult result) throws UserNotFoundException {
        if (result instanceof RateLimitedFetchResult) {
            throw new UserNotFoundException("Can not fetch skin due to rate-limit for " + result.getId());
        }
    }

    /*
    private FetchResult fetchSkin(UUID uuid) throws IOException, UserNotFoundException, JsonSyntaxException {
        HttpURLConnection connection = getConnection(SKIN_URL + UuidUtils.toUndashed(uuid) + "?unsigned=false");
        int responseCode = connection.getResponseCode();

        if (validate(responseCode)) {
            JsonObject paresed = getJson(connection).getAsJsonObject();
            JsonObject skin = paresed.getAsJsonArray("properties").get(0).getAsJsonObject();
            return new SkinFetchResult(uuid,
                    new GameProfile.Property("textures", skin.get("value").getAsString(), skin.get("signature").getAsString()));
        } else if (responseCode == 429) { //Rate limited
            return fetchSkinAlternative(uuid);
        } else if (responseCode != HttpURLConnection.HTTP_NO_CONTENT && responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
            printErrorStream(connection, responseCode);
        }
        throw new UserNotFoundException(uuid.toString());
    }
    */

    private FetchResult fetchSkinAlternative(UUID uuid) throws IOException, UserNotFoundException, JsonSyntaxException {
        HttpURLConnection connection = getConnection(ALTERNATIVE_SKIN_URL + uuid.toString());
        int responseCode = connection.getResponseCode();

        if (validate(responseCode)) {
            JsonObject parsed = getJson(connection).getAsJsonObject();
            JsonObject skin = parsed.getAsJsonObject("textures").getAsJsonObject("raw");
            return new SkinFetchResult(uuid,
                    new GameProfile.Property("textures", skin.get("value").getAsString(), skin.get("signature").getAsString()));
        } else if (responseCode != HttpURLConnection.HTTP_NO_CONTENT && responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
            printErrorStream(connection, responseCode);
        }
        throw new UserNotFoundException(uuid.toString());
    }

    private JsonElement getJson(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            return JSON_PARSER.parse(reader);
        }
    }

    private boolean validate(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_OK;
    }

    private UUID getUuidIfValid(String toParse) {
        try {
            return UUID.fromString(toParse);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private HttpURLConnection getConnection(String url) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) new URL(url).openConnection();

        httpConnection.setConnectTimeout(TIMEOUT);
        httpConnection.setReadTimeout(2 * TIMEOUT);

        httpConnection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json");
        httpConnection.setRequestProperty(HttpHeaders.USER_AGENT, "SimpleSkins-velocity-plugin");
        return httpConnection;
    }

    private void printErrorStream(HttpURLConnection connection, int responseCode) throws IOException {
        Logger logger = plugin.getLogger();
        //this necessary, because we cannot access input stream if the response code is something like 404
        try (InputStream in = responseCode < HttpURLConnection.HTTP_BAD_REQUEST
                ? connection.getInputStream() : connection.getErrorStream()) {
            logger.error("Received response: {} for {}", responseCode, connection.getURL());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                logger.error("Error stream: {}", CharStreams.toString(reader));
            }
        }
    }

    static class UserNotFoundException extends Exception {

        UserNotFoundException(String userName) {
            super(userName);
        }
    }
}
