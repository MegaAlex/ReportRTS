package com.nyancraft.reportrts.api;

import com.nyancraft.reportrts.ReportRTS;
import com.nyancraft.reportrts.data.Ticket;

import java.util.Map;

public class Response {

    public static String getRequests(){

        StringBuilder resp = new StringBuilder();
        resp.append("{");
        resp.append("\"openRequests\":");
        resp.append("{");
        int i = 1;
        for(Map.Entry<Integer, Ticket> entry: ReportRTS.getPlugin().requestMap.entrySet()){

            resp.append("\"" + entry.getKey().toString() + "\":[");
            resp.append("{\"status\":").append(entry.getValue().getStatus()).append(",");
            resp.append("\"x\":").append(entry.getValue().getX()).append(",");
            resp.append("\"y\":").append(entry.getValue().getY()).append(",");
            resp.append("\"z\":").append(entry.getValue().getZ()).append(",");
            resp.append("\"yaw\":").append(entry.getValue().getYaw()).append(",");
            resp.append("\"pitch\":").append(entry.getValue().getPitch()).append(",");
            resp.append("\"timestamp\":").append(entry.getValue().getTimestamp()).append(",");
            resp.append("\"modtimestamp\":").append(entry.getValue().getModTimestamp()).append(",");
            resp.append("\"message\":").append("\"").append(entry.getValue().getMessage()).append("\"").append(",");
            resp.append("\"uuid\":").append("\"").append(entry.getValue().getUUID()).append("\"").append(",");
            resp.append("\"moduuid\":").append("\"").append(entry.getValue().getModUUID()).append("\"").append(",");
            resp.append("\"comment\":").append("\"").append(entry.getValue().getModComment()).append("\"").append(",");
            resp.append("\"server\":").append("\"").append(entry.getValue().getBungeeCordServer()).append("\"").append("}");
            resp.append("]");

            if(ReportRTS.getPlugin().requestMap.size() > i ) resp.append(",");
            i++;
        }
        resp.append("}");
        resp.append("}\n");

        return resp.toString();
    }

    public static String getRequest(int n){
       if(!ReportRTS.getPlugin().requestMap.containsKey(n)) return "{\"success\":\"false\",\"message\":\"Request not found\"}";
        Ticket request = ReportRTS.getPlugin().requestMap.get(n);
       return "{\"success\":\"true\",\"data\":[{\"status\":\"" + request.getStatus() + "\"," +
               "\"x\":\"" + request.getX() + "\"," + "\"y\":\"" + request.getY() + "\"," +
               "\"z\":\"" + request.getZ() + "\"," + "\"yaw\":\"" + request.getYaw() + "\"," +
               "\"pitch\":\"" + request.getPitch() + "\"," + "\"timestamp\":\"" + request.getTimestamp() + "\"," +
               "\"modtimestamp\":\"" + request.getModTimestamp() + "\"," + "\"message\":\"" + request.getMessage() + "\"," +
               "\"uuid\":\"" + request.getUUID() + "\"," + "\"moduuid\":\"" + request.getModUUID() + "\"," +
               "\"comment\":\"" + request.getModComment() + "\"," + "\"server\":\"" + request.getBungeeCordServer() + "\"," +
               "]}";
    }

    public static String uncheckedResult(){
        return "{\"success\":\"true\",\"message\":\"Action was performed, unable to check result\"}";
    }

    public static String noAction(){
        return "{\"success\":\"false\",\"message\":\"No action specified\"}";
    }

    public static String loginRequired(){
        return "{\"success\":\"false\",\"message\":\"Authentication missing or incorrect\"}";
    }

    public static String moreArgumentsExpected(String n){
        return "{\"success\":\"false\",\"message\":\"Not enough arguments, " + n + " required\"}";
    }

    public static String invalidArgument(){
        return "{\"success\":\"false\",\"message\":\"One of the provided arguments are invalid\"}";
    }
}
