package com.example.deepak.prototype;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitUtil {
    final private File file;
    final private OutputStream out;
    final private Socket client;
    final private Long offsetSize;
    final private Long size;

    public SplitUtil(File file, OutputStream out, Socket client, Long offset, Long size) {
        this.file = file;
        this.out = out;
        this.client = client;
        this.offsetSize = offset;
        this.size = size;
    }

    public void split() {
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String off="";
                    Long offset = offsetSize;

                    Long hrs=offset/3600;
                    offset=offset-hrs*3600;
                    Long mn=offset/60;
                    offset=offset-mn*60;
                    Long sec=offset;

                    off = ((hrs<10)?"0"+hrs.toString():hrs.toString())+":"+((mn<10)?"0"+mn.toString():mn.toString())+":"+((sec<10)?"0"+sec.toString():sec.toString());

                    // -i input.mp4 -ss 00:00:50.0 -codec copy -t 20 output.mp4

                    Matcher mat = Pattern.compile("(.*)\\.(.*)").matcher(file.getAbsolutePath());
                    String newpath=null;

                    if(mat.find()){
                        newpath= mat.group(1)+"COPY."+mat.group(2);
                    }

                    //String cmd = "-y -i " + file.getAbsolutePath() + " -ss "+ off +" -codec copy -t " + size + " " + newpath ;
                    String[] command = {"-y", "-i", file.getAbsolutePath(), "-ss", off, "-codec", "copy", "-t", size.toString(), newpath};

                    try {
                        File toDelete = null;
                        if (newpath != null) {
                            toDelete = new File(newpath);
                        }
                        if (toDelete != null) {
                            if(toDelete.delete())
                                System.out.println("COPY file REMOVED!");
                            else
                                System.out.println("COPY file doesn't exist already");
                        }
                    }catch(Exception e) {
                        System.out.println("COPY file doesn't exist already");
                    }

                    ffmpegSplit(command, out, newpath, client);
                   /* init array with file length
                    bytesArray = new byte[len.intValue()];
                    FileInputStream fis = new FileInputStream(file);
                    fis.read(bytesArray,offset.intValue(),len.intValue()); //read file into bytes[]
                    fis.close();
                    */
                }
            }).start();

        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void ffmpegSplit(final String[] command, final OutputStream out, final String newpath, final Socket client) {
        try {

            MainActivity.ffmpeg.execute(command, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String s) {

                    System.out.println("Splitting failed");
                    try {
                        client.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onSuccess(String s) {
                    try {
                        System.out.println("Sucess split");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                File file = new File(newpath);
                                try {
                                    byte[] bytesArray = new byte[MainActivity.buffSize];
                                    FileInputStream fis = new FileInputStream(file);
                                    int count;

                                    int sum = 0;
                                    while ((count = fis.read(bytesArray)) > 0) {
                                        out.write(bytesArray, 0, count);
                                        sum += count;
                                    }
                                    //   fis.read(bytesArray); //read file into bytes[]
                                    System.out.println("Sent all");
                                    fis.close();
                                    //out.close();
                                    //client.close();
                                } catch(IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        out.close();
                                        client.close();
                                    }catch(Exception e1) {
                                        e1.printStackTrace();
                                    }
                                    try {
                                        file.delete();
                                    } catch(Exception e) {
                                        System.out.println("Unable to delete COPY file after sending");
                                    }
                                }
                            }
                        }).start();

                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onProgress(String s) {
                    //    Toast.makeText(MainActivity.this,"Progress\n"+s,Toast.LENGTH_SHORT).show();
                    //pr("P");
                }

                @Override
                public void onStart() {
                    //  Toast.makeText(MainActivity.this,"Start",Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFinish() {
                    //   Toast.makeText(MainActivity.this,"Finish",Toast.LENGTH_SHORT).show();
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // do nothing for now
            e.printStackTrace();
        }
    }
}
