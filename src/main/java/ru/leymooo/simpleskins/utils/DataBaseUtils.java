package ru.leymooo.simpleskins.utils;

import com.velocitypowered.api.util.GameProfile;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import ru.leymooo.simpleskins.SimpleSkins;

public class DataBaseUtils {

    private final SimpleSkins plugin;
    private Connection connection;

    private final String INSERT_SQL = "INSERT INTO `Users` (`Name`,`SkinUUID`,`SkinValue`,`SkinSignature`,`Timestamp`) VALUES (?,?,?,?,?);";
    private final String UPDATE_SQL = "UPDATE `Users` SET `SkinUUID` = ?,`SkinValue` = ?,`SkinSignature` = ?,`Timestamp` = ? WHERE `Name` = ?;";
    private final String SELECT_SKIN_SQL = "SELECT `SkinUUID`, `SkinValue`, `SkinSignature` FROM `Users` WHERE `Name` = ? LIMIT 1;";
    private final String SELECT_UUID_SQL = "SELECT `SkinUUID` FROM `Users` WHERE `Name` = ? LIMIT 1;";
    private final String SELECT_NAME_SQL = "SELECT `Name` FROM `Users` WHERE `Name` = ? LIMIT 1;";

    public DataBaseUtils(SimpleSkins plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        try {
            Class.forName("org.h2.Driver");
            this.connection = DriverManager.getConnection("jdbc:h2:." + File.separator + plugin.getDataDirectory().toString() + File.separator + "users;mode=MySQL;MULTI_THREADED=1;", null, null);
            try (Statement st = this.connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS `Users` ("
                        + "`Name` VARCHAR(16) NOT NULL PRIMARY KEY,"
                        + "`SkinUUID` UUID,"
                        + "`SkinValue` TEXT NOT NULL,"
                        + "`SkinSignature` TEXT NOT NULL,"
                        + "`Timestamp` BIGINT NOT NULL);";
                st.execute(sql);
            }
        } catch (SQLException | ClassNotFoundException ex) {
            plugin.getLogger().error("Can not init database", ex);
        }
    }

    public Optional<SkinFetcher.FetchResult> getProperty(String name) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_SKIN_SQL)) {
            ps.setString(1, name.toLowerCase());
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                return Optional.of(new SkinFetcher.FetchResult(set.getObject(1, UUID.class),
                        new GameProfile.Property("textures", set.getString(2), set.getString(3))));
            }
        } catch (SQLException ex) {
            plugin.getLogger().error("Can not execute SQL", ex);
        }
        return Optional.empty();
    }

    public Optional<UUID> getUuid(String name) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_UUID_SQL)) {
            ps.setString(1, name.toLowerCase());
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                return Optional.ofNullable(set.getObject(1, UUID.class));
            }
        } catch (SQLException ex) {
            plugin.getLogger().error("Can not execute SQL", ex);
        }
        return Optional.empty();
    }

    public void saveUser(String name, SkinFetcher.FetchResult result) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_NAME_SQL)) {
            ps.setString(1, name.toLowerCase());
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                updateUser(name, result.getId(), result.getProperty());
            } else {
                addUser(name, result.getId(), result.getProperty());
            }
        } catch (SQLException ex) {
            plugin.getLogger().error("Can not execute SQL", ex);
        }
    }

    private void addUser(String name, UUID uuid, GameProfile.Property property) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
            ps.setString(1, name.toLowerCase());
            ps.setObject(2, uuid);
            ps.setString(3, property.getValue());
            ps.setString(4, property.getSignature());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().error("Can not execute SQL", ex);
        }
    }

    private void updateUser(String name, UUID uuid, GameProfile.Property property) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_SQL)) {
            ps.setObject(1, uuid);
            ps.setString(2, property.getValue());
            ps.setString(3, property.getSignature());
            ps.setLong(4, System.currentTimeMillis());
            ps.setString(5, name.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().error("Can not execute SQL", ex);
        }
    }

    public void closeConnection() {
        try {
            this.connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().error("Can not close database connection", ex);
        }
    }
}
