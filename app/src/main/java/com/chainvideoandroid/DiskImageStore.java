package com.chainvideoandroid;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DiskImageStore implements ImageStore {
    private String imageFolder;
    private ConcurrentMap<String, ImageMetada> data;

    public DiskImageStore(String imageFolder) {
        this.imageFolder = imageFolder;
        this.data = new ConcurrentHashMap<>(0);
    }
    @Override
    public String Save(String imageType, ByteArrayOutputStream imageData, String imageID) throws IOException {
        //String imageID = UUID.randomUUID().toString();s

        String imagePath = String.format("%s/%s%s", imageFolder, imageID, imageType);
        FileOutputStream fileOutputStream = new FileOutputStream(imagePath);
        imageData.writeTo(fileOutputStream);
        fileOutputStream.close();

        ImageMetada metadata = new ImageMetada(imageType, imagePath);
        data.put(imageID, metadata);
        return imageID;
    }
}
