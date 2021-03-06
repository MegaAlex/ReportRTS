package com.nyancraft.reportrts.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;

import org.bukkit.Location;

public interface Database {

    public boolean connect();

    public void disconnect();

    public boolean isLoaded();

    public void setLoaded();

    public void populateRequestMap();

    public void deleteRequestsByTime(String table, int lessThanThis);

    public void deleteEntryById(String table, int id);

    public int updateTicket(int ticketId);

    public int getNumberHeldRequests();

    public int getUserId(String player);
    public int getUserId(String player, UUID uuid, boolean createIfNotExists);

    public UUID getUserUUID(int userId);

    public int countRequests(int status);

    public int getLatestTicketIdByUser(int userId);

    public String getUserName(int userId);

    public boolean fileRequest(String player, String world, Location location, String message, int userId);

    public boolean insertRequest(int modId, String world, int x, int y, int z, String message, int userId, int tstamp);

    public boolean insertUser(int userId, String name, int banned);

    public ResultSet getHeldRequests(int from, int limit);

    public ResultSet getClosedRequests(int from, int limit);

    public ResultSet getHandledBy(String player);

    public ResultSet getLimitedHandledBy(String player, int from, int limit);

    public ResultSet getLimitedCreatedBy(String player, int from, int limit);

    public ResultSet getTicketById(int id);

    public ResultSet getHeldTicketById(int id);

    public ResultSet getLocationById(int id);

    public ResultSet getUnnotifiedUsers();

    public ResultSet getAllFromTable(String table);

    public ResultSet getStats();

    public boolean setRequestStatus(int id, String user, int status, String comment, int notified, long timestamp, boolean createUser);

    public boolean setNotificationStatus(int id, int status);

    public boolean setUserStatus(String player, UUID uuid, int status);
    public boolean setUserStatus(String player, int status);

    public boolean userExists(String player);

    public boolean resetDB();

    public boolean checkTable(String table);

    public Connection connection();

    public ResultSet query(String query);

    public void refresh();

}