package com.buquati.vipr;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Trace;
import android.util.Log;

import com.buquati.vipr.models.Item;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.Operation;
import org.tensorflow.TensorFlow;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;
import org.tensorflow.types.UInt8;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

public class TensorFlowImageClassifier implements Classifier {

    // Pre-allocated buffers.
    private HashMap<Integer,HashMap<String,String>> labels = new HashMap<>();
    private static final String TAG = "TensorFlowImageClassifier";

    // Only return this many results with at least this confidence.
    private static final int MAX_RESULTS = 5;
    private static final float THRESHOLD = 0.1f;

    // Config values.
    private String inputName;
    private String outputName;
    private int inputSize;

    // Pre-allocated buffers.
    private int[] intValues;
    private float[] floatValues;
    private float[] outputs;
    private String[] outputNames;

    private boolean logStats = false;


    private TensorFlowInferenceInterface inferenceInterface;

    private TensorFlowImageClassifier() {}

    @SuppressLint("LongLogTag")
    public static Classifier create(
            AssetManager assetManager,
            String modelFilename,
            String labelFilename,
            int inputSize,
            String inputName,
            String outputName) throws IOException, JSONException {
        TensorFlowImageClassifier c = new TensorFlowImageClassifier();
        c.inputName = inputName;
        c.outputName = outputName;
        // Read the label names into memory.



        c.inferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);

        // The shape of the output is [N, NUM_CLASSES], where N is the batch size.
        final Operation operation = c.inferenceInterface.graphOperation(outputName);

        final int numClasses = (int) operation.output(0).shape().size(1);
        // Read the label names into memory.
        String json=null;
        InputStream is =assetManager.open(labelFilename);
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        json = new String(buffer, "UTF-8");
        JSONObject jsonObject = new JSONObject(json);
        for(int ct=0;ct<numClasses;ct++){
            JSONObject one = jsonObject.getJSONObject(String.valueOf(ct+1));
            HashMap<String,String> jsonARR = new HashMap<>();
            jsonARR.put("name",one.getString("name"));
            jsonARR.put("display_name",one.getString("display_name"));
            jsonARR.put("image_path",one.getString("image_path"));
            jsonARR.put("image_name",one.getString("image_name"));
            jsonARR.put("url",one.getString("url"));
            /* labels: 0 - 8
             *  detection_classes: 1 - 9
             * */
            c.labels.put(ct,jsonARR);
        }
        Log.i(TAG, "Read " + c.labels.size() + " labels, output layer size is " + numClasses);

        // Ideally, inputSize could have been retrieved from the shape of the input operation.  Alas,
        // the placeholder node for input in the graphdef typically used does not specify a shape, so it
        // must be passed in as a parameter.
        c.inputSize = inputSize;

        // Pre-allocate buffers.
        c.outputNames = new String[] {outputName};
        c.intValues = new int[inputSize * inputSize];
        c.floatValues = new float[inputSize * inputSize * 3];
        c.outputs = new float[numClasses];

        return c;
    }


    public List<Recognition> recognizeImage(final Bitmap bitmap, AssetManager assetManager)  {

        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("preprocessBitmap");
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            floatValues[i * 3] = (((val >> 16) & 0xFF) / 255.0f);
            floatValues[i * 3 + 1] = (((val >> 8) & 0xFF) / 255.0f);
            floatValues[i * 3 + 2] = ((val & 0xFF) / 255.0f);
        }
        Trace.endSection();

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed");
        inferenceInterface.feed(inputName, floatValues, 1, inputSize, inputSize, 3);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("run");
        inferenceInterface.run(outputNames, logStats);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("fetch");
        inferenceInterface.fetch(outputName, outputs);
        Trace.endSection();

        // Find the best classifications.
        PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(
                        3,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });
        for (int i = 0; i < outputs.length; ++i) {
            if (outputs[i] >= THRESHOLD) {
                InputStream is = null;
                try {
                    is = assetManager.open("images/"+labels.get(i).get("image_name"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Bitmap  resultBitmap = BitmapFactory.decodeStream(is);
                Item item = new Item(labels.get(i).get("display_name"),resultBitmap);
                pq.add(
                        new Recognition("" + i, labels.size() > i ? labels.get(i).get("image_name") : "unknown", outputs[i],null,item));
            }
        }
        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        Trace.endSection(); // "recognizeImage"
        return recognitions;
    }

    @Override
    public void enableStatLogging(boolean logStats) {
        this.logStats = logStats;
    }

    @Override
    public String getStatString() {
        return inferenceInterface.getStatString();
    }

    @Override
    public void close() {
        inferenceInterface.close();
    }



}
