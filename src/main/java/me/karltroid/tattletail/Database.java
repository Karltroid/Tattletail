package me.karltroid.tattletail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class Database
{
    static final String DATABASE_NAME = "database.db";
    static final String IGNORED_PLAYERS_TABLE_NAME = "ignored_players";
    static final String IGNORED_LOCATIONS_TABLE_NAME = "ignored_locations";

    private static Connection getConnection(Plugin plugin) throws SQLException {

        if (plugin == null) return null;

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }
        String databasePath = dataFolder.getAbsolutePath() + File.separator + DATABASE_NAME;
        String jdbcUrl = "jdbc:sqlite:" + databasePath;
        return DriverManager.getConnection(jdbcUrl);
    }

    public static void createTables() {
        try (Connection conn = getConnection(Tattletail.getInstance());
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + IGNORED_PLAYERS_TABLE_NAME + " ("
                            + "player1UUID VARCHAR(36) NOT NULL,"
                            + "player2UUID VARCHAR(36) NOT NULL,"
                            + "PRIMARY KEY (player1UUID, player2UUID)"
                            + ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + IGNORED_LOCATIONS_TABLE_NAME + " ("
                            + "world VARCHAR(36) NOT NULL,"
                            + "x INT NOT NULL,"
                            + "y INT NOT NULL,"
                            + "z INT NOT NULL,"
                            + "PRIMARY KEY (world, x, y, z)"
                            + ")"
            );
            Tattletail.log("Tables created successfully");
        } catch (SQLException e) {
            getLogger().severe("Failed to create database table: " + e.getMessage());
        }
    }


    public static void loadIgnoredPlayers()
    {
        try (Connection conn = getConnection(Tattletail.getInstance()))
        {
            try (PreparedStatement ignoredPlayersDataStatement = conn.prepareStatement("SELECT * FROM " + IGNORED_PLAYERS_TABLE_NAME))
            {
                try (ResultSet ignoredPlayerDataResult = ignoredPlayersDataStatement.executeQuery())
                {
                    Tattletail.getInstance().ignorePlayerCombos = new ArrayList<>();

                    while (ignoredPlayerDataResult.next())
                    {
                        UUID player1UUID = UUID.fromString(ignoredPlayerDataResult.getString("player1UUID"));
                        UUID player2UUID = UUID.fromString(ignoredPlayerDataResult.getString("player2UUID"));

                        Tattletail.getInstance().ignorePlayerCombos.add(new UUID[]{player1UUID, player2UUID});
                    }

                    Tattletail.log("Ignored players loaded successfully");
                }
            }
        }
        catch (SQLException e)
        {
            getLogger().severe("Failed to load data from database: " + e.getMessage());
        }
    }

    public static void loadIgnoredLocations()
    {
        try (Connection conn = getConnection(Tattletail.getInstance()))
        {
            try (PreparedStatement ignoredLocationsDataStatement = conn.prepareStatement("SELECT * FROM " + IGNORED_LOCATIONS_TABLE_NAME))
            {
                try (ResultSet ignoredLocationsDataResult = ignoredLocationsDataStatement.executeQuery())
                {
                    Tattletail.getInstance().ignoreLocations = new ArrayList<>();

                    while (ignoredLocationsDataResult.next())
                    {
                        String worldName = ignoredLocationsDataResult.getString("world");
                        Tattletail.log(worldName);
                        World world = Bukkit.getWorld(worldName);
                        int x = ignoredLocationsDataResult.getInt("x");
                        int y = ignoredLocationsDataResult.getInt("y");
                        int z = ignoredLocationsDataResult.getInt("z");

                        Tattletail.getInstance().ignoreLocations.add(new Location(world, x, y, z));
                    }
                }
            }
        }
        catch (SQLException e)
        {
            getLogger().severe("Failed to load data from database: " + e.getMessage());
        }
    }

    public static void saveIgnoredPlayers()
    {
        if (Tattletail.getInstance().ignorePlayerCombos.isEmpty())
            return;

        try (Connection conn = getConnection(Tattletail.getInstance());
             PreparedStatement deleteIgnoredPlayersStatement = conn.prepareStatement(
                     "DELETE FROM " + IGNORED_PLAYERS_TABLE_NAME
             );
             PreparedStatement insertIgnoredPlayersStatement = conn.prepareStatement(
                     "INSERT INTO " + IGNORED_PLAYERS_TABLE_NAME + " (player1UUID, player2UUID) VALUES (?, ?)" +
                             "ON CONFLICT(player1UUID, player2UUID) DO UPDATE SET player1UUID = excluded.player1UUID, player2UUID = excluded.player2UUID"
             );
        ) {
            // clear database
            deleteIgnoredPlayersStatement.executeUpdate();

            // fill table with new data
            for (UUID[] ignorePlayerCombo : Tattletail.getInstance().ignorePlayerCombos)
            {
                insertIgnoredPlayersStatement.setString(1, ignorePlayerCombo[0].toString());
                insertIgnoredPlayersStatement.setString(2, ignorePlayerCombo[1].toString());
                insertIgnoredPlayersStatement.addBatch();
            }
            insertIgnoredPlayersStatement.executeBatch();
        }
        catch (SQLException e)
        {
            getLogger().severe("Failed to save data to database: " + e.getMessage());
        }
    }

    public static void saveIgnoredLocations()
    {
        if (Tattletail.getInstance().ignoreLocations.isEmpty())
            return;

        try (Connection conn = getConnection(Tattletail.getInstance());
             PreparedStatement deleteIgnoredLocationsStatement = conn.prepareStatement(
                     "DELETE FROM " + IGNORED_LOCATIONS_TABLE_NAME
             );
             PreparedStatement insertIgnoredLocationsStatement = conn.prepareStatement(
                     "INSERT INTO " + IGNORED_LOCATIONS_TABLE_NAME + " (world, x, y, z) VALUES (?, ?, ?, ?)" +
                             "ON CONFLICT(world, x, y, z) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z"
             );
        ) {
            // clear database
            deleteIgnoredLocationsStatement.executeUpdate();

            // fill table with new data
            for (Location ignoreLocation : Tattletail.getInstance().ignoreLocations)
            {
                insertIgnoredLocationsStatement.setString(1, ignoreLocation.getWorld().getName());
                insertIgnoredLocationsStatement.setInt(2, ignoreLocation.getBlockX());
                insertIgnoredLocationsStatement.setInt(3, ignoreLocation.getBlockY());
                insertIgnoredLocationsStatement.setInt(4, ignoreLocation.getBlockZ());
                insertIgnoredLocationsStatement.addBatch();
            }
            insertIgnoredLocationsStatement.executeBatch();
        }
        catch (SQLException e)
        {
            getLogger().severe("Failed to save data to database: " + e.getMessage());
        }
    }
}
