package com.chainvideoandroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface FileStore {
    String Save(String fileType, ByteArrayOutputStream fileData, String fileID) throws IOException;
}
