package nl.pim16aap2.bigDoors.storage.sqlite;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.Door;
import nl.pim16aap2.bigDoors.util.DoorDirection;
import nl.pim16aap2.bigDoors.util.DoorType;
import nl.pim16aap2.bigDoors.util.RotateDirection;
import nl.pim16aap2.bigDoors.util.Util;

public class SQLiteJDBCDriverConnection
{
    private BigDoors plugin;
    private File     dataFolder;
    private String   url;
    private static final String DRIVER = "org.sqlite.JDBC";
    private static final int DATABASE_VERSION    =  2;

    private static final int DOOR_ID             =  1;
    private static final int DOOR_NAME           =  2;
    private static final int DOOR_WORLD          =  3;
    private static final int DOOR_OPEN           =  4;
    private static final int DOOR_MIN_X          =  5;
    private static final int DOOR_MIN_Y          =  6;
    private static final int DOOR_MIN_Z          =  7;
    private static final int DOOR_MAX_X          =  8;
    private static final int DOOR_MAX_Y          =  9;
    private static final int DOOR_MAX_Z          = 10;
    private static final int DOOR_ENG_X          = 11;
    private static final int DOOR_ENG_Y          = 12;
    private static final int DOOR_ENG_Z          = 13;
    private static final int DOOR_LOCKED         = 14;
    private static final int DOOR_TYPE           = 15;
    private static final int DOOR_ENG_SIDE       = 16;
    private static final int DOOR_POWER_X        = 17;
    private static final int DOOR_POWER_Y        = 18;
    private static final int DOOR_POWER_Z        = 19;
    private static final int DOOR_OPEN_DIR       = 20;
    private static final int DOOR_AUTO_CLOSE     = 21;
    private static final int DOOR_CHUNK_HASH     = 22;
    private static final int DOOR_BLOCKS_TO_MOVE = 23;

    private static final int PLAYERS_ID          =  1;
    private static final int PLAYERS_UUID        =  2;

    @SuppressWarnings("unused")
    private static final int UNION_ID            =  1;
    private static final int UNION_PERM          =  2;
    private static final int UNION_PLAYER_ID     =  3;
    private static final int UNION_DOOR_ID       =  4;

    public SQLiteJDBCDriverConnection(BigDoors plugin, String dbName)
    {
        this.plugin = plugin;
        dataFolder  = new File(plugin.getDataFolder(), dbName);
        url         = "jdbc:sqlite:" + dataFolder;
        init();
        upgrade();
    }

    // Establish a connection.
    public Connection getConnection()
    {
        Connection conn = null;
        try
        {
            Class.forName(DRIVER);
            conn = DriverManager.getConnection(url);
            conn.createStatement().execute("PRAGMA foreign_keys=ON");
        }
        catch (SQLException ex)
        {
            plugin.getMyLogger().logMessage("53: Failed to open connection!", true, false);
        }
        catch (ClassNotFoundException e)
        {
            plugin.getMyLogger().logMessage("57: Failed to open connection: CLass not found!!", true, false);
        }
        return conn;
    }

    // Initialize the tables.
    public void init()
    {
        if (!dataFolder.exists())
        {
            try
            {
                dataFolder.createNewFile();
                plugin.getMyLogger().logMessageToLogFile("New file created at " + dataFolder);
            }
            catch (IOException e)
            {
                plugin.getMyLogger().logMessageToLogFile("File write error: " + dataFolder);
            }
        }

        // Table creation
        Connection conn = null;
        try
        {
            conn = getConnection();

            Statement stmt1 = conn.createStatement();
            String sql1     = "CREATE TABLE IF NOT EXISTS doors "
                            + "(id            INTEGER    PRIMARY KEY autoincrement, "
                            + " name          TEXT       NOT NULL, "
                            + " world         TEXT       NOT NULL, "
                            + " isOpen        INTEGER    NOT NULL, "
                            + " xMin          INTEGER    NOT NULL, "
                            + " yMin          INTEGER    NOT NULL, "
                            + " zMin          INTEGER    NOT NULL, "
                            + " xMax          INTEGER    NOT NULL, "
                            + " yMax          INTEGER    NOT NULL, "
                            + " zMax          INTEGER    NOT NULL, "
                            + " engineX       INTEGER    NOT NULL, "
                            + " engineY       INTEGER    NOT NULL, "
                            + " engineZ       INTEGER    NOT NULL, "
                            + " isLocked      INTEGER    NOT NULL, "
                            + " type          INTEGER    NOT NULL, "
                            + " engineSide    INTEGER    NOT NULL, "
                            + " powerBlockX   INTEGER    NOT NULL, "
                            + " powerBlockY   INTEGER    NOT NULL, "
                            + " powerBlockZ   INTEGER    NOT NULL, "
                            + " openDirection INTEGER    NOT NULL, "
                            + " autoClose     INTEGER    NOT NULL, "
                            + " chunkHash     INTEGER    NOT NULL, "
                            + " blocksToMove  INTEGER    NOT NULL) ";
            stmt1.executeUpdate(sql1);
            stmt1.close();

            Statement stmt2 = conn.createStatement();
            String sql2     = "CREATE TABLE IF NOT EXISTS players "
                            + "(id          INTEGER    PRIMARY KEY AUTOINCREMENT, "
                            + " playerUUID  TEXT       NOT NULL)";
            stmt2.executeUpdate(sql2);
            stmt2.close();

            Statement stmt3 = conn.createStatement();
            String sql3     = "CREATE TABLE IF NOT EXISTS sqlUnion "
                            + "(id          INTEGER    PRIMARY KEY AUTOINCREMENT, "
                            + " permission  INTEGER    NOT NULL, "
                            + " playerID    REFERENCES players(id) ON UPDATE CASCADE ON DELETE CASCADE, "
                            + " doorUID     REFERENCES doors(id)   ON UPDATE CASCADE ON DELETE CASCADE)";
            stmt3.executeUpdate(sql3);
            stmt3.close();
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("128: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("138: " + e.getMessage());
            }
            catch (Exception e)
            {
                plugin.getMyLogger().logMessageToLogFile("142: " + e.getMessage());
            }
        }
    }

    // Get the permission level for a given player for a given door.
    public int getPermission(String playerUUID, long doorUID)
    {
        Connection conn = null;
        int ret = -1;
        try
        {
            conn = getConnection();
            // Get the player ID as used in the sqlUnion table.
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM players WHERE playerUUID = '" + playerUUID + "';");
            ResultSet rs1         = ps1.executeQuery();
            while (rs1.next())
            {
                int playerID = rs1.getInt(PLAYERS_ID);
                // Select all doors from the sqlUnion table that have the previously found player as owner.
                PreparedStatement ps2 = conn.prepareStatement("SELECT * FROM sqlUnion WHERE playerID = '" + playerID + "' AND doorUID = '" + doorUID + "';");
                ResultSet rs2         = ps2.executeQuery();
                while (rs2.next())
                    ret = rs2.getInt(UNION_PERM);
                ps2.close();
                rs2.close();
            }
            ps1.close();
            rs1.close();
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("174: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("184: " + e.getMessage());
            }
        }

        return ret;
    }

    // Construct a new door from a resultset.
    public Door newDoorFromRS(ResultSet rs, long doorUID, int permission, UUID playerUUID)
    {
        try
        {
            World world     = Bukkit.getServer().getWorld(UUID.fromString(rs.getString(DOOR_WORLD)));
            Location min    = new Location(world, rs.getInt(DOOR_MIN_X),   rs.getInt(DOOR_MIN_Y),   rs.getInt(DOOR_MIN_Z));
            Location max    = new Location(world, rs.getInt(DOOR_MAX_X),   rs.getInt(DOOR_MAX_Y),   rs.getInt(DOOR_MAX_Z));
            Location engine = new Location(world, rs.getInt(DOOR_ENG_X),   rs.getInt(DOOR_ENG_Y),   rs.getInt(DOOR_ENG_Z));
            Location powerB = new Location(world, rs.getInt(DOOR_POWER_X), rs.getInt(DOOR_POWER_Y), rs.getInt(DOOR_POWER_Z));

            Door door = new Door(playerUUID, world, min, max, engine, rs.getString(DOOR_NAME), (rs.getInt(DOOR_OPEN) == 1 ? true : false),
                                 doorUID, (rs.getInt(DOOR_LOCKED) == 1 ? true : false), permission, DoorType.valueOf(rs.getInt(DOOR_TYPE)),
                                 DoorDirection.valueOf(rs.getInt(DOOR_ENG_SIDE)), powerB, RotateDirection.valueOf(rs.getInt(DOOR_OPEN_DIR)),
                                 rs.getInt(DOOR_AUTO_CLOSE));

            door.setBlocksToMove(rs.getInt(DOOR_BLOCKS_TO_MOVE));
            return door;
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("208: " + e.getMessage());
            return null;
        }
    }

    // Remove a door with a given ID.
    public void removeDoor(long doorID)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            String deleteDoor    = "DELETE FROM doors WHERE id = '" + doorID + "';";
            PreparedStatement ps = conn.prepareStatement(deleteDoor);
            ps.executeUpdate();
            ps.close();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("250: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("260: " + e.getMessage());
            }
        }
    }

    // Remove a door with a given name, owned by a certain player.
    public void removeDoor(String playerUUID, String doorName)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            // Get the player ID as used in the sqlUnion table.
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM players WHERE playerUUID = '" + playerUUID + "';");
            ResultSet rs1         = ps1.executeQuery();
            while (rs1.next())
            {
                int playerID = rs1.getInt(PLAYERS_ID);
                // Select all doors from the sqlUnion table that have the previously found player as owner.
                PreparedStatement ps2 = conn.prepareStatement("SELECT * FROM sqlUnion WHERE playerID = '" + playerID + "';");
                ResultSet rs2         = ps2.executeQuery();
                while (rs2.next())
                {
                    // Delete all doors with the provided name owned by the provided player.
                    PreparedStatement ps3 = conn.prepareStatement("DELETE FROM doors WHERE id = '" + rs2.getInt(UNION_DOOR_ID)
                                                                            + "' AND name = '" + doorName + "';");
                    ps3.executeUpdate();
                    ps3.close();
                }
                ps2.close();
                rs2.close();
            }
            ps1.close();
            rs1.close();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("297: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("307: " + e.getMessage());
            }
        }
    }

    // Get Door from a doorID.
    public Door getDoor(long doorID)
    {
        Door door = null;

        Connection conn = null;
        try
        {
            conn = getConnection();
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM doors WHERE id = '" + doorID + "';");
            ResultSet rs1         = ps1.executeQuery();
            while (rs1.next())
            {
                String foundPlayerUUID = null;
                int permission = -1;
                PreparedStatement ps2  = conn.prepareStatement("SELECT * FROM sqlUnion WHERE doorUID = '" + rs1.getLong(DOOR_ID) + "';");
                ResultSet rs2          = ps2.executeQuery();
                while (rs2.next())
                {
                    permission = rs2.getInt(UNION_PERM);
                    PreparedStatement ps3 = conn.prepareStatement("SELECT * FROM players WHERE id = '" + rs2.getInt(UNION_PLAYER_ID) + "';");
                    ResultSet rs3         = ps3.executeQuery();
                    while (rs3.next())
                        foundPlayerUUID   = rs3.getString(PLAYERS_UUID);
                    ps3.close();
                    rs3.close();
                }
                ps2.close();
                rs2.close();

                door = newDoorFromRS(rs1, rs1.getLong(DOOR_ID), permission, UUID.fromString(foundPlayerUUID));
            }
            ps1.close();
            rs1.close();
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("387: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("397: " + e.getMessage());
            }
        }
        return door;
    }

    // Get ALL doors owned by a given playerUUID.
    public ArrayList<Door> getDoors(String playerUUID, String name)
    {
        return getDoors(playerUUID, name, 0, Long.MAX_VALUE);
    }

    // Get all doors with a given name.
    public ArrayList<Door> getDoors(String name)
    {
        ArrayList<Door> doors = new ArrayList<Door>();

        Connection conn = null;
        try
        {
            conn = getConnection();

            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM doors WHERE name = '" + name + "';");
            ResultSet rs1         = ps1.executeQuery();

            while (rs1.next())
            {
                String foundPlayerUUID = null;
                int    permission      = -1;
                PreparedStatement ps2  = conn.prepareStatement("SELECT * FROM sqlUnion WHERE doorUID = '" + rs1.getLong(DOOR_ID) + "';");
                ResultSet rs2          = ps2.executeQuery();
                while (rs2.next())
                {
                    permission            = rs2.getInt(UNION_PERM);
                    PreparedStatement ps3 = conn.prepareStatement("SELECT * FROM players WHERE id = '" + rs2.getInt(UNION_PLAYER_ID) + "';");
                    ResultSet rs3         = ps3.executeQuery();
                    while (rs3.next())
                        foundPlayerUUID   = rs3.getString(PLAYERS_UUID);
                    ps3.close();
                    rs3.close();
                }
                ps2.close();
                rs2.close();

                doors.add(newDoorFromRS(rs1, rs1.getLong(DOOR_ID), permission, UUID.fromString(foundPlayerUUID)));
            }
            ps1.close();
            rs1.close();
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("448: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("458: " + e.getMessage());
            }
        }
        return doors;
    }

    // Get all doors associated with this player in a given range. Name can be null
    public ArrayList<Door> getDoors(String playerUUID, String name, long start, long end)
    {
        ArrayList<Door> doors = new ArrayList<Door>();

        Connection conn = null;
        try
        {
            conn = getConnection();

            int playerID          = -1;
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM players WHERE playerUUID = '" + playerUUID + "';");
            ResultSet rs1         = ps1.executeQuery();
            while (rs1.next())
                playerID = rs1.getInt(PLAYERS_ID);

            ps1.close();
            rs1.close();

            PreparedStatement ps2 = conn.prepareStatement("SELECT * FROM sqlUnion WHERE playerID = '" + playerID + "';");
            ResultSet rs2         = ps2.executeQuery();
            int count             = 0;
            while (rs2.next())
            {
                PreparedStatement ps3 = conn.prepareStatement("SELECT * FROM doors WHERE id = '" + rs2.getInt(UNION_DOOR_ID) + "';");
                ResultSet rs3         = ps3.executeQuery();
                while (rs3.next())
                {
                    if ((name == null || (name != null && rs3.getString(DOOR_NAME).equals(name))) && count >= start && count <= end)
                        doors.add(newDoorFromRS(rs3, rs3.getLong(DOOR_ID), rs2.getInt(UNION_PERM), UUID.fromString(playerUUID)));
                    ++count;
                }
                ps3.close();
                rs3.close();
            }
            ps2.close();
            rs2.close();
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("501: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("511: " + e.getMessage());
            }
        }
        return doors;
    }

    public HashMap<Long, Long> getPowerBlockData(long chunkHash)
    {
        HashMap<Long, Long> doors = new HashMap<Long, Long>();

        Connection conn = null;
        try
        {
            conn = getConnection();
            // Get the door associated with the x/y/z location of the power block block.
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM doors WHERE chunkHash = '" + chunkHash + "';");
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                doors.put(Util.locationHash(rs.getInt(DOOR_POWER_X), rs.getInt(DOOR_POWER_Y), rs.getInt(DOOR_POWER_Z), UUID.fromString(rs.getString(DOOR_WORLD))),
                          rs.getLong(DOOR_ID));
            }
            ps.close();
            rs.close();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("337: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("347: " + e.getMessage());
            }
        }
        return doors;
    }

    public void updateDoorBlocksToMove(long doorID, int blocksToMove)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET "
                          +   "blocksToMove='" + blocksToMove
                          + "' WHERE id = '"   + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("540: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("550: " + e.getMessage());
            }
        }
    }

    // Update the door at doorUID with the provided coordinates and open status.
    public void updateDoorCoords(long doorID, boolean isOpen, int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, DoorDirection engSide)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET "
                          +   "xMin='"       + xMin
                          + "',yMin='"       + yMin
                          + "',zMin='"       + zMin
                          + "',xMax='"       + xMax
                          + "',yMax='"       + yMax
                          + "',zMax='"       + zMax
                          + "',isOpen='"     + (isOpen  == true ?  1 : 0)
                          + "',engineSide='" + (engSide == null ? -1 : DoorDirection.getValue(engSide))
                          + "' WHERE id = '" + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("540: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("550: " + e.getMessage());
            }
        }
    }

    // Update the door with UID doorUID's Power Block Location with the provided coordinates and open status.
    public void updateDoorAutoClose(long doorID, int autoClose)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET "
                          +   "autoClose='"  + autoClose
                          + "' WHERE id = '" + doorID  + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("571: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("581: " + e.getMessage());
            }
        }
    }
    // Update the door with UID doorUID's Power Block Location with the provided coordinates and open status.
    public void updateDoorOpenDirection(long doorID, RotateDirection openDir)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET "
                          +   "openDirection='" + RotateDirection.getValue(openDir)
                          + "' WHERE id = '"    + doorID  + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("601: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("611: " + e.getMessage());
            }
        }
    }

    // Update the door with UID doorUID's Power Block Location with the provided coordinates and open status.
    public void updateDoorPowerBlockLoc(long doorID, int xPos, int yPos, int zPos, UUID worldUUID)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET "
                          +   "powerBlockX='" + xPos
                          + "',powerBlockY='" + yPos
                          + "',powerBlockZ='" + zPos
                          + "',chunkHash='"   + Util.chunkHashFromLocation(xPos, zPos, worldUUID)
                          + "' WHERE id = '"  + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("634: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("644: " + e.getMessage());
            }
        }
    }

    // Check if a given location already contains a power block or not.
    // Returns false if it's already occupied.
    public boolean isPowerBlockLocationEmpty(Location loc)
    {
        // Prepare door and connection.
        Connection conn = null;
        try
        {
            conn = getConnection();
            // Get the door associated with the x/y/z location of the power block block.
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM doors WHERE powerBlockX = '" + loc.getBlockX()     +
                                                                             "' AND powerBlockY = '" + loc.getBlockY()     +
                                                                             "' AND powerBlockZ = '" + loc.getBlockZ()     +
                                                                             "' AND world = '"       + loc.getWorld().getUID().toString() + "';");
            ResultSet rs = ps.executeQuery();
            boolean isAvailable = true;

            if (rs.next())
                isAvailable = false;

            ps.close();
            rs.close();
            return isAvailable;
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("687: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("697: " + e.getMessage());
            }
        }

        return false;
    }

    // Update the door at doorUID with the provided new lockstatus.
    public void setLock(long doorID, boolean newLockStatus)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET "
                          +   "isLocked='" + (newLockStatus == true ? 1 : 0)
                          + "' WHERE id='" + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("720: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("730: " + e.getMessage());
            }
        }
    }

    // Insert a new door in the db.
    public void insert(Door door)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();

            long playerID = -1;
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM players WHERE playerUUID = '" + door.getPlayerUUID().toString() + "';");
            ResultSet rs1         = ps1.executeQuery();

            while (rs1.next())
                playerID = rs1.getLong(PLAYERS_ID);
            ps1.close();
            rs1.close();

            if (playerID == -1)
            {
                Statement stmt2 = conn.createStatement();
                String sql2     = "INSERT INTO players (playerUUID) "
                                + "VALUES ('" + door.getPlayerUUID().toString() + "');";
                stmt2.executeUpdate(sql2);
                stmt2.close();

                String query          = "SELECT last_insert_rowid() AS lastId";
                PreparedStatement ps2 = conn.prepareStatement(query);
                ResultSet rs2         = ps2.executeQuery();
                playerID              = rs2.getLong("lastId");
                ps2.close();
                rs2.close();
            }

            String doorInsertsql = "INSERT INTO doors(name,world,isOpen,xMin,yMin,zMin,xMax,yMax,zMax,engineX,engineY,engineZ,isLocked,type,engineSide,powerBlockX,powerBlockY,powerBlockZ,openDirection,autoClose,chunkHash,blocksToMove) "
                                 + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            PreparedStatement doorstatement = conn.prepareStatement(doorInsertsql);

            doorstatement.setString(DOOR_NAME - 1,         door.getName());
            doorstatement.setString(DOOR_WORLD - 1,        door.getWorld().getUID().toString());
            doorstatement.setInt(DOOR_OPEN - 1,            door.isOpen() == true ? 1 : 0);
            doorstatement.setInt(DOOR_MIN_X - 1,           door.getMinimum().getBlockX());
            doorstatement.setInt(DOOR_MIN_Y - 1,           door.getMinimum().getBlockY());
            doorstatement.setInt(DOOR_MIN_Z - 1,           door.getMinimum().getBlockZ());
            doorstatement.setInt(DOOR_MAX_X - 1,           door.getMaximum().getBlockX());
            doorstatement.setInt(DOOR_MAX_Y - 1,           door.getMaximum().getBlockY());
            doorstatement.setInt(DOOR_MAX_Z - 1,           door.getMaximum().getBlockZ());
            doorstatement.setInt(DOOR_ENG_X - 1,           door.getEngine().getBlockX());
            doorstatement.setInt(DOOR_ENG_Y - 1,           door.getEngine().getBlockY());
            doorstatement.setInt(DOOR_ENG_Z - 1,           door.getEngine().getBlockZ());
            doorstatement.setInt(DOOR_LOCKED - 1,          door.isLocked() == true ? 1 : 0);
            doorstatement.setInt(DOOR_TYPE - 1,            DoorType.getValue(door.getType()));
            // Set -1 if the door has no engineSide (normal doors don't use it)
            doorstatement.setInt(DOOR_ENG_SIDE - 1,        door.getEngSide() == null ? -1 : DoorDirection.getValue(door.getEngSide()));
            doorstatement.setInt(DOOR_POWER_X - 1,         door.getEngine().getBlockX());
            doorstatement.setInt(DOOR_POWER_Y - 1,         door.getEngine().getBlockY() - 1); // Power Block Location is 1 block below the engine, by default.
            doorstatement.setInt(DOOR_POWER_Z - 1,         door.getEngine().getBlockZ());
            doorstatement.setInt(DOOR_OPEN_DIR - 1,        RotateDirection.getValue(door.getOpenDir()));
            doorstatement.setInt(DOOR_AUTO_CLOSE - 1,      door.getAutoClose());
            doorstatement.setLong(DOOR_CHUNK_HASH - 1,     door.getPowerBlockChunkHash());
            doorstatement.setLong(DOOR_BLOCKS_TO_MOVE - 1, door.getBlocksToMove());

            doorstatement.executeUpdate();
            doorstatement.close();

            String query          = "SELECT last_insert_rowid() AS lastId";
            PreparedStatement ps2 = conn.prepareStatement(query);
            ResultSet rs2         = ps2.executeQuery();
            Long doorID           = rs2.getLong("lastId");
            ps2.close();
            rs2.close();

            Statement stmt3 = conn.createStatement();
            String sql3     = "INSERT INTO sqlUnion (permission, playerID, doorUID) "
                            + "VALUES ('" + door.getPermission() + "', '" + playerID + "', '" + doorID + "');";
            stmt3.executeUpdate(sql3);
            stmt3.close();
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("813: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("823: " + e.getMessage());
            }
        }
    }

    // Insert a new door in the db.
    public void removeOwner(long doorUID, UUID playerUUID)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();

            long playerID = -1;
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM players WHERE playerUUID = '" + playerUUID.toString() + "';");
            ResultSet rs1         = ps1.executeQuery();

            while (rs1.next())
                playerID = rs1.getLong(PLAYERS_ID);
            ps1.close();
            rs1.close();

            if (playerID == -1)
            {
                plugin.getMyLogger().logMessage("Trying to remove player " + playerUUID.toString() +
                                                " as ownwer of door " + doorUID + ". But player does not exist!" , true, false);
                return;
            }

            PreparedStatement ps2 = conn.prepareStatement("DELETE FROM sqlUnion WHERE " +
                                                            "playerUUID = '" + playerUUID.toString() +
                                                            "' AND doorUID = '" + doorUID +
                                                            "' AND permission > '" + 0 + "';"); // The creator cannot be removed as owner
            ps2.execute();
            ps2.close();

        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("813: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("823: " + e.getMessage());
            }
        }
    }

    // Insert a new door in the db.
    public void addOwner(long doorUID, UUID playerUUID, int permission)
    {
        Connection conn = null;
        try
        {
            conn = getConnection();

            long playerID = -1;
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM players WHERE playerUUID = '" + playerUUID.toString() + "';");
            ResultSet rs1         = ps1.executeQuery();

            while (rs1.next())
                playerID = rs1.getLong(PLAYERS_ID);
            ps1.close();
            rs1.close();

            if (playerID == -1)
            {
                Statement stmt2 = conn.createStatement();
                String sql2     = "INSERT INTO players (playerUUID) "
                                + "VALUES ('" + playerUUID.toString() + "');";
                stmt2.executeUpdate(sql2);
                stmt2.close();

                String query          = "SELECT last_insert_rowid() AS lastId";
                PreparedStatement ps2 = conn.prepareStatement(query);
                ResultSet rs2         = ps2.executeQuery();
                playerID              = rs2.getLong("lastId");
                ps2.close();
                rs2.close();
            }



            Statement stmt3 = conn.createStatement();
            String sql3     = "INSERT INTO sqlUnion (permission, playerID, doorUID) "
                            + "VALUES ('" + permission + "', '" + playerID + "', '" + doorUID + "');";
            stmt3.executeUpdate(sql3);
            stmt3.close();
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("813: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("823: " + e.getMessage());
            }
        }
    }

    // Get the number of doors owned by this player.
    // If name is null, it will ignore door names, otherwise it will return the number of doors with the provided name.
    public long countDoors(String playerUUID, String name)
    {
        long count = 0;
        Connection conn = null;
        try
        {
            conn = getConnection();
            // Get the player ID as used in the sqlUnion table.
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM players WHERE playerUUID = '" + playerUUID + "';");
            ResultSet rs1         = ps1.executeQuery();
            while (rs1.next())
            {
                int playerID = rs1.getInt(PLAYERS_ID);
                // Select all doors from the sqlUnion table that have the previously found player as owner.
                PreparedStatement ps2 = conn.prepareStatement("SELECT * FROM sqlUnion WHERE playerID = '" + playerID + "';");
                ResultSet rs2         = ps2.executeQuery();
                while (rs2.next())
                {
                    // Retrieve the door with the provided ID.
                    PreparedStatement ps3 = conn.prepareStatement("SELECT * FROM doors WHERE id = '" + rs2.getInt(UNION_DOOR_ID) + "';");
                    ResultSet rs3         = ps3.executeQuery();
                    // Check if this door matches the provided name, if a name was provided.
                    while (rs3.next())
                        if (name == null || name != null && rs3.getString(DOOR_NAME).equals(name))
                            ++count;
                    ps3.close();
                    rs3.close();
                }
                ps2.close();
                rs2.close();
            }
            ps1.close();
            rs1.close();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("866: " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("876: " + e.getMessage());
            }
        }
        return count;
    }

    // Add columns and such when needed (e.g. upgrades from older versions).
    private void upgrade()
    {
        Connection conn = null;
        try
        {
            conn = DriverManager.getConnection(url);
            Statement stmt = conn.createStatement();
            ResultSet rs   = stmt.executeQuery("PRAGMA user_version;");
            int dbVersion  = rs.getInt(1);
            if (dbVersion == 0)
                upgradeToV1(conn);

            if (dbVersion == 1)
                upgradeToV2(conn);

            if (dbVersion != DATABASE_VERSION)
                setDBVersion(conn, DATABASE_VERSION);

            stmt.close();
            rs.close();
        }
        catch(SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("1030 " + e.getMessage());
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch(SQLException e)
            {
                plugin.getMyLogger().logMessageToLogFile("1040 " + e.getMessage());
            }
        }
    }










    private void setDBVersion(Connection conn, int version)
    {
        try
        {
            conn.createStatement().execute("PRAGMA user_version = " + version + ";");
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("972 " + e.getMessage());
        }
    }

    private void upgradeToV1(Connection conn)
    {
        try
        {
            String addColumn;

            DatabaseMetaData md = conn.getMetaData();
            ResultSet rs = md.getColumns(null, null, "doors", "type");
            Statement stmt = conn.createStatement();

            if (!rs.next())
            {
                plugin.getMyLogger().logMessage("Upgrading database! Adding type!", true, true);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN type int NOT NULL DEFAULT 0";
                stmt.execute(addColumn);
            }

            rs = md.getColumns(null, null, "doors", "engineSide");
            if (!rs.next())
            {
                plugin.getMyLogger().logMessage("Upgrading database! Adding engineSide!", true, true);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN engineSide int NOT NULL DEFAULT -1";
                stmt.execute(addColumn);
            }

            rs = md.getColumns(null, null, "doors", "powerBlockX");
            if (!rs.next())
            {
                plugin.getMyLogger().logMessage("Upgrading database! Adding powerBlockLoc!", true, true);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN powerBlockX int NOT NULL DEFAULT -1";
                stmt.execute(addColumn);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN powerBlockY int NOT NULL DEFAULT -1";
                stmt.execute(addColumn);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN powerBlockZ int NOT NULL DEFAULT -1";
                stmt.execute(addColumn);

                PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM doors;");
                ResultSet rs1 = ps1.executeQuery();
                String update;

                while (rs1.next())
                {
                    long UID = rs1.getLong(DOOR_ID);
                    int x    = rs1.getInt(DOOR_ENG_X);
                    int y    = rs1.getInt(DOOR_ENG_Y) - 1;
                    int z    = rs1.getInt(DOOR_ENG_Z);
                    update   = "UPDATE doors SET "
                             +   "powerBlockX='" + x
                             + "',powerBlockY='" + y
                             + "',powerBlockZ='" + z
                             + "' WHERE id = '"  + UID + "';";
                    conn.prepareStatement(update).executeUpdate();
                }
                ps1.close();
                rs1.close();
            }

            rs = md.getColumns(null, null, "doors", "openDirection");
            if (!rs.next())
            {
                plugin.getMyLogger().logMessage("Upgrading database! Adding openDirection!", true, true);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN openDirection int NOT NULL DEFAULT 0";
                stmt.execute(addColumn);


                plugin.getMyLogger().logMessage("Upgrading database! Swapping open-status of drawbridges to conform to the new standard!", true, true);
                String update = "UPDATE doors SET "
                              +   "isOpen='" + 2
                              + "' WHERE isOpen = '" + 0 + "' AND type = '" + DoorType.getValue(DoorType.DRAWBRIDGE) + "';";
                stmt.execute(update);
                update = "UPDATE doors SET "
                       +   "isOpen='" + 0
                       + "' WHERE isOpen = '" + 1 + "' AND type = '" + DoorType.getValue(DoorType.DRAWBRIDGE) + "';";
                stmt.execute(update);
                update = "UPDATE doors SET "
                       +   "isOpen='" + 1
                       + "' WHERE isOpen = '" + 2 + "' AND type = '" + DoorType.getValue(DoorType.DRAWBRIDGE) + "';";
                stmt.execute(update);
            }

            rs = md.getColumns(null, null, "doors", "autoClose");
            if (!rs.next())
            {
                plugin.getMyLogger().logMessage("Upgrading database! Adding autoClose!", true, true);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN autoClose int NOT NULL DEFAULT -1";
                stmt.execute(addColumn);
            }

            rs = md.getColumns(null, null, "doors", "chunkHash");
            if (!rs.next())
            {
                plugin.getMyLogger().logMessage("Upgrading database! Adding chunkHash!", true, true);
                addColumn = "ALTER TABLE doors "
                          + "ADD COLUMN chunkHash int NOT NULL DEFAULT -1";
                stmt.execute(addColumn);

                PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM doors;");
                ResultSet rs1 = ps1.executeQuery();
                String update;

                while (rs1.next())
                {
                    long UID = rs1.getLong(DOOR_ID);
                    UUID worldUUID = UUID.fromString(rs1.getString(DOOR_WORLD));
                    int x    = rs1.getInt(DOOR_POWER_X);
                    int z    = rs1.getInt(DOOR_POWER_Z);

                    update   = "UPDATE doors SET "
                             +   "chunkHash='" + Util.chunkHashFromLocation(x, z, worldUUID)
                             + "' WHERE id = '"  + UID + "';";
                    conn.prepareStatement(update).executeUpdate();
                }
                ps1.close();
                rs1.close();
            }
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("1101 " + e.getMessage());
        }
    }

    private void upgradeToV2(Connection conn)
    {
        try
        {
            String addColumn;
            Statement stmt = conn.createStatement();
            plugin.getMyLogger().logMessage("Upgrading database! Adding blocksToMove!", true, true);
            addColumn = "ALTER TABLE doors "
                      + "ADD COLUMN blocksToMove int NOT NULL DEFAULT 0";
            stmt.execute(addColumn);
        }
        catch (SQLException e)
        {
            plugin.getMyLogger().logMessageToLogFile("1118 " + e.getMessage());
        }
    }

}
