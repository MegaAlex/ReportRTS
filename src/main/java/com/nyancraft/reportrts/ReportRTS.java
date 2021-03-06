package com.nyancraft.reportrts;

import java.io.FileReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

import com.nyancraft.reportrts.api.ApiServer;
import com.nyancraft.reportrts.persistence.DatabaseManager;
import com.nyancraft.reportrts.command.*;
import com.nyancraft.reportrts.data.Ticket;
import com.nyancraft.reportrts.util.*;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class ReportRTS extends JavaPlugin implements PluginMessageListener {

    private static ReportRTS plugin;
    private final Logger log = Logger.getLogger("Minecraft");
    private static MessageHandler messageHandler = new MessageHandler();
    private VersionChecker versionChecker = new VersionChecker();

    public Map<Integer, Ticket> requestMap = new LinkedHashMap<>();
    public Map<Integer, UUID> notificationMap = new HashMap<>();
    public Map<UUID, Integer> teleportMap = new HashMap<>();
    public Map<String, String> commandMap = new HashMap<>();
    public ArrayList<UUID> moderatorMap = new ArrayList<>();

    public boolean notifyStaffOnNewRequest;
    public boolean notificationSound;
    public boolean hideNotification;
    public boolean hideWhenOffline;
    public boolean debugMode;
    public boolean outdated;
    public boolean vanishSupport;
    public boolean bungeeCordSupport;
    public boolean setupDone = true;
    public boolean requestNagHeld;
    public boolean requestPreventDuplicate;
    public boolean apiEnabled;
    public boolean legacyCommands;
    public boolean fancify;

    public int maxRequests;
    public int requestDelay;
    public int requestMinimumWords;
    public int requestsPerPage;
    public int storagePort;
    public int consoleID;
    public long requestNagging;
    public long storageRefreshTime;
    public long bungeeCordSync;
    public String storageType;
    public String storageHostname;
    public String storageDatabase;
    public String storageUsername;
    public String storagePassword;
    public String storagePrefix;
    public String versionString;
    public String bungeeCordServerPrefix;
    public String lineSeparator = System.lineSeparator();

    public UUID consoleUUID;

    public static Permission permission = null;

    private ApiServer apiServer;
    private int apiPort;
    private String apiPassword;
    private List<String> apiAllowedIPs = new ArrayList<>();

    private String serverIP;

    public void onDisable() {
        DatabaseManager.getDatabase().disconnect();
        if(apiEnabled) {
            try{
                apiServer.getListener().close();
            }catch(IOException e) {
                e.printStackTrace();
            }
        }
        messageHandler.saveMessageConfig();
    }

    public void onEnable() {
        plugin = this;
        reloadSettings();

        // Enable BungeeCord support if wanted.
        if(bungeeCordSupport) {
            if(getConfig().getString("bungeecord.serverName") == null || getConfig().getString("bungeecord.serverName").isEmpty()) {
                plugin.getLogger().warning("BungeeCord support enabled, but server name is not set yet. Scheduling a name-update task.");
                new BungeeNameTask(plugin).runTaskTimer(plugin, 160L, 480L);
            } else {
                BungeeCord.setServer(getConfig().getString("bungeecord.serverName"));
            }
        }
        final PluginManager pm = getServer().getPluginManager();

        // Register events that ReportRTS listens to.
        pm.registerEvents(new RTSListener(plugin), plugin);

        // Ensure that storage information is not default as that may not work.
        if(assertConfigIsDefault("STORAGE")) {
            setupDone = false;
        } else {
            if(!DatabaseManager.load()) {
                log.severe("Encountered an error while attempting to connect to the database.  Disabling...");
                pm.disablePlugin(this);
            }
            reloadPlugin();
        }

        // Check if plugin is up to date. TODO: This has to be updated for Spigot's website.
        outdated = !versionChecker.upToDate();

        // Enable fancier tickets if enabled and if ProtocolLib is enabled on the server.
        if(fancify && pm.getPlugin("ProtocolLib") == null) {
            log.warning("Fancy messages are enabled, but ProtocolLib was not found.");
            fancify = false;
        }

        // Store console information for performance reasons.
        consoleID = DatabaseManager.getDatabase().getUserId("CONSOLE");
        consoleUUID = DatabaseManager.getDatabase().getUserUUID(consoleID);

        // Register commands.
        if(legacyCommands) {
            pm.registerEvents(new LegacyCommandListener(commandMap.get("readTicket"), commandMap.get("openTicket"), commandMap.get("closeTicket"), commandMap.get("reopenTicket"),
                    commandMap.get("claimTicket"), commandMap.get("unclaimTicket"), commandMap.get("holdTicket"), commandMap.get("teleportToTicket"), commandMap.get("broadcastToStaff"),
                    commandMap.get("listStaff")), plugin);
        }

        getCommand("reportrts").setExecutor(new ReportRTSCommand(plugin));
        getCommand("ticket").setExecutor(new TicketCommand(plugin));
        getCommand("ticket").setTabCompleter(new TabCompleteHelper(plugin));

        // Set up Vault if it exists on the server.
        if(pm.getPlugin("Vault") != null) setupPermissions();

        // Attempt to set up Metrics.
        try {
            MetricsLite metrics = new MetricsLite(this);
            metrics.start();
        } catch(IOException e) {
            log.info("Unable to submit stats!");
        }

        // Enable API. (Not recommended since it is very incomplete!)
        if(apiEnabled) {
            try {
                Properties props = new Properties();
                props.load(new FileReader("server.properties"));
                serverIP = props.getProperty("server-ip", "ANY");
                if(serverIP.isEmpty()) serverIP = "ANY";
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    apiPassword = apiPassword + "ReportRTS";
                    md.update(apiPassword.getBytes("UTF-8"));
                    byte[] hash = md.digest();
                    StringBuffer sb = new StringBuffer();
                    for(byte b : hash) {
                        sb.append(String.format("%02x", b));
                    }
                    apiPassword = sb.toString();
                } catch(NoSuchAlgorithmException e) {
                    log.warning("[ReportRTS] Unable to hash password, consider disabling the API!");
                    e.printStackTrace();
                }
                apiServer = new ApiServer(plugin, serverIP, apiPort, apiAllowedIPs, apiPassword);
            } catch(IOException e) {
                log.warning("[ReportRTS] Unable to start API server!");
                e.printStackTrace();
            }
            apiServer.start();
        }

        // Enable nagging, staff will be reminded of unresolved tickets.
        if(requestNagging > 0){
            getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable(){
                public void run(){
                    int openRequests = requestMap.size();
                    if(requestNagHeld) {
                        int heldRequests = DatabaseManager.getDatabase().getNumberHeldRequests();
                        if(heldRequests > 0) {
                            if(openRequests > 0) RTSFunctions.messageMods(Message.parse("generalOpenHeldRequests", openRequests, heldRequests, (plugin.legacyCommands ? plugin.commandMap.get("readTicket") : "ticket " + plugin.commandMap.get("readTicket"))), false);
                        } else {
                            if(openRequests > 0) RTSFunctions.messageMods(Message.parse("generalOpenRequests", openRequests, (plugin.legacyCommands ? plugin.commandMap.get("readTicket") : "ticket " + plugin.commandMap.get("readTicket"))), false);
                        }
                    } else {
                        if(openRequests > 0) RTSFunctions.messageMods(Message.parse("generalOpenRequests", openRequests, (plugin.legacyCommands ? plugin.commandMap.get("readTicket") : "ticket " + plugin.commandMap.get("readTicket"))), false);
                    }
                }
            }, 120L, (requestNagging * 60) * 20);
        }

        // Enable a refresh timer if it is needed to prevent interruption in the data-provider.
        if(plugin.storageRefreshTime > 0) {
            getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                public void run() {
                    DatabaseManager.getDatabase().refresh();
                }
            }, 4000L, plugin.storageRefreshTime * 20);
        }

        if(bungeeCordSupport) {
            // Register BungeeCord channels.
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);

            // Schedule a offline-sync in case no players are online.
            getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                public void run() {
                    if(BungeeCord.isServerEmpty()) {
                        RTSFunctions.sync();
                    }
                }
            }, plugin.bungeeCordSync * 20, plugin.bungeeCordSync * 20);
        }
    }

    public void reloadPlugin() {
        reloadSettings();
        RTSFunctions.sync();
    }

    public void reloadSettings() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        assertConfigUpToDate();
        messageHandler.reloadMessageConfig();
        messageHandler.saveMessageConfig();
        messageHandler.reloadMessageMap();
        notifyStaffOnNewRequest = getConfig().getBoolean("notifyStaff");
        notificationSound = getConfig().getBoolean("notifySound");
        hideNotification = getConfig().getBoolean("hideMessageIfEmpty");
        hideWhenOffline = getConfig().getBoolean("ticket.hideOffline");
        maxRequests = getConfig().getInt("ticket.max");
        requestDelay = getConfig().getInt("ticket.delay");
        requestMinimumWords = getConfig().getInt("ticket.minimumWords");
        requestsPerPage = getConfig().getInt("ticket.perPage");
        requestPreventDuplicate = getConfig().getBoolean("ticket.preventDuplicates", true);
        requestNagging = getConfig().getLong("ticket.nag");
        requestNagHeld = getConfig().getBoolean("ticket.nagHeld", false);
        storageRefreshTime = getConfig().getLong("storage.refreshTime");
        storageType = getConfig().getString("storage.type", "mysql");
        storagePort = getConfig().getInt("storage.port");
        storageHostname = getConfig().getString("storage.hostname");
        storageDatabase = getConfig().getString("storage.database");
        storageUsername = getConfig().getString("storage.username");
        storagePassword = getConfig().getString("storage.password");
        storagePrefix = getConfig().getString("storage.prefix");
        debugMode = getConfig().getBoolean("debug");
        vanishSupport = getConfig().getBoolean("VanishSupport", false);
        bungeeCordSupport = getConfig().getBoolean("bungeecord.enable", false);
        bungeeCordSync = getConfig().getLong("bungeecord.sync", 300L);
        bungeeCordServerPrefix = getConfig().getString("bungeecord.serverPrefix");
        apiEnabled =  false; // TODO: Change to this when it's ready: getConfig().getBoolean("api.enable", false);
        apiPort = getConfig().getInt("api.port", 25567);
        apiPassword = getConfig().getString("api.password");
        apiAllowedIPs = getConfig().getStringList("api.whitelist");
        legacyCommands = getConfig().getBoolean("command.legacy", false);
        fancify = getConfig().getBoolean("ticket.fancify", true);
        commandMap.clear();
        // Register all commands/subcommands.
        commandMap.put("readTicket",getConfig().getString("command.readTicket"));
        commandMap.put("openTicket",getConfig().getString("command.openTicket"));
        commandMap.put("closeTicket",getConfig().getString("command.closeTicket"));
        commandMap.put("reopenTicket",getConfig().getString("command.reopenTicket"));
        commandMap.put("claimTicket",getConfig().getString("command.claimTicket"));
        commandMap.put("unclaimTicket",getConfig().getString("command.unclaimTicket"));
        commandMap.put("holdTicket",getConfig().getString("command.holdTicket"));
        commandMap.put("teleportToTicket",getConfig().getString("command.teleportToTicket"));
        commandMap.put("broadcastToStaff",getConfig().getString("command.broadcastToStaff"));
        commandMap.put("listStaff",getConfig().getString("command.listStaff"));
        commandMap.put("assignTicket",getConfig().getString("command.assignTicket"));
        // Commands registered!
    }

    public static ReportRTS getPlugin() {
        return plugin;
    }

    public static MessageHandler getMessageHandler() {
        return messageHandler;
    }

    private Boolean setupPermissions() {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if(permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
        return (permission != null);
    }

    public void onPluginMessageReceived(String pluginChannel, Player player, byte[] bytes) {
        if(!pluginChannel.equals("BungeeCord")) return;

        BungeeCord.handleNotify(bytes);
    }

    private void assertConfigUpToDate() {
        /**
         * What it does:
         * - - - - -
         * Checks if the mapping "requests" is located in the config
         * and replaces it with "ticket".
         * - - - - -
         * Since version:
         * 1.2.3
         */
        if(getConfig().getConfigurationSection("request") != null) {
            getConfig().createSection("ticket", getConfig().getConfigurationSection("request").getValues(false));
            getConfig().set("request", null);
            log.info("Updated configuration. 'request' => 'ticket'.");
        }

        // Save changes.
        saveConfig();
    }

    private boolean assertConfigIsDefault(String path) {
        /**
         * What it does:
         * - - - - -
         * Checks if the specified configuration section is default,
         * returns a boolean depending on the result.
         */

        switch(path.toUpperCase()) {

            case "STORAGE":

                return (storageHostname.equalsIgnoreCase("localhost") && storagePort == 3306 && storageDatabase.equalsIgnoreCase("minecraft")
                        && storageUsername.equalsIgnoreCase("username") && storagePassword.equalsIgnoreCase("password")
                        && storagePrefix.equalsIgnoreCase("") && storageRefreshTime == 600);
        }
        return false;
    }
}