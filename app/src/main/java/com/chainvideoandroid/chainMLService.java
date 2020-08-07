package com.chainvideoandroid;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.chainML.pb.*;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.*;
import java.util.logging.Logger;

public class chainMLService extends chainMLServiceGrpc.chainMLServiceImplBase {

    private static final Logger logger = Logger.getLogger(chainMLService.class.getName());
    private FileStore fileStore;

    public chainMLService(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    String nextDevice;
    String ipController;
    String model;
    String label;
    String action;
    String action2;
    String condition;
    String condition2;
    String applicationType;
    int portController;


    //
    //Receives from the controller it's IP and port
    @Override
    public void defineController(DefineControllerRequest request, StreamObserver<DefineControllerReply> responseObserver) {
        ipController = request.getIpController();
        Runtime runtime = Runtime.getRuntime();
        portController = request.getPortController();
        long memory = runtime.totalMemory() - runtime.freeMemory();
        int numberOfProcessors = runtime.availableProcessors();
        String OS = Build.MODEL;
        DefineControllerReply reply = DefineControllerReply.newBuilder().setMessage("Device :" + OS + "\n" + "Number of processors: " +
                + numberOfProcessors + "\n" + "Memory available: \"\n" +
                + memory + " bytes \nModels available on the device: \nmobilenet.tflite").build();        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    //
    //Receive which device is next in line
    @Override
    public void defineOrder(OrderRequest request, StreamObserver<OrderReply> responseObserver) {
        OrderReply reply = OrderReply.newBuilder().setMessage("Hello " + request.getName()).build();
        nextDevice = request.getName();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    //
    //Sends the spec of the device to the controller
    @Override
    public void getSpecs(OrderRequest request, StreamObserver<OrderReply> responseObserver) {
        Runtime runtime = Runtime.getRuntime();
        long memory = runtime.totalMemory() - runtime.freeMemory();
        int numberOfProcessors = runtime.availableProcessors();
        String OS = Build.MODEL;
        OrderReply reply = OrderReply.newBuilder().setMessage("Device : " + OS + "\n" + "Number of processors: " + numberOfProcessors + "\n" + "Memory available: " + memory + " bytes \n" + "\nModels available: \n deeplabv3_257.tflite \n mobilenet.tflite ").build();
        nextDevice = request.getName();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    //
    //Receives :
    // - All the information needed from a device to run a model
    // - Action that should be taken once this model has been run
    @Override
    public void defineModelLabel(DefineModelLabelRequest request, StreamObserver<DefineModelLabelReply> responseObserver) {
        model = request.getModel();
        label = request.getLabel();
        condition = request.getCondition();
        condition2 = request.getCondition2();
        action = request.getAction();
        action2 = request.getAction2();
        applicationType = request.getApplicationType();
        DefineModelLabelReply reply = DefineModelLabelReply.newBuilder().setMessage("Received model and label info").build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    //
    //Function that stores file receive from a client
    @Override
    public StreamObserver<UploadFileRequest> uploadFile(final StreamObserver<UploadFileResponse> responseObserver) {
        return new StreamObserver<UploadFileRequest>() {
            private String fileType;
            private ByteArrayOutputStream fileData;
            private TypeFile type_file;
            private FileName file_name;
            @Override
            public void onNext(UploadFileRequest request) {
                if(request.getDataCase() == UploadFileRequest.DataCase.INFO) {
                    FileInfo info = request.getInfo();
                    type_file = request.getTypeFile();
                    logger.info("receive " + type_file.getTypefile() + " info" + info);
                    fileType = info.getImageType();
                    fileData = new ByteArrayOutputStream();
                    file_name = request.getFileName();

                    return;

                }
                ByteString chunkData = request.getChunkData();
                if (fileData == null) {
                    logger.info( type_file.getTypefile()+ " info was not sent before");
                    responseObserver.onError(
                            Status.INVALID_ARGUMENT
                                    .withDescription(type_file.getTypefile() + "info was not sent before")
                                    .asRuntimeException()
                    );
                    return;
                }
                try {
                    chunkData.writeTo(fileData);
                } catch (IOException e) {
                    responseObserver.onError(
                            Status.INTERNAL
                                    .withDescription("cannot write chunk data: " + e.getMessage())
                                    .asRuntimeException()
                    );
                    return;
                }

            }

            @Override
            public void onError(Throwable t) {
                logger.warning(t.getMessage());
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onCompleted() {
                String fileID = "";
                int imageSize = fileData.size();

                try {
                    if(type_file.getTypefile().equals("image")) {
                        fileID = fileStore.Save(fileType, fileData, "image");
                        byte[] bitmapdata = fileData.toByteArray();
                        Bitmap bMap = BitmapFactory.decodeByteArray(bitmapdata, 0, bitmapdata.length);
                        MainActivity.getInstance().process_image(bMap, nextDevice, model, label, condition, condition2, action, action2, ipController, portController, applicationType);
                    } else{
                        fileID = fileStore.Save(fileType, fileData, "video");
                        MainActivity.getInstance().play_video(fileID + fileType, model, label, condition, condition2, action, action2, nextDevice, ipController, portController, applicationType);
                    }

                } catch (IOException e) {
                    responseObserver.onError(
                            Status.INTERNAL
                                    .withDescription("cannot save the " + type_file.getTypefile() + " to the store: " + e.getMessage())
                                    .asRuntimeException()
                    );
                }
                logger.info( "Received: " + file_name.getFilename() + fileType);


                UploadFileResponse response = UploadFileResponse.newBuilder()
                        .setId(fileID)
                        .setSize(imageSize)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

            }

        };
    }
}
