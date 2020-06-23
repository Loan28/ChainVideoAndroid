package com.chainvideoandroid;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class Classifier {
    private final Interpreter interpreter;
    private List<String> labelList = new ArrayList<>();;
    private float[][] labelProbArray;
    Classifier(Interpreter interpreter) {
        this.interpreter = interpreter;
    }
    private static final int RESULTS_TO_SHOW = 1;
    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });
    public static Classifier loadClassifier(AssetManager assetManager, String modelPath) throws IOException {
        ByteBuffer byteBuffer = loadModelFile(assetManager, modelPath);
        File modelFile =  new File( MainActivity.getInstance().getCacheDir(), "model.tflite");
        Interpreter interpreter = new Interpreter(modelFile);
        return new Classifier(interpreter);
    }
    private static ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    public void loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        String line;
        File labelFile =  new File( MainActivity.getInstance().getCacheDir(), "label.txt");
        FileInputStream fileInputStream = new FileInputStream(labelFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader((fileInputStream)));
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        labelProbArray = new float[1][labelList.size()];
        reader.close();
    }
    public String recognizeImage(Bitmap bitmap) {
        int batchNum = 0;
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        float[][][][] input = new float[1][224][224][3];
        for (int x = 0; x < 224; x++) {
            for (int y = 0; y < 224; y++) {
                int pixel = resized.getPixel(x, y);
                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                input[batchNum][x][y][0] = (Color.red(pixel) - 127) / 128.0f;
                input[batchNum][x][y][1] = (Color.green(pixel) - 127) / 128.0f;
                input[batchNum][x][y][2] = (Color.blue(pixel) - 127) / 128.0f;
            }
        }
        interpreter.run(input,labelProbArray);
        String textToShow = printTopKLabels();
        return textToShow;
    }
    private String printTopKLabels() {
        for (int i = 0; i < labelList.size(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(labelList.get(i), (labelProbArray[0][i]) / 255.0f));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        String textToShow = "";
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            textToShow = "\n" + label.getKey() + ":" + Float.toString(label.getValue()) + textToShow;
        }
        return textToShow;
    }

}
