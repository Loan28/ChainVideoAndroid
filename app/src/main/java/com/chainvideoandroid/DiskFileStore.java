package com.chainvideoandroid;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DiskFileStore implements FileStore {
    private String fileFolder;
    private ConcurrentMap<String, FileMetada> data;

    public DiskFileStore(String fileFolder) {
        this.fileFolder = fileFolder;
        this.data = new ConcurrentHashMap<>(0);
    }
    @Override
    public String Save(String fileType, ByteArrayOutputStream fileData, String fileID) throws IOException {

        String imagePath = String.format("%s/%s%s", fileFolder, fileID, fileType);
        FileOutputStream fileOutputStream = new FileOutputStream(imagePath);
        fileData.writeTo(fileOutputStream);
        fileOutputStream.close();

        FileMetada metadata = new FileMetada(fileType, imagePath);
        data.put(fileID, metadata);
        return fileID;
    }
}
