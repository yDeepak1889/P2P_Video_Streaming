package com.example.deepak.prototype;

import com.google.gson.Gson;

public class Chunk {
    private Long totalSize;
    private Long offset;
    private Long reqSize;
    private String md5Sum;


    public Chunk(Long totalSize, Long offset, Long reqSize, String md5Sum) {
        this.totalSize = totalSize;
        this.offset = offset;
        this.reqSize = reqSize;
        this.md5Sum = md5Sum;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public Long getOffset() {
        return offset;
    }

    public Long getReqSize() {
        return reqSize;
    }

    public String getMd5Sum() {
        return md5Sum;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static Chunk fromJson(String jsoncan){
        Gson gson = new Gson();
        return gson.fromJson(jsoncan, Chunk.class);
    }
}
