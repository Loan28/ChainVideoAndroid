package com.chainvideoandroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface ImageStore {
    String Save(String imageType, ByteArrayOutputStream imageData, String imageID) throws IOException;
}
