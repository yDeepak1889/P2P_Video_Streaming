package com.example.deepak.prototype;

import com.google.gson.Gson;

import java.io.File;
import java.util.HashMap;

public class ServerFileManager {
    private HashMap<String, ServerFileInfo> serverFileInfoHashMap;

    public ServerFileManager() {
        this.serverFileInfoHashMap = new HashMap<>();
    }

    public void reset() {
        serverFileInfoHashMap.clear();
    }

    public ServerFileInfo getFilesInfo(String key) {
        return serverFileInfoHashMap.containsKey(key) ? serverFileInfoHashMap.get(key) : null;
    }

    public void insertEntry(String md5Sum, String ip, Long duration, File file) {
        if (!serverFileInfoHashMap.containsKey(md5Sum))
            serverFileInfoHashMap.put(md5Sum, new ServerFileInfo(md5Sum, duration));
        serverFileInfoHashMap.get(md5Sum).insertIntry(ip, file);
    }

    public ServerFileInfo getServerFile(String key) {
        return serverFileInfoHashMap.containsKey(key) ? serverFileInfoHashMap.get(key) : null;
    }

    public HashMap<String, ServerFileInfo> getServerFileInfoHashMap() {
        return serverFileInfoHashMap;
    }

    public String toJson(){
        Gson gson = new Gson();
        String json = gson.toJson(this);
        return json;
    }

    public static ServerFileManager fromJson(String jsoncan){
        Gson gson = new Gson();
        return gson.fromJson(jsoncan,ServerFileManager.class);
    }
}
