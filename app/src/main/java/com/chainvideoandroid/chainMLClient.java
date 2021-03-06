package com.chainvideoandroid;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.chainML.pb.FileInfo;
import com.chainML.pb.FileName;
import com.chainML.pb.ImageInfo;
import com.chainML.pb.OrderReply;
import com.chainML.pb.OrderRequest;
import com.chainML.pb.TimeReply;
import com.chainML.pb.TimeRequest;
import com.chainML.pb.TypeFile;
import com.chainML.pb.UploadFileRequest;
import com.chainML.pb.UploadFileResponse;
import com.chainML.pb.chainMLServiceGrpc;
import com.chainML.pb.chainMLServiceGrpc.chainMLServiceBlockingStub;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class chainMLClient {
    private static final Logger logger = Logger.getLogger(chainMLClient.class.getName());

    private final ManagedChannel channel;
    private final chainMLServiceBlockingStub blockingStub;
    private final chainMLServiceGrpc.chainMLServiceStub asyncStub;

    public chainMLClient(String host, int port){
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        blockingStub = chainMLServiceGrpc.newBlockingStub(channel);
        asyncStub = chainMLServiceGrpc.newStub(channel);

    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    //
    //Receive which device is next in line
    public void defineOrder(String name) {
        OrderRequest request = OrderRequest.newBuilder().setName(name).build();
        OrderReply response;
        try {
            response = blockingStub.defineOrder(request);
            logger.info(response.getMessage());
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
    }
    //
    //Send upload time to the controller
    public void sendUploadTime(double execTime, String device) {
        TimeRequest request = TimeRequest.newBuilder().setTime(execTime).setDevice(device).build();
        TimeReply response;
        try {
            response = blockingStub.sendUploadTime(request);
            logger.info(response.getName());
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
    }
    //
    //Send execution time to the controller
    public void sendExecTime(double execTime, String device) {
        TimeRequest request = TimeRequest.newBuilder().setTime(execTime).setDevice(device).build();
        TimeReply response;
        try {
            response = blockingStub.sendExecTime(request);
            logger.info(response.getName());
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
    }
    //
    //Function to upload file to the server, arg: file path, type of file sent
    public void uploadFile(String type, Bitmap bmap) throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<UploadFileRequest> requestObserver = asyncStub.uploadFile(new StreamObserver<UploadFileResponse>() {
            @Override
            public void onNext(UploadFileResponse response) {
                logger.info("receive response: " + response);

            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.SEVERE, "upload failed: " + t);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("file uploaded");
                finishLatch.countDown();
            }
        });

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
        byte[] bitmapdata = bos.toByteArray();
        ByteArrayInputStream bs = new ByteArrayInputStream(bitmapdata);

        String imageType = ".png";
        String imageName = "test";

        FileInfo info = FileInfo.newBuilder().setImageType(imageType).build();
        TypeFile typeFile = TypeFile.newBuilder().setTypefile(type).build();
        FileName fileName = FileName.newBuilder().setFilename(imageName).build();
        UploadFileRequest request = UploadFileRequest.newBuilder().setInfo(info).setFileName(fileName).setTypeFile(typeFile).build();

        try {
            requestObserver.onNext(request);
            logger.info("sent file info" + info);

            byte[] buffer = new byte[1024];
            while (true) {
                int n = bs.read(buffer);
                if (n <= 0) {
                    break;
                }

                if (finishLatch.getCount() == 0) {
                    return;
                }
                request = UploadFileRequest.newBuilder()
                        .setChunkData(ByteString.copyFrom(buffer, 0, n))
                        .build();
                requestObserver.onNext(request);
                logger.info("sent file chunk with size: " + n);
            }
        }catch (Exception e){
            logger.log(Level.SEVERE, "unexcepted error: " + e.getMessage());
            requestObserver.onError(e);
            return;
        }

        requestObserver.onCompleted();

        if (!finishLatch.await(1, TimeUnit.MINUTES)){
            logger.warning("request cannot finish within 1 minute");
        }
    }
}
