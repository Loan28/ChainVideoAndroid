package com.chainvideoandroid;

public class ImageMetada {
    private String type;
    private String path;

    public ImageMetada(String type, String path){
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
