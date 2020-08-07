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
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

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

    //
    //Load the model into a bytebuffer that can then be use for the interpreter
    public static Classifier loadClassifier(String modelPath) throws IOException {
        ByteBuffer byteBuffer = loadModelFile(modelPath);
        Interpreter interpreter = new Interpreter(byteBuffer);
        return new Classifier(interpreter);
    }
    //
    //Load a model into a bytebuffer
    private static ByteBuffer loadModelFile(String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = MainActivity.getInstance().getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    //
    //Load labels into a list
    public void loadLabelList(String labelPath) throws IOException {
        String line;
        InputStream InputStream = MainActivity.getInstance().getAssets().open(labelPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream)));
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        labelProbArray = new float[1][labelList.size()];
        reader.close();
    }


    //
    //Function that prepare a image so it can be processed by a model
    // Use if model is object segmentation
    public List<String> recognizeImage(Bitmap bitmap) {
        int batchNum = 0;
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 257, 257, true);
        float[][][][] input = new float[1][257][257][3];
        for (int x = 0; x < 257; x++) {
            for (int y = 0; y < 257; y++) {
                int pixel = resized.getPixel(x, y);
                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                input[batchNum][x][y][0] = (Color.red(pixel) - (float)127.5) / 128.0f;
                input[batchNum][x][y][1] = (Color.green(pixel) - (float)127.5) / 128.0f;
                input[batchNum][x][y][2] = (Color.blue(pixel) - (float)127.5) / 128.0f;
            }
        }
        ByteBuffer segmentationMasks = ByteBuffer.allocateDirect(1 * 257 * 257 * 21 * 4);
        segmentationMasks.order(ByteOrder.nativeOrder());
        interpreter.run(input,segmentationMasks);
        List<Integer> indexList = convertBytebufferMaskToBitmap(segmentationMasks, 257, 257);
        Set<Integer> uniqueVal = new HashSet<Integer>(indexList);
        List<String> output = new ArrayList<String>();
        for (Integer value : uniqueVal) {
            output.add(labelList.get(value));
        }
        return output;
    }

    //
    //Function that prepare a image so it can be processed by a model
    //Use if model is object recognition
    public String recognizeImage2(Bitmap bitmap) {
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

    //
    //Function that retrieve the most accurate label
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
            textToShow = "\n" + label.getKey();
        }
        return textToShow;
    }


    //
    //Function that thakes a bytebuffer and coonvert it to a bitmap
    private List<Integer> convertBytebufferMaskToBitmap(ByteBuffer inputBuffer, int imageWidth, int imageHeight) {
            int[][] mSegmentBits = new int[imageWidth][imageHeight];
            List<Integer> itemsFound = new ArrayList<>();;
            inputBuffer.rewind();
            for (int y =0; y < imageHeight; y++ ){
                for (int x =0; x < imageWidth; x++ ){
                    float maxVal = 0f;
                    mSegmentBits[x][y] = 0;

                    for (int c = 0; c < 21; c++ ){
                        float value = inputBuffer
                                .getFloat((y * imageWidth * 21 + x * 21 + c) * 4);
                        if (c == 0 || value > maxVal) {
                            maxVal = value;
                            if(c != 0){
                                mSegmentBits[x][y] = c;
                            }
                        }
                    }
                    if(y == 0){
                        if(mSegmentBits[x][y] != 0) {
                            itemsFound.add(mSegmentBits[x][y]);
                        }
                    }
                    else if(itemsFound.add(mSegmentBits[x][y-1]) != itemsFound.add(mSegmentBits[x][y])){
                        itemsFound.add(mSegmentBits[x][y]);
                    }
                }
            }

            return itemsFound;
        }
}
