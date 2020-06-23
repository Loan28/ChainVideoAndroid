package com.chainvideoandroid;

public class FileMetada {
    private String type;
    private String path;

    public FileMetada(String type, String path){
        this.type = type;
        this.path = path;

    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }
}
