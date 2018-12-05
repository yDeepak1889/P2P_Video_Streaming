package com.example.deepak.prototype;

import android.util.Pair;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;

public class ServerFileInfo {
    private String md5Sum;
    private Long duration;
    private ArrayList<Pair<String, File>> fileList;

    public ServerFileInfo(String md5Sum, Long duration) {
        this.duration = duration;
        this.fileList = new ArrayList<>();
        this.md5Sum = md5Sum;
    }

    public void insertIntry(String ip, File file) {
        for (Pair<String, File> ele : fileList) {
            if (ele.first.equals(ip))
                return ;
        }
        fileList.add(new Pair<>(ip, file));
    }

    public Long getDuration() {
        return duration;
    }

    public String getMd5Sum() {
        return md5Sum;
    }

    public ArrayList<Pair<String, File>> getFileList() {
        return fileList;
    }

    public String toJson(){
        Gson gson = new Gson();
        String json = gson.toJson(this);
        return json;
    }
}
