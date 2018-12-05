package com.example.deepak.prototype;


import com.google.gson.Gson;

public class Peer {
    private String id;
    private Float battery;
    private String IpAddr;

    public Peer(String id, Float battery, String IpAddr) {
        this.id = id;
        this.battery = battery;
        this.IpAddr = IpAddr;
    }

    public String getId() {
        return id;
    }

    public Float getBattery() {
        return battery;
    }

    public String getIpAddr() {
        return IpAddr;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    static public Peer fromJson(String jsoncan){
        Gson gson = new Gson();
        return gson.fromJson(jsoncan,Peer.class);
    }
}
