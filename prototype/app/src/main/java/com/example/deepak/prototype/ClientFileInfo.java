package com.example.deepak.prototype;

import java.io.File;

public class ClientFileInfo {
    private File file;
    private Long duration;

    public ClientFileInfo(File file, Long duration) {
        this.file = file;
        this.duration = duration;
    }

    public File getFile() {
        return file;
    }

    public Long getDuration() {
        return duration;
    }
}
