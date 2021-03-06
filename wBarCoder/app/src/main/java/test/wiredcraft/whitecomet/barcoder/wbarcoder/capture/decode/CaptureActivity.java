package test.wiredcraft.whitecomet.barcoder.wbarcoder.capture.decode;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import test.wiredcraft.whitecomet.barcoder.wbarcoder.capture.PreferencesConstants;
import test.wiredcraft.whitecomet.barcoder.wbarcoder.capture.camera.CameraManager;

/**
 * An abstract class to help setup a scan activity.
 * @author shiyinayuriko
 */
public abstract class CaptureActivity extends Activity implements SurfaceHolder.Callback{
    private static final String TAG = CaptureActivity.class.getSimpleName();

    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    /**
     * To manager the camera, include the flash light.
     * @see CameraManager
     */
    protected CameraManager cameraManager;
    private CaptureActivityHandler handler;
    /**
     * To beep a sound and vibrate.Usually used when finishing a scanning.
     * @see BeepManager
     */
    protected BeepManager beepManager;
    private AmbientLightManager ambientLightManager;
    /**
     * To stop or resume the scanning.
     * @see InactivityTimer
     */
    protected InactivityTimer inactivityTimer;
    private Set<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType,?> decodeHints = null;
    private String characterSet;

    /**
     * Finish this method so that the capture activity can get the ViewfinderView inflate in its children.
     * @return
     */
    protected abstract ViewfinderView getViewFinderView();

    /**
     * Finish this method so that the capture activity can get the SurfaceView inflate in its children.
     * @return
     */
    protected abstract SurfaceView getSurfaceView();

    /**
     * Finish it and when a barcode scan success, it will be called.
     * @param rawResult The info in the barcode captured
     * @param barcode The captured barcode bitmap.
     * @param scaleFactor
     */
    public abstract void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hasSurface =false;
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        cameraManager =  new CameraManager(getApplication());

        viewfinderView = getViewFinderView();
        viewfinderView.setCameraManager(cameraManager);

        handler = null;

        if (PreferencesConstants.KEY_DISABLE_AUTO_ORIENTATION) {
            setRequestedOrientation(getCurrentOrientation());
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        beepManager.updatePrefs();
        ambientLightManager.start(cameraManager);

        inactivityTimer.onResume();


        decodeFormats = null;
//        decodeFormats = DecodeFormatManager.PRODUCT_FORMATS;
        characterSet = null;

        SurfaceView surfaceView = getSurfaceView();
        SurfaceHolder surfaceHolder = surfaceView.getHolder();

        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
        }
    }

    /**
     * Get the Current Orientation
     * @return an Enum of ActivityInfo such as ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
     * @see ActivityInfo
     */
    protected int getCurrentOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            switch (rotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                default:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            }
        } else {
            switch (rotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_270:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                default:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            }
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        ambientLightManager.stop();
        beepManager.close();
        cameraManager.closeDriver();
        if (!hasSurface) {
            SurfaceView surfaceView = getSurfaceView();
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }

        if (cameraManager.isOpen()) {
            Log.w(TAG,"initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            if (handler == null) {
                handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unexpected error initializing camera", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG,"*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface =true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface =false;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }
}
