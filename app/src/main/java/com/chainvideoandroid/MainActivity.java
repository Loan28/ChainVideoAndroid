package com.chainvideoandroid;

import android.app.AlertDialog;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.view.ViewGroup.LayoutParams;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.opencv.opencv_core.IplImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static MainActivity instance;

    chainServer server;


    private Classifier test_Classifier;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DiskFileStore fileStore = new DiskFileStore(this.getCacheDir().getAbsolutePath())  ;
        server = new chainServer(50051, fileStore);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button= (Button)findViewById(R.id.button);
        TextView t = (TextView)findViewById(R.id.display_1);
        instance = this;
        final AssetManager am = this.getAssets();
        button.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View view) {
                start_server();
            }
        });
    }


    public static MainActivity getInstance() {
        return instance;
    }

    //
    //Function that splits the video input into frames
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void play_video(String filePath, String model, String label, String condition, String condition2, String action, String action2, String nextDevice, String ipController, int portController, String applicationType) throws IOException {

        File file =  new File(MainActivity.getInstance().getCacheDir(), filePath);
        String path = file.getAbsolutePath();
        FFmpegFrameGrabber g = new FFmpegFrameGrabber(path);
        AndroidFrameConverter a = new AndroidFrameConverter();

        g.start();
        Frame frame;
        while ((frame = g.grabImage()) != null) {
            final Bitmap bmp = a.convert(frame);
            process_image(bmp, nextDevice, model, label, condition, condition2, action, action2, ipController, portController, applicationType);
        }
        g.stop();
    }

    public void start_server(){
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop_server() throws InterruptedException {
        server.stop();
    }


    //
    //Function that takes care of calling the functions to run the model on a frame, then sends the frame depending on the action to take sent by the controller
    public void process_image(Bitmap bMap, String nextDevice, String model, String label, String condition, String condition2, String action, String action2, String ipController, int portController, String applicationType){

        TextView t = (TextView)findViewById(R.id.display_1);
        Runtime runtime = Runtime.getRuntime();
        try {
                test_Classifier = Classifier.loadClassifier(model);
                test_Classifier.loadLabelList(label);
                chainMLClient controller = new chainMLClient(ipController,portController);
                 List<String> OutputList = null;
                long startTime = System.nanoTime();
                //
                //If model is object segmentation then use function recognizeImage, if not use recognizeImage2
                if(model.equals("deeplabv3_257.tflite")){
                    OutputList = test_Classifier.recognizeImage(bMap);
                }
                else{
                    OutputList.add(test_Classifier.recognizeImage2(bMap));
                }
                long endTime = System.nanoTime();
                long memoryLeft = ((runtime.totalMemory() - runtime.freeMemory())/1048576);
                long timeElapsed = endTime - startTime;
                controller.sendExecTime(timeElapsed, "0000003");
                controller.sendExecTime(memoryLeft, "0000003m");
                controller.defineOrder(" Feedback from Android\nExecution time in milliseconds : " + String.valueOf(timeElapsed) + "\n" + "Memory available : " + memoryLeft + " Bytes. "  );
                chainMLClient client = new chainMLClient(nextDevice, 50051);

                    if (applicationType.equals("pipeline")) {
                        boolean isInFrame = false;
                        for (int j = 0; j < OutputList.size(); j++) {
                            if (condition.equals(OutputList.get(j))) {
                                isInFrame = true;
                            }
                        }
                        if (isInFrame) {
                            System.out.println("In the frame");
                            if (!action.equals("drop"))  {
                                long startTimeFileTransfer = System.nanoTime();
                                chainMLClient client1 = new chainMLClient(action, 50051);
                                client1.uploadFile("image",bMap);
                                long endTimeFileTransfer = System.nanoTime();
                                long timeElapsedFileTransfer = endTimeFileTransfer - startTimeFileTransfer;
                                client1.shutdown();
                                controller.sendUploadTime(timeElapsedFileTransfer/1000000, "0000003");
                            }
                        } else {
                            client.shutdown();
                            controller.shutdown();
                        }
                    } else {
                        boolean isCondition1 = false;
                        boolean isCondition2 = false;
                        for (int j = 0; j < OutputList.size(); j++) {
                            if (condition.equals(OutputList.get(j))) {
                                isCondition1 = true;
                            }
                            if (condition2.equals(OutputList.get(j))) {
                                isCondition2 = true;
                            }
                        }
                        if (isCondition1) {
                            System.out.println(condition + " in the frame");
                            if (!action.equals("drop")) {
                                chainMLClient client1 = new chainMLClient(action, 50051);
                                long startTimeFileTransfer = System.nanoTime();
                                client1.uploadFile("image",bMap);
                                long endTimeFileTransfer = System.nanoTime();
                                long timeElapsedFileTransfer = endTimeFileTransfer - startTimeFileTransfer;
                                client1.shutdown();
                                controller.sendUploadTime(timeElapsedFileTransfer/1000000, "0000003");
                            }
                        }
                        if (isCondition2) {
                            System.out.println(condition2 + " in the frame");
                            if (!action2.equals("drop")) {
                                chainMLClient client2 = new chainMLClient(action2, 50051);
                                long startTimeFileTransfer = System.nanoTime();
                                client2.uploadFile("image",bMap);
                                long endTimeFileTransfer = System.nanoTime();
                                long timeElapsedFileTransfer = endTimeFileTransfer - startTimeFileTransfer;
                                client2.shutdown();
                                controller.sendUploadTime(timeElapsedFileTransfer/1000000, "0000003");
                            }
                        }
                        client.shutdown();
                        controller.shutdown();

                    }

            } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        }

}

