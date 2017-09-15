
package com.kviation.sample.orientation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class Orientation implements SensorEventListener {

    public interface Listener {
        void onOrientationChanged(float pitch, float roll);
    }

    private static final int SENSOR_DELAY_MICROS = 50 * 1000; // 50ms
    private final WindowManager mWindowManager;
    private final SensorManager mSensorManager;

    private float timestamp;

    @Nullable
    private final Sensor mGyroscopeSensor;
    private Sensor mAccelerometer;

    private int mLastAccuracy;
    private Listener mListener;


    public Orientation(Activity activity) {
        mWindowManager = activity.getWindow().getWindowManager();
        mSensorManager = (SensorManager) activity.getSystemService(Activity.SENSOR_SERVICE);

        // Can be null if the sensor hardware is not available
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void startListening(Listener listener) {
        if (mListener == listener) {
            return;
        }
        mListener = listener;
        if (mGyroscopeSensor == null) {
            LogUtil.w("Gyroscope vector sensor not available; will not provide orientation data.");
            return;
        }
        mSensorManager.registerListener(this, mGyroscopeSensor, SENSOR_DELAY_MICROS);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stopListening() {
        mSensorManager.unregisterListener(this);
        mListener = null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (mLastAccuracy != accuracy) {
            mLastAccuracy = accuracy;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mListener == null) {
            return;
        }
        if (mLastAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return;
        }
        if (event.sensor == mGyroscopeSensor) {
            updateOrientation(event.values, event.timestamp);
        }
        if (event.sensor == mAccelerometer) {
            writeAccelero(event.values, event.timestamp);
        }
    }

    private void writeAccelero(float[] acclVals, long timestamp) {
        System.out.println("acc, " + acclVals[0] + ", " + acclVals[1] + ", " + acclVals[2] );
        writeFile(timestamp + ", " + acclVals[0] + ", " + acclVals[1] + ", " + acclVals[2], "acc");
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void updateOrientation(float[] gyroVals, long timestamp) {
        float[] deltaRotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, gyroVals);

        final int worldAxisForDeviceAxisX;
        final int worldAxisForDeviceAxisY;

        // Remap the axes as if the device screen was the instrument panel,
        // and adjust the rotation matrix for the device orientation.
        switch (mWindowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                break;
            case Surface.ROTATION_90:
                worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                break;
            case Surface.ROTATION_270:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                break;
        }

        float[] adjustedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(deltaRotationMatrix, worldAxisForDeviceAxisX, worldAxisForDeviceAxisY, adjustedRotationMatrix);

        // Transform rotation matrix into azimuth/pitch/roll
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);

        // Convert radians to degrees
        float pitch = orientation[1] * -57;
        float roll = orientation[2] * -57;

        mListener.onOrientationChanged(pitch, roll);

        System.out.println("gyro, " + gyroVals[0] + ", " + gyroVals[1] + ", " + gyroVals[2] );
        writeFile(timestamp + ", " + gyroVals[0] + ", " + gyroVals[1] + ", " + gyroVals[2], "gyro");
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }



    public void writeFile(String string, String filename) {
        File sdcard = Environment.getExternalStorageDirectory();
        // to this path add a new directory path
        File dir = new File(sdcard.getAbsolutePath() + "/wardi/");
        // create this directory if not already created
        dir.mkdir();
        // create the file in which we will write the contents
        File file = new File(dir, filename + ".txt");
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file, true);
            string = string + "\n";
            os.write(string.getBytes());
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
