package com.nyancraft.reportrts.command.sub;

import com.nyancraft.reportrts.RTSFunctions;
import com.nyancraft.reportrts.RTSPermissions;
import com.nyancraft.reportrts.ReportRTS;
import com.nyancraft.reportrts.data.NotificationType;
import com.nyancraft.reportrts.event.TicketAssignEvent;
import com.nyancraft.reportrts.persistence.DatabaseManager;
import com.nyancraft.reportrts.util.BungeeCord;
import com.nyancraft.reportrts.util.Message;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.UUID;

public class AssignTicket {

    private static ReportRTS plugin = ReportRTS.getPlugin();

    /**
     * Initial handling of the AssignTicket sub-command.
     * @param sender player that sent the command
     * @param args arguments
     * @return true if command handled correctly
     */
    public static boolean handleCommand(CommandSender sender, String[] args) {

        if(args.length < 3) return false;

        if(!RTSPermissions.canAssignRequests(sender)) return true;
        if(!RTSFunctions.isNumber(args[1])) {
            sender.sendMessage(Message.parse("generalInternalError", "Ticket ID must be a number, provided: " + args[1]));
            return true;
        }
        int ticketId = Integer.parseInt(args[1]);

        // The ticket the user is trying to claim is not open.
        if(!plugin.requestMap.containsKey(ticketId)){
            sender.sendMessage(Message.parse("claimNotOpen"));
            return true;
        }

        String assignee = args[2];
        if(assignee == null){
            sender.sendMessage(Message.parse("generalInternalError", "Your name or assignee is null! Try again."));
            return true;
        }

        int userId = DatabaseManager.getDatabase().getUserId(assignee);
        if(userId == 0){
            sender.sendMessage(Message.parse("generalInternalError", "That user does not exist!"));
            return true;
        }

        UUID assigneeUUID = DatabaseManager.getDatabase().getUserUUID(userId);
        if(assigneeUUID == null) {
            sender.sendMessage(Message.parse("generalInternalError", "User UUID might not exist for user ID " + userId));
            return true;
        }

        long timestamp = System.currentTimeMillis() / 1000;

        if(!DatabaseManager.getDatabase().setRequestStatus(ticketId, assignee, 1, "", 0, timestamp, true)){
            sender.sendMessage(Message.parse("generalInternalError", "Unable to assign request #" + ticketId + " to " + assignee));
            return true;
        }

        Player player = sender.getServer().getPlayer(plugin.requestMap.get(ticketId).getUUID());
        if(player != null) {
            player.sendMessage(Message.parse("assignUser", assignee));
            player.sendMessage(Message.parse("assignText", plugin.requestMap.get(ticketId).getMessage()));
        }
        plugin.requestMap.get(ticketId).setStatus(1);
        plugin.requestMap.get(ticketId).setModUUID(assigneeUUID);
        plugin.requestMap.get(ticketId).setModTimestamp(timestamp);
        plugin.requestMap.get(ticketId).setModName(assignee);

        try{
            BungeeCord.globalNotify(Message.parse("assignRequest", assignee, ticketId), ticketId, NotificationType.MODIFICATION);
        }catch(IOException e){
            e.printStackTrace();
        }
        RTSFunctions.messageMods(Message.parse("assignRequest", assignee, ticketId), false);
        // Let other plugins know the request was assigned.
        plugin.getServer().getPluginManager().callEvent(new TicketAssignEvent(plugin.requestMap.get(ticketId), sender));

        return true;
    }
}
