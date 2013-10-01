package piuk.blockchain.android.ui;

import com.dm.zbar.android.scanner.ZBarConstants;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.io.IOException;

import piuk.blockchain.android.R;
import piuk.blockchain.android.ui.view.SimpleCameraPreview;


public class QrScannerActivity extends Activity implements Camera.PreviewCallback, ZBarConstants, SurfaceHolder.Callback
{

	private static final String TAG = "QRScannerActivity";
	private SimpleCameraPreview mPreview;
	private Camera mCamera;
	private ImageScanner mScanner;
	private Handler mAutoFocusHandler;
	private boolean mPreviewing = true;

	static {
		System.loadLibrary("iconv");
	}

    private ViewGroup mCameraHolder;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(!isCameraAvailable()) {
			// Cancel request if there is no rear-facing camera.
			cancelRequest();
			return;
		}

		// Hide the window title.
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.scanner_activity);
        mCameraHolder = (ViewGroup) findViewById(R.id.camera_holder);

		mAutoFocusHandler = new Handler();

		// Create and configure the ImageScanner;
		setupScanner();

	}

    @Override
    protected void onResume() {
        super.onResume();
        mCamera = getCameraInstance();
        mCamera.setPreviewCallback(this);
		mPreview = new SimpleCameraPreview(this, mCamera);
        SurfaceHolder mSurfaceHolder = mPreview.getHolder();
        mSurfaceHolder.addCallback(this);

        mCameraHolder.removeAllViews();
        mCameraHolder.addView(mPreview, 0,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.cancelAutoFocus();
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();

            mPreviewing = false;
            mCamera = null;

        }
    }

    public void setupScanner() {
		mScanner = new ImageScanner();
		mScanner.setConfig(0, Config.X_DENSITY, 3);
		mScanner.setConfig(0, Config.Y_DENSITY, 3);

		int[] symbols = getIntent().getIntArrayExtra(SCAN_MODES);
		if (symbols != null) {
			mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
			for (int symbol : symbols) {
				mScanner.setConfig(symbol, Config.ENABLE, 1);
			}
		}
	}

    private Camera getCameraInstance() {
        Camera camera = null;
        try {
            camera = Camera.open();
            camera.lock();
            camera.setDisplayOrientation(90);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            camera.setParameters(parameters);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return camera;
    }



	public boolean isCameraAvailable() {
		PackageManager pm = getPackageManager();
		return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
	}

	public void cancelRequest() {
		Intent dataIntent = new Intent();
		dataIntent.putExtra(ERROR_INFO, "Camera unavailable");
		setResult(Activity.RESULT_CANCELED, dataIntent);
		finish();
	}

	public void onPreviewFrame(byte[] data, Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		Camera.Size size = parameters.getPreviewSize();

		Image barcode = new Image(size.width, size.height, "Y800");
		barcode.setData(data);

		int result = mScanner.scanImage(barcode);

		if (result != 0) {
			mCamera.cancelAutoFocus();
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mPreviewing = false;
			SymbolSet syms = mScanner.getResults();
			for (Symbol sym : syms) {
				String symData = sym.getData();
				if (!TextUtils.isEmpty(symData)) {
					Intent dataIntent = new Intent();
					dataIntent.putExtra(SCAN_RESULT, symData);
					dataIntent.putExtra(SCAN_RESULT_TYPE, sym.getType());
					setResult(Activity.RESULT_OK, dataIntent);
					finish();
					break;
				}
			}
		}
	}
	private Runnable doAutoFocus = new Runnable() {
		public void run() {
			if(mCamera != null && mPreviewing) {
				mCamera.autoFocus(autoFocusCB);
			}
		}
	};

	// Mimic continuous auto-focusing
	Camera.AutoFocusCallback autoFocusCB = new Camera.AutoFocusCallback() {
		public void onAutoFocus(boolean success, Camera camera) {
			mAutoFocusHandler.postDelayed(doAutoFocus, 1000);
		}
	};



    @Override
    public void surfaceChanged(SurfaceHolder holder, int i, int i2, int i3) {
        if (holder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        try {
            mCamera.setPreviewCallback(this);
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            mPreviewing = true;
            if (hasAutoFocus()) {
                mCamera.autoFocus(autoFocusCB);
            }

        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);

            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    private boolean hasAutoFocus() {
        return getPackageManager().hasSystemFeature("android.hardware.camera.autofocus");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            if (hasAutoFocus()) {
                mCamera.cancelAutoFocus();
            }
            mCamera.stopPreview();
        }
    }
}

