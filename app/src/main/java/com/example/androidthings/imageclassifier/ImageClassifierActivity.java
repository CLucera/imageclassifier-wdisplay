/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.imageclassifier;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.IOException;

public class ImageClassifierActivity extends Activity
{
    private static final String TAG = "ImageClassifierActivity";

    private String[] labels;
    private TensorFlowInferenceInterface inferenceInterface;
    private AlphanumericDisplay mDisplay;

    // ADD ARTIFICIAL INTELLIGENCE
    private Button buttonC;
    private Button buttonA;
    private boolean mProcessing;

    // ADD CAMERA SUPPORT
    private CameraHandler mCameraHandler;
    private ImagePreprocessor mImagePreprocessor;
    private String resultDisplay = "";
    private int currentPosition = 0;
    private Handler displayHandler;
    private Runnable displayRunnable;

    /**
     * Initialize the classifier that will be used to process images.
     */
    private void initClassifier()
    {
        this.inferenceInterface = new TensorFlowInferenceInterface(
                getAssets(), Helper.MODEL_FILE);
        this.labels = Helper.readLabels(this);
    }

    /**
     * Clean up the resources used by the classifier.
     */
    private void destroyClassifier()
    {
        inferenceInterface.close();
    }

    //DISPLAY
    private void initDisplay()
    {
        try {
            mDisplay = RainbowHat.openDisplay();
            mDisplay.setEnabled(true);
            mDisplay.clear();

            mDisplay.display("RDY");
            Log.d(TAG, "Initialized I2C Display");
            return;
        } catch (IOException e) {
            Log.e(TAG, "Error initializing display", e);
            Log.d(TAG, "Display disabled");
            mDisplay = null;
        }
    }

    private void destroyDisplay()
    {
        if (mDisplay != null) {
            try {
                mDisplay.clear();
                mDisplay.setEnabled(false);
                mDisplay.close();
            } catch (IOException e) {
                Log.e(TAG, "Error disabling display", e);
            } finally {
                mDisplay = null;
            }
        }
    }


    // --------------------------------------------------------------------------------------
    // NOTE: The normal codelab flow won't require you to change anything below this line,
    // although you are encouraged to read and understand it.

    /**
     * Process an image and identify what is in it. When done, the method
     * {@link #onPhotoRecognitionReady(String[])} must be called with the results of
     * the image recognition process.
     *
     * @param image Bitmap containing the image to be classified. The image can be
     *              of any size, but preprocessing might occur to resize it to the
     *              format expected by the classification process, which can be time
     *              and power consuming.
     */
    private void doRecognize(Bitmap image)
    {
        float[] pixels = Helper.getPixels(image);

        // Feed the pixels of the image into the
        // TensorFlow Neural Network
        inferenceInterface.feed(Helper.INPUT_NAME, pixels,
                Helper.NETWORK_STRUCTURE);

        // Run the TensorFlow Neural Network with the provided input
        inferenceInterface.run(Helper.OUTPUT_NAMES);

        // Extract the output from the neural network back
        // into an array of confidence per category
        float[] outputs = new float[Helper.NUM_CLASSES];
        inferenceInterface.fetch(Helper.OUTPUT_NAME, outputs);

        // Send to the callback the results with the highest
        // confidence and their labels
        onPhotoRecognitionReady(Helper.getBestResults(outputs, labels));
    }

    /**
     * Initialize the camera that will be used to capture images.
     */
    private void initCamera()
    {
        // ADD CAMERA SUPPORT
        mImagePreprocessor = new ImagePreprocessor();
        mCameraHandler = CameraHandler.getInstance();

        Handler threadLooper = new Handler(getMainLooper());


        mCameraHandler.initializeCamera( this, threadLooper,
                new ImageReader.OnImageAvailableListener()
                {
                    @Override
                    public void onImageAvailable(ImageReader imageReader)
                    {
                        Bitmap bitmap = mImagePreprocessor.preprocessImage(imageReader.acquireNextImage());
                        onPhotoReady(bitmap);
                    }
                });
    }

    /**
     * Clean up resources used by the camera.
     */
    private void closeCamera()
    {
        // ADD CAMERA SUPPORT
        mCameraHandler.shutDown();
        mProcessing = false;
    }

    /**
     * Load the image that will be used in the classification process.
     * When done, the method {@link #onPhotoReady(Bitmap)} must be called with the image.
     */
    private void loadPhoto()
    {
        // ADD CAMERA SUPPORT
        mCameraHandler.takePicture();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initCamera();
        initClassifier();
        initButton();
        initDisplay();
        Log.d(TAG, "READY");
    }

    /**
     * Register a GPIO button that, when clicked, will generate the {@link KeyEvent#KEYCODE_ENTER}
     * key, to be handled by {@link #onKeyUp(int, KeyEvent)} just like any regular keyboard
     * event.
     * <p>
     * If there's no button connected to the board, the doRecognize can still be triggered by
     * sending key events using a USB keyboard or `adb shell input keyevent 66`.
     */
    private void initButton()
    {
        try {
            buttonA = RainbowHat.openButtonA();
            buttonA.setOnButtonEventListener(new Button.OnButtonEventListener()
            {
                @Override
                public void onButtonEvent(Button button, boolean pressed)
                {
//                    if(pressed) {
//                        closeCamera();
//                        initCamera();
//                    }
                }
            });

            buttonC = RainbowHat.openButtonC();
            buttonC.setOnButtonEventListener(new Button.OnButtonEventListener()
            {
                @Override
                public void onButtonEvent(Button button, boolean pressed)
                {
                    if( pressed) {
                        if (mProcessing) {
                            Log.e(TAG, "Still processing, please wait");
                            return;
                        }
                        Log.d(TAG, "Running photo recognition");
                        mProcessing = true;
                        loadPhoto();
                    }
                }
            });
        } catch (IOException e) {
            Log.w(TAG, "Cannot find button. Ignoring push button. Use a keyboard instead.", e);
        }
    }

    private Bitmap getStaticBitmap()
    {
        Log.d(TAG, "Using sample photo in res/drawable/sampledog_224x224.png");
        return BitmapFactory.decodeResource(this.getResources(), R.drawable.sampledog_224x224);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mProcessing) {
                Log.e(TAG, "Still processing, please wait");
                return true;
            }
            Log.d(TAG, "Running photo recognition");
            mProcessing = true;
            loadPhoto();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void onPhotoReady(Bitmap bitmap)
    {

        ImageView imageView = (ImageView) findViewById(R.id.imageView);
        if(imageView != null) {
            imageView.setImageBitmap(bitmap);
        }
        doRecognize(bitmap);
    }

    private void onPhotoRecognitionReady(String[] results)
    {
        String resultString = Helper.formatResults(results);
        Log.d(TAG, "RESULTS: " + resultString);

        try {
            mDisplay.clear();
            resultDisplay = "    " + resultString.toUpperCase() + "    ";
            if (displayHandler != null) {
                displayHandler.removeCallbacks(displayRunnable);
            }
            if (resultDisplay.length() > 4) {
                currentPosition = 0;
                displayHandler = new Handler();
                displayRunnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        currentPosition += 1;
                        currentPosition = currentPosition % resultDisplay.length();
                        try {
                            mDisplay.display(resultDisplay.substring(currentPosition, resultDisplay.length() -1));

                            displayHandler.postDelayed(this, 200);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
            mDisplay.display(resultDisplay.substring(currentPosition, Math.max(currentPosition + 4, resultDisplay.length())));
            } else {
                mDisplay.display(resultDisplay);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        displayHandler.postDelayed(displayRunnable, 200);


        mProcessing = false;
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        try {
            destroyClassifier();
        } catch (Throwable t) {
            // close quietly
        }
        try {
            closeCamera();
        } catch (Throwable t) {
            // close quietly
        }
        try {
            if (buttonC != null) buttonC.close();
            if (buttonA != null) buttonA.close();
        } catch (Throwable t) {
            // close quietly
        }
    }
}
