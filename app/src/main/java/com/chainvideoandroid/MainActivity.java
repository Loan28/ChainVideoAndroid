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
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.opencv.opencv_core.IplImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private static MainActivity instance;
    VideoView vid;
    MediaController m;
    MediaMetadataRetriever mediaMetadataRetriever;
    ContextWrapper c = new ContextWrapper(this);
    final Handler handler = new Handler();
    final int delay = 500; //milliseconds
    int i = 0;
   // String destFolder = getCacheDir().getAbsolutePath();
    chainServer server;


    private Classifier test_Classifier;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DiskFileStore fileStore = new DiskFileStore(this.getCacheDir().getAbsolutePath())  ;
        server = new chainServer(50051, fileStore);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button= (Button)findViewById(R.id.button);
        Button buttonPlay = (Button)findViewById(R.id.button2);
        TextView t = (TextView)findViewById(R.id.display_1);
        instance = this;
        vid = (VideoView)findViewById(R.id.videoView);
        mediaMetadataRetriever = new MediaMetadataRetriever();

        button.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View view) {
                start_server();
            }
        });

        buttonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        play_video();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

    }
    public static MainActivity getInstance() {
        return instance;
    }

    public void take_frame(){
        i++;

        int currentPosition = vid.getCurrentPosition(); //in millisecond
        Toast.makeText(MainActivity.this,
                "Current Position: " + currentPosition + " (ms)",
                Toast.LENGTH_LONG).show();

        Bitmap bmFrame = mediaMetadataRetriever
                .getFrameAtTime(currentPosition * 1000); //unit in microsecond

        if(bmFrame == null){
            Toast.makeText(MainActivity.this,
                    "bmFrame == null!",
                    Toast.LENGTH_LONG).show();
        }else{
            AlertDialog.Builder myCaptureDialog =
                    new AlertDialog.Builder(MainActivity.this);
            ImageView capturedImageView = new ImageView(MainActivity.this);
            capturedImageView.setImageBitmap(bmFrame);
            LayoutParams capturedImageViewLayoutParams =
                    new LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT);
            capturedImageView.setLayoutParams(capturedImageViewLayoutParams);

            myCaptureDialog.setView(capturedImageView);
            recognize_image2(bmFrame);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void play_video() throws IOException {
     /*   i = 0;
        m = new MediaController(this);
        m.setAnchorView(vid);

        Uri u = Uri.parse(path);
        mediaMetadataRetriever.setDataSource(this, u);

        vid.setMediaController(m);
        vid.setVideoURI(u);
        vid.requestFocus();
        vid.start();
        handler.postDelayed(new Runnable(){
                public void run(){
                    if(i < 5 ){
                        take_frame();
                        handler.postDelayed(this, delay);
                    }
                    else{
                        handler.removeCallbacks(this);
                    }
                }
            }, delay);
*/
        String path = "android.resource://" + getPackageName() + "/" + R.raw.cup_keyboard_mouse;
        FFmpegFrameGrabber g = new FFmpegFrameGrabber(path);
        Java2DFrameConverter c = new Java2DFrameConverter();
        g.start();
        Frame frame;
        while ((frame = g.grabImage()) != null) {
            if(frame.keyFrame) {

                ByteBuffer buf = frame.data;
                byte[] imageBytes = new byte[buf.remaining()];
                buf.get(imageBytes);
                final Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                recognize_image2(bmp);
            }
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


    public void recognize_image2(Bitmap bMap){
            TextView t = (TextView)findViewById(R.id.display_1);
            try {
                test_Classifier = Classifier.loadClassifier();
                test_Classifier.loadLabelList();
                t.setText(test_Classifier.recognizeImage(bMap));
                //chainMLClient client = new  chainMLClient("192.168.1.69", 50051);
                //client.uploadFile("image",bMap);
                //client.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public void recognize_image3(ByteArrayOutputStream bMapArray, String nextDevice){
        TextView t = (TextView)findViewById(R.id.display_1);
        try {
            test_Classifier = Classifier.loadClassifier();
            test_Classifier.loadLabelList();
            byte[] bitmapdata = bMapArray.toByteArray();
            Bitmap bMap = BitmapFactory.decodeByteArray(bitmapdata, 0, bitmapdata.length);
            t.setText(test_Classifier.recognizeImage(bMap));

            if(nextDevice.equals("end"))
            {
                System.out.println("end");
            }
            else{
                chainMLClient client = new chainMLClient(nextDevice, 50051);
                client.uploadFile("image",bMap);
                client.shutdown();            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
