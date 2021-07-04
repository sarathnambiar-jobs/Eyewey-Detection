/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nexart.eyewey_detection.currency;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import com.nexart.eyewey_detection.R;
import com.nexart.eyewey_detection.currency.env.BorderedText;
import com.nexart.eyewey_detection.currency.env.ImageUtils;
import com.nexart.eyewey_detection.currency.env.Logger;
import com.nexart.eyewey_detection.currency.tflite.Classifier;
import com.nexart.eyewey_detection.currency.tflite.Classifier.Device;
import com.nexart.eyewey_detection.currency.tflite.Classifier.Model;
import com.nexart.eyewey_detection.utils.MyApplication;

import java.io.IOException;
import java.util.List;

public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(2960, 1440);
    private static final float TEXT_SIZE_DIP = 10;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private long lastProcessingTimeMs;
    private Integer sensorOrientation;
    private Classifier classifier;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private BorderedText borderedText;
    private String lastObject = "";
    private static final String TAG = ClassifierActivity.class.getSimpleName();

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        recreateClassifier(getModel(), getDevice(), getNumThreads());
        if (classifier == null) {
            LOGGER.e("No classifier on preview!");
            return;
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap =
                Bitmap.createBitmap(
                        classifier.getImageSizeX(), classifier.getImageSizeY(), Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth,
                        previewHeight,
                        classifier.getImageSizeX(),
                        classifier.getImageSizeY(),
                        sensorOrientation,
                        MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
    }

    @Override
    protected void processImage() {
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        if (classifier != null) {
                            final long startTime = SystemClock.uptimeMillis();
                            final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
                            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                            LOGGER.v("Detect: %s", results);
                            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            for (Classifier.Recognition result : results) {
                                                final RectF location = result.getLocation();
                                                if (location != null && result.getConfidence() >= 0.65f) {
                                                    showResultsInBottomSheet(results);
                                                    showFrameInfo(previewWidth + "x" + previewHeight);
                                                    showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                                    showCameraResolution(canvas.getWidth() + "x" + canvas.getHeight());
                                                    showRotationInfo(String.valueOf(sensorOrientation));
                                                    showInference(lastProcessingTimeMs + "ms");
                                                    Log.e(TAG, "result: "+result.getTitle() );
                                                    //if (com.nexart.eyewey.core.module.objectdetection.CameraActivity.isSpeechEnabled) {
                                                    if (!lastObject.equals(result.getTitle())) {
                                                    MyApplication.speakerbox.unmute();
                                                    int confidence = (int) (result.getConfidence() * 100f);
                                                    //MyApplication.speakerbox.play("I am " + confidence + "%" + " sure that" + " this is " + result.getTitle());
                                                    Log.e(TAG, "run: " + confidence);
                                                    //predictionTV.setText(result.getTitle() + " : " + confidence + "%");
                                                    lastObject = result.getTitle();
                                                    }
                                                    //}
                                                }
                                            }
                                        }
                                    });
                        }
                        readyForNextImage();
                    }
                });
    }

    @Override
    protected void onInferenceConfigurationChanged() {
        if (croppedBitmap == null) {
            // Defer creation until we're getting camera frames.
            return;
        }
        final Device device = getDevice();
        final Model model = getModel();
        final int numThreads = getNumThreads();
        runInBackground(() -> recreateClassifier(model, device, numThreads));
    }

    private void recreateClassifier(Model model, Device device, int numThreads) {
        if (classifier != null) {
            LOGGER.d("Closing classifier.");
            classifier.close();
            classifier = null;
        }
        if (device == Device.GPU && model == Model.QUANTIZED) {
            LOGGER.d("Not creating classifier: GPU doesn't support quantized models.");
            runOnUiThread(
                    () -> {
                        Toast.makeText(this, "GPU does not yet supported quantized models.", Toast.LENGTH_LONG)
                                .show();
                    });
            return;
        }
        try {
            LOGGER.d(
                    "Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads);
            classifier = Classifier.create(this, model, device, numThreads);
        } catch (IOException e) {
            LOGGER.e(e, "Failed to create classifier.");
        }
    }
}
