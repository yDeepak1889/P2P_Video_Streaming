package com.example.deepak.prototype;

import android.util.Pair;

import com.google.gson.Gson;

import java.util.ArrayList;

public class ListToSend {
    private ArrayList<Pair<String, Long>> ipsPartList;

    public ListToSend() {
        this.ipsPartList = new ArrayList<>();
    }

    public void insertEntry(String ip, Long duration) {
        ipsPartList.add(new Pair<>(ip, duration));
    }

    public ArrayList<Pair<String, Long>> getIpsPartList() {
        return ipsPartList;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static ListToSend fromJson(String jsoncan){
        Gson gson = new Gson();
        return gson.fromJson(jsoncan, ListToSend.class);
    }
}
