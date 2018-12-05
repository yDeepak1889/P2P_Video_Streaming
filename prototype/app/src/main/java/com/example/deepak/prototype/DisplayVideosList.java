package com.example.deepak.prototype;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DisplayVideosList extends AppCompatActivity {
    private String smartHeadIp;
    private ListView listView;
    private ArrayList<String> filesName;
    private HashMap<String, String> fileNameToMd5Sum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_videos_list);

        getSupportActionBar().setTitle("Files");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        listView = findViewById(R.id.lv);
        filesName = new ArrayList<>();
        fileNameToMd5Sum = new HashMap<>();

        String filesInfo = getIntent().getStringExtra("filesInfo");
        smartHeadIp = getIntent().getStringExtra("headIP");

        String ipAddr = getIntent().getStringExtra("ipAddr");

        ServerFileManager serverFileManager = ServerFileManager.fromJson(filesInfo);

        for (Map.Entry<String, ServerFileInfo> entry : serverFileManager.getServerFileInfoHashMap().entrySet()) {
            if (!check(entry.getValue().getFileList(), ipAddr)) {
                filesName.add(entry.getValue().getFileList().get(0).second.getName());
                fileNameToMd5Sum.put(entry.getValue().getFileList().get(0).second.getName(), entry.getKey());
            }
        }

        System.out.println(filesInfo);
        System.out.println(smartHeadIp);

        listView.setAdapter(new ArrayAdapter<>(this, R.layout.listview_item, filesName));
        listView.setTextFilterEnabled(true);

        setOnClickListener(serverFileManager);
    }

    private boolean check(ArrayList<Pair<String, File>> files, String ip) {
        for (Pair<String, File> file : files)
            if (file.first.equals(ip)) {
                return true;
            }

        return false;
    }

    private void setOnClickListener(final ServerFileManager serverFileManager) {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Inside Thread Click");

                        Socket socket = null, socket1 = null;
                        BufferedReader in = null;
                        PrintWriter out = null;

                        //Create socket connection
                        try {
                            socket = new Socket(smartHeadIp, 4321);
                            socket.setSoTimeout(MainActivity.sockTimeOut);
                            out = new PrintWriter(socket.getOutputStream(),
                                    true);
                            in = new BufferedReader(new InputStreamReader(
                                    socket.getInputStream()));
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        try {
                            String md5Sum = fileNameToMd5Sum.get(filesName.get(position));

                            if (out != null) {
                                out.write("d" + md5Sum + "\n");
                                out.flush();
                            }

                            String ipsInfo = "";
                            if (in != null) {
                                ipsInfo = in.readLine();
                            }

                            System.out.println("Ips Info " + ipsInfo);

                            ListToSend listToSend = ListToSend.fromJson(ipsInfo);

                            downloadParts(listToSend, md5Sum, serverFileManager.getFilesInfo(md5Sum).getDuration());

                        }catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });
    }

    private void downloadParts(final ListToSend listToSend, final String md5Sum, final Long totalSize) throws InterruptedException {
        long offset = 0;
        long cnt = 0;
        Thread[] threads = new Thread[listToSend.getIpsPartList().size()];

        for (final Pair<String, Long> entry : listToSend.getIpsPartList()) {
            final long offset1 = offset;
            offset += entry.second;

            final long cnt1 = cnt;
            cnt += 1;

            threads[(int)cnt1] = new Thread(new Runnable() {
                @Override
                public void run() {
                    Socket sock = null;
                    InputStream in1 = null;
                    PrintWriter out1 = null;

                    try {
                        sock = new Socket(entry.first, 4325);
                        sock.setSoTimeout(MainActivity.sockTimeOut);
                        out1 = new PrintWriter(sock.getOutputStream(),
                                true);
                        in1 = sock.getInputStream();

                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                        System.exit(1);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                    try {
                        out1.write("m" + new Chunk(totalSize, offset1, entry.second, md5Sum).toJson() + "\n");
                        out1.flush();

                        String path = Environment.getExternalStorageDirectory().getPath();

                        FileOutputStream fos = new FileOutputStream(path + "/" + "playTempVid" + cnt1 + ".mp4");

                        byte[] bytes = new byte[MainActivity.buffSize];
                        int count;
                        int sum =0;

                        while ((count = in1.read(bytes)) > 0) {
                            fos.write(bytes, 0, count);
                            sum += count;
                            System.out.println("Sum " + sum);
                        }

                        in1.close();
                        out1.close();
                        fos.close();
                        sock.close();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            threads[(int)cnt1].start();
        }

        Log.i(MainActivity.LOG_TAG, "Preparing . . .");

        threads[0].join();

        Intent intent = new Intent(DisplayVideosList.this, Play.class);
        Bundle bun = new Bundle();
        System.out.println("Size of listToSend " + listToSend.getIpsPartList().size());
        bun.putInt("noofseeders", listToSend.getIpsPartList().size());
        bun.putString("peersInfo", listToSend.toJson());
        intent.putExtras(bun);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        finish();
        return true;
    }
}
