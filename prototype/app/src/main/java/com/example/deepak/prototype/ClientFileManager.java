package com.example.deepak.prototype;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;

public class ClientFileManager {
    private String ip;
    private HashMap<String, ClientFileInfo> filesMap;

    public ClientFileManager(String ip) {
        this.filesMap = new HashMap<>();
        this.ip = ip;
    }

    public void init(Context context) {
        setFilesMap(context);
    }

    private void setFilesMap (Context context) {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        Toast.makeText(context,path,Toast.LENGTH_LONG).show();
        System.out.println(path);
        File file = new File(path);

        if (file.isDirectory()) {
            //System.out.println("Inside");
            scanDirectory(file, context);
        } else {
            if(file.getName().endsWith(".mp4")){
                System.out.println(file.getName());
                String md5Sum = getmd5sum(context, file);
                MainActivity.getDuration(file, md5Sum);
            }
        }
    }

    public void insertEntry(String md5Sum, File file, Long duration) {
        if (!this.filesMap.containsKey(md5Sum)) {
            this.filesMap.put(md5Sum, new ClientFileInfo(file, duration));
        }
        System.out.println(md5Sum + " " + file.getName() + " " + duration);
    }

    public File getFile(String key) {
        return this.filesMap.containsKey(key) ? this.filesMap.get(key).getFile() : null;
    }

    private void scanDirectory(File directory, Context context) {
        if (directory != null) {
            File[] listFiles = directory.listFiles();
            if (listFiles != null && listFiles.length > 0) {
                for (File file : listFiles) {
                    if (file.isDirectory()) {
                        scanDirectory(file, context);
                    } else {
                        if(file.getName().endsWith(".mp4")){
                            System.out.println(file.getName());
                            String md5Sum = getmd5sum(context, file);
                            MainActivity.getDuration(file, md5Sum);
                        }
                    }

                }
            }
        }
    }

    private String getmd5sum(Context c, File f){
        String checksum = null;
        try {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            checksum = getFileChecksum(md5Digest, f);
        } catch(Exception e){
            e.printStackTrace();
        }
        return checksum;
    }

    private String getFileChecksum(MessageDigest digest, File file) throws IOException
    {
        //Get file input stream for reading the file content
        FileInputStream fis = new FileInputStream(file);

        //Create byte array to read data in chunks
        byte[] byteArray = new byte[MainActivity.buffSize];
        int bytesCount = 0;

        //Read file data and update in message digest
        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        };

        //close the stream; We don't need it now.
        fis.close();

        //Get the hash's bytes
        byte[] bytes = digest.digest();

        //This bytes[] has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();

        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }

        //return complete hash
        return sb.toString();
    }

    public String getIp() {
        return this.ip;
    }

    public HashMap<String, ClientFileInfo> getFilesMap() {
        return this.filesMap;
    }

    public String toJson(){
        Gson gson = new Gson();
        String json = gson.toJson(this);
        return json;
    }

    public ClientFileManager fromJson(String jsoncan){
        Gson gson = new Gson();
        ClientFileManager can = gson.fromJson(jsoncan,ClientFileManager.class);
        return can;
    }
}
