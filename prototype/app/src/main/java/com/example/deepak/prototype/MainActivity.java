package com.example.deepak.prototype;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.UUID.randomUUID;


public class MainActivity extends AppCompatActivity {

    private InetAddress INET_ADDRESS;
    private MulticastSocket multicastSocket;
    private WifiManager.MulticastLock lock;
    private static ClientFileManager clientFileManager;
    private ServerFileManager serverFileManager;
    private Peer smartNode;
    private ArrayList<Peer> peers;
    public static String LOG_TAG = "P2P";

    @SuppressLint("StaticFieldLeak")
    public static FFmpeg ffmpeg;
    private static Semaphore ffmpegSemaphore;
    public Thread electionListenerThread;
    public Thread smartHeadListenerThread;
    public Thread clientListenerThread;

    public final static String INET_ADDR = "224.0.0.3";
    public final static int PORT = 8888;
    public static final int buffSize = 10000000;      //10 MB
    public static final int sockTimeOut = 600000;     //10 min


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        populateData();
        try {
            lockWifi();
            INET_ADDRESS = InetAddress.getByName(INET_ADDR);
            multicastSocket = new MulticastSocket(PORT);
        } catch (IOException io) {
            raiseError();
            io.printStackTrace();
        }

        serverFileManager = new ServerFileManager();
        peers = new ArrayList<>();

        smartHeadListenerThread = getSmartHeadListenerThread();
        smartHeadListenerThread.start();

        clientListenerThread = getClientListenerThread();
        clientListenerThread.start();

        electionListenerThread = getListenerThread();
        electionListenerThread.start();

        ffmpeg = FFmpeg.getInstance(getApplicationContext());
        loadFFMpegBinary();

        ffmpegSemaphore = new Semaphore(1);

        clientFileManager = new ClientFileManager(getIP());
        clientFileManager.init(getApplicationContext());
    }

    private void loadFFMpegBinary() {
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    System.exit(1);
                }
            });
        } catch (FFmpegNotSupportedException e) {
            e.printStackTrace();
        }
    }

    private Thread  getClientListenerThread() {
        return new Thread() {
            @Override
            public void run() {
                ServerSocket server = null;
                Socket client = null;
                BufferedReader in = null;
                OutputStream out = null;

                // INPUT STREAM SOCKET
                try {
                    server = new ServerSocket(4325);
                    server.setSoTimeout(sockTimeOut);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                while (true) {
                    try {
                        client = server.accept();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        out = client.getOutputStream();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        String line = null;
                        if (in != null) {
                            line = in.readLine();
                        }

                        if (line != null && line.charAt(0) == 'm') {
                            String lin = line.substring(1);
                            Chunk chk = Chunk.fromJson(lin);

                            File file =  clientFileManager.getFile(chk.getMd5Sum());
                            SplitUtil splitUtil = new SplitUtil(file, out, client, chk.getOffset(), chk.getReqSize());
                            splitUtil.split();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    private Thread getSmartHeadListenerThread () {
        return new Thread() {
            @Override
            public void run() {
                try {
                    ServerSocket server = null;
                    Socket client = null;
                    BufferedReader in = null;
                    PrintWriter out = null;

                    // INPUT STREAM SOCKET
                    try {
                        server = new ServerSocket(4321);
                        server.setSoTimeout(sockTimeOut);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    while (true) {
                        try {
                            client = server.accept();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        try {
                            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            out = new PrintWriter(client.getOutputStream(),
                                    true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        try {
                            String line = null;

                            if (in != null) {
                                line = in.readLine();
                            }

                            if (line != null && line.charAt(0) == 'i') {        //client has sent his file information, update hashmaps
                                ClientFileManager fm1 = clientFileManager.fromJson(line.substring(1));

                                Log.i(LOG_TAG, "Files information received by Smart Head from IP " + fm1.getIp() + "\n" + line.substring(1));

                                for (Map.Entry<String, ClientFileInfo> entry : fm1.getFilesMap().entrySet()) {
                                    serverFileManager.insertEntry(entry.getKey(),
                                            fm1.getIp(),
                                            entry.getValue().getDuration(),
                                            entry.getValue().getFile());
                                }

                                System.out.println("ServerFileManager : " + serverFileManager.toJson());
                            } else if (line != null && out != null && line.charAt(0) == 'r') {
                                Log.i(LOG_TAG, "Request for all files");

                                System.out.println("Request Received " + line);
                                out.write(serverFileManager.toJson() + "\n");
                                out.flush();
                            } else if (line != null && out != null && line.charAt(0) == 'd') {
                                String md5Sum = line.substring(1);
                                Log.i(LOG_TAG, "Md5Sum " + md5Sum);

                                ServerFileInfo serverFileInfo = serverFileManager.getFilesInfo(md5Sum);
                                ArrayList<Pair<String, Double>> ips = new ArrayList<>();

                                Log.i(LOG_TAG, "Server File Info " + serverFileInfo.toJson());


                                if (serverFileInfo != null) {
                                    double totalBattery = 0f;

                                    for (Pair<String, File> entry : serverFileInfo.getFileList()) {
                                        for (Peer peer : peers) {
                                            if (peer.getIpAddr().equals(entry.first) && peer.getBattery() > 20.0) {
                                                ips.add(new Pair<>(entry.first, peer.getBattery().doubleValue()));
                                                totalBattery += peer.getBattery();
                                            }
                                        }
                                    }
                                    Log.i(LOG_TAG, Integer.toString(ips.size()));
                                    double totalDuration = serverFileInfo.getDuration().doubleValue();
                                    Long currentDur = ((long) totalDuration);
                                    ListToSend listToSend = new ListToSend();

                                    double tempVal;
                                    long tempDur;

                                    for (Pair<String, Double> entry : ips) {
                                        tempVal = totalDuration * (entry.second/totalBattery);
                                        tempDur = ((long) Math.ceil(tempVal));
                                        tempDur = Math.min(tempDur, currentDur);
                                        currentDur -= tempDur;
                                        listToSend.insertEntry(entry.first, tempDur);
                                        if (currentDur == 0)
                                            break;
                                    }

                                    Log.i(LOG_TAG, "List Sent : " + listToSend.toJson());

                                    out.write(listToSend.toJson() + "\n");
                                    out.flush();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private Thread getListenerThread() {
         return new Thread() {
            byte[] buf = new byte[256];

            @Override
            public void run() {
                try {
                    while (true) {
                        buf = new byte[256];
                        DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);

                        if (isInterrupted()) {
                            //raiseError();
                            return;
                        }

                        if (multicastSocket.isClosed())
                            return;

                        multicastSocket.receive(msgPacket);


                        if (isInterrupted()) {
                            //raiseError();
                            return;
                        }

                        String msg = new String(buf, 0, msgPacket.getLength());

                        System.out.println("Socket 1 received msg: " + msg);

                        if (msg.charAt(0) == 'e') {
                            Log.i(LOG_TAG, "Starting the election " + msg.substring(1));

                            serverFileManager.reset();
                            peers.clear();
                            Peer msgPeer = Peer.fromJson(msg.substring(1));

                            if (!msgPeer.getIpAddr().equals(getIP())) {
                                Peer peer = new Peer(randomUUID().toString(), getBattery(), getIP());
                                sendMulticastPacket("p" + peer.toJson());
                            }

                            peers.add(msgPeer);

                            smartNode = findSmartNode(peers);

                            System.out.println(smartNode.toJson());

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    populateSmartHeadInfo(smartNode);
                                }
                            });

                        } else {
                            Log.i(LOG_TAG, "Sending candidacy " + msg.substring(1));

                            Peer msgPeer = Peer.fromJson(msg.substring(1));
                            peers.add(msgPeer);

                            System.out.println(peers.size());

                            smartNode = findSmartNode(peers);

                            System.out.println(smartNode.toJson());

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    populateSmartHeadInfo(smartNode);
                                }
                            });
                        }
                    }
                } catch (IOException eIO) {
                    //raiseError();
                    eIO.printStackTrace();
                }
            }
        };
    }

    private void sendFilesInfo(final String ip, final int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String data = clientFileManager.toJson();
                if (!sendSocketPacket(ip, port, data)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Connection Refused By Smart Head", Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }
            }
        }).start();
    }

    private boolean sendSocketPacket(String ip, int port, String data) {
        Socket socket;
        PrintWriter out = null;

        //Create socket connection
        try {
            socket = new Socket(ip, port);
            socket.setSoTimeout(sockTimeOut);
            out = new PrintWriter(socket.getOutputStream(),true);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        try {
            out.write("i" + data + "\n");
            System.out.println(data);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @SuppressLint("SetTextI18n")
    private void populateSmartHeadInfo(final Peer smartHead) {
        Button button = findViewById(R.id.subscribe);
        button.setVisibility(View.INVISIBLE);

        Button requestButton = findViewById(R.id.request);
        requestButton.setVisibility(View.VISIBLE);

        TextView ramLeft = findViewById(R.id.memLeft);
        TextView batteryLeft = findViewById(R.id.batteryLeft);

        final String headIP = "Smart Head IP : ";
        final String headBattery = "Smart Head Battery Left : ";

        ramLeft.setText(headIP + smartHead.getIpAddr());
        batteryLeft.setText(headBattery + smartHead.getBattery() + " %");
    }

    private Peer findSmartNode(final ArrayList<Peer> peers) {
        Peer smartHead = null;

        for (Peer peer : peers) {
            if (smartHead == null)
                smartHead = peer;
            else if (smartHead.getBattery() < peer.getBattery())
                smartHead = peer;
        }

        sendFilesInfo(smartHead.getIpAddr(), 4321);

        Log.i(LOG_TAG, "New elected smart head " + smartHead.toJson());

        return smartHead;
    }

    @SuppressLint("SetTextI18n")
    private void populateData() {
        Button requestButton = findViewById(R.id.request);
        requestButton.setVisibility(View.INVISIBLE);

        TextView yourIPAddr = findViewById(R.id.yourIP);
        TextView batteryLeft = findViewById(R.id.batteryLeft);
        TextView ramLeft = findViewById(R.id.memLeft);
        final String IpInfo = "Your Ip address";
        final String batteryInfo = "Battery Left";
        final String ramInfo = "RAM Unused";

        yourIPAddr.setText(IpInfo + " : " + getIP());
        batteryLeft.setText(batteryInfo + " : " + getBattery() + " %");
        ramLeft.setText(ramInfo + " : " + getFreeMem() + " MB");
    }

    public void joinNetwork(View view) throws IOException {

            multicastSocket.joinGroup(INET_ADDRESS);

            new Thread(new Runnable() {
                Peer peer = new Peer(randomUUID().toString(), getBattery(), getIP());
                @Override
                public void run() {
                    sendMulticastPacket("e" + peer.toJson());   //request to start election
                }
            }).start();
    }

    public void requestFile(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //Toast.makeText(getApplicationContext(), "Request Sent", Toast.LENGTH_LONG).show();
                System.out.println("Inside Send Request Thread.");
                Socket socket;
                BufferedReader in = null;
                PrintWriter out = null;

                //Create socket connection
                try {
                    socket = new Socket(smartNode.getIpAddr(), 4321);
                    socket.setSoTimeout(sockTimeOut);
                    out = new PrintWriter(socket.getOutputStream(),
                            true);
                    in = new BufferedReader(new InputStreamReader(
                            socket.getInputStream()));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    System.exit(1);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                try {
                    out.write("r" + "\n");
                    out.flush();

                    String filesInfo = in.readLine();

                    System.out.println("FileInfo " + filesInfo);

                    Intent intent = new Intent(MainActivity.this, DisplayVideosList.class);
                    intent.putExtra("filesInfo", filesInfo);
                    intent.putExtra("headIP", smartNode.getIpAddr());
                    intent.putExtra("ipAddr", getIP());
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void raiseError() {
        Toast.makeText(getApplicationContext(), "Something Went Wrong", Toast.LENGTH_LONG)
                .show();
    }

    private void lockWifi() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager != null) {
            lock = wifiManager.createMulticastLock("WifiDevices");
            lock.acquire();
        }
    }

    private void unlockWifi() {
       if (lock != null) {
           lock.release();
           lock = null;
       }
    }

    private String getIP() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip;
    }

    private Float getBattery() {
        final IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        Intent batteryStatus = this.registerReceiver(null, ifilter);

        assert batteryStatus != null;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return 100 * (level / (float) scale);
    }

    private Long getFreeMem() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        Long availableMegs = memoryInfo.availMem / 1048576L;

        return availableMegs;
    }

   private void sendMulticastPacket(String msg) {
       try {
           DatagramSocket socketClient;
           socketClient = new DatagramSocket();
           byte [] outBuf = msg.getBytes();

           DatagramPacket outPacket = new DatagramPacket(outBuf, outBuf.length, INET_ADDRESS, PORT);
           socketClient.send(outPacket);
           socketClient.close();

           System.out.println("Success");
       } catch (IOException e) {
           e.printStackTrace();
       }
   }

   @Override
    public void onBackPressed() {
        electionListenerThread.interrupt();
        multicastSocket.close();
        unlockWifi();
        this.finish();
        System.exit(0);
   }


    public static void getDuration(final File file, final String md5) {
        final String path = file.getAbsolutePath();

        final String[] command = {"-i", path};

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ffmpegSemaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ffmpegDuration(command, md5, file);
                ffmpegSemaphore.release();
            }
        }).start();
    }

    public static void ffmpegDuration(final String[] command, final String md5, final File file) {
        try {
            ffmpeg.execute(command, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String s) {
                    Long dur = FollowDur(s);
                    clientFileManager.insertEntry(md5, file, dur);
                }

                @Override
                public void onSuccess(String s) {
                    Long dur = FollowDur(s);
                    clientFileManager.insertEntry(md5, file, dur);
                }

                @Override
                public void onProgress(String s) {
                }

                @Override
                public void onStart() {
                }

                @Override
                public void onFinish() {
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // do nothing for now
            e.printStackTrace();
        }
    }

    public static Long FollowDur(String s) {
        String pattern = "Duration: ([0-9][0-9]):([0-9][0-9]):([0-9][0-9])";

        // Create a Pattern object
        Pattern r = Pattern.compile(pattern);

        // Now create matcher object.
        Matcher m = r.matcher(s);

        Long sec = 0L;

        if (m.find()) {
            sec = Long.parseLong(m.group(1)) * 60 * 60 + Long.parseLong(m.group(2)) * 60 + Long.parseLong(m.group(3));
        }

        return sec;
    }
}
