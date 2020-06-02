package com.ielson.djiBote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import java.util.Timer;
import java.util.TimerTask;

import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.midware.media.DJIVideoDecoder;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;


import org.ros.EnvironmentVariables;
import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;
import com.dji.videostreamdecodingsample.media.NativeHelper;

public class MainActivity extends RosActivity implements TextureView.SurfaceTextureListener, View.OnClickListener, DJIVideoStreamDecoder.IYuvDataListener {

    protected SurfaceView mVideoSurface = null;
    private SurfaceHolder mVideoSurfaceHolder;
    private Button mLandBtn, mTakeOffBtn;
    private static final String TAG = MainActivity.class.getName();
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    private static BaseProduct product;
    public static FlightController mFlightController;
    private OnScreenJoystick mScreenJoystickRight;
    private OnScreenJoystick mScreenJoystickLeft;

    public static TextView mTextView;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    private TextureView videostreamPreviewTtView;

    private HandlerThread backgroundHandlerThread;
    public Handler backgroundHandler;

    protected DJICodecManager mCodecManager = null;

    private Talker talker;



    public MainActivity() {
        // The RosActivity constructor configures the notification title and ticker
        // messages.
        super("DJIBote", "Communication DJI-ROS");
        // If you know the IP/Port of the ROS Master, you can set it as follows and avoid having the Master Chooser activity:
        //super("DJI-Ros Driver Activity", "DJI-Ros Driver Activity", URI.create("http://10.42.0.1:11311")
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NativeHelper.getInstance().init();
        Log.e(TAG, "native helper inited");

        setContentView(R.layout.activity_main);

        backgroundHandlerThread = new HandlerThread("background handler thread");
        Log.d(TAG, "background handler created");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());

        initUI();
        initPreviewer();


        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {
            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                Log.d(TAG, "camera recv video data size: " + size);
                //if (mCodecManager != null) {
                //    mCodecManager.sendDataToDecoder(videoBuffer, size);
                //}
                DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
            }
        };
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        // At this point, the user has already been prompted to either enter the URI
        // of a master to use or to start a master locally.
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(getRosHostname());
        nodeConfiguration.setMasterUri(getMasterUri());
        Log.d("Master URI ", getMasterUri().toString());
        Log.d("ROS HOSTNAME", EnvironmentVariables.ROS_HOSTNAME);
        Log.d("ROS IP", EnvironmentVariables.ROS_IP);
        Log.d("ROS MASTER URI", EnvironmentVariables.ROS_MASTER_URI);
        Log.d("ROS HOSTNAME", EnvironmentVariables.ROS_ROOT);
        //Log.d("Node name", nodeConfiguration.getNodeName().toString());





        nodeMainExecutor.execute(talker, nodeConfiguration);
        //nodeMainExecutor.execute(rosTextView, nodeConfiguration);
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightController();
    }
    private void initFlightController() {


        talker = new Talker("position");
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();

            }
        }

    }

    protected void onProductChange() {
        initPreviewer();
    }

    private void initPreviewer() {

        product = ConnectionActivity.mProduct;
        if (product == null || !product.isConnected()) {
            Toast.makeText(this, getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
        } else {
            DJIVideoStreamDecoder.getInstance().resume();
            Toast.makeText(this, "Video Decoder Resumed", Toast.LENGTH_LONG).show();
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
            }
        }

        Log.d(TAG, "onInitPreviewer");
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "SurfaceTextureAvailable");
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) mCodecManager.cleanSurface();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        });
    }
    private void uninitPreviewer() {
        Camera camera = product.getCamera();
        if (camera != null){
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
            Toast.makeText(this, "Video Feed paused", Toast.LENGTH_SHORT).show();

        }
    }


    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        DJIVideoStreamDecoder.getInstance().resume();
        initPreviewer();
        initFlightController();
        onProductChange();
        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }


    class SendVirtualStickDataTask extends TimerTask {
        @Override
        public void run() {
            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                            }
                        }
                );
            }
        }
    }

    @Override
    public void onPause() {
        DJIVideoStreamDecoder.getInstance().stop();
        super.onPause();
    }
    @Override
    public void onStop() {
        super.onStop();
    }
    public void onReturn(View view){
        this.finish();
    }
    @Override
    protected void onDestroy() {
        DJIVideoStreamDecoder.getInstance().destroy();
        NativeHelper.getInstance().release();
        super.onDestroy();
    }
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        return false;
    }
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }




    @Override
    public void onYuvDataReceived(byte[] yuvFrame, int width, int height) {
        Log.e(TAG, "YUV");
        //In this demo, we test the YUV data by saving it into JPG files.
        if (DJIVideoStreamDecoder.getInstance().frameIndex % 30 == 0) {
            byte[] y = new byte[width * height];
            byte[] u = new byte[width * height / 4];
            byte[] v = new byte[width * height / 4];
            byte[] nu = new byte[width * height / 4]; //
            byte[] nv = new byte[width * height / 4];
            System.arraycopy(yuvFrame, 0, y, 0, y.length);
            for (int i = 0; i < u.length; i++) {
                v[i] = yuvFrame[y.length + 2 * i];
                u[i] = yuvFrame[y.length + 2 * i + 1];
            }
            int uvWidth = width / 2;
            int uvHeight = height / 2;
            for (int j = 0; j < uvWidth / 2; j++) {
                for (int i = 0; i < uvHeight / 2; i++) {
                    byte uSample1 = u[i * uvWidth + j];
                    byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
                    byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
                    byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
                    nu[2 * (i * uvWidth + j)] = uSample1;
                    nu[2 * (i * uvWidth + j) + 1] = uSample1;
                    nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
                    nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
                    nv[2 * (i * uvWidth + j)] = vSample1;
                    nv[2 * (i * uvWidth + j) + 1] = vSample1;
                    nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
                    nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
                }
            }
            //nv21test
            byte[] bytes = new byte[yuvFrame.length];
            System.arraycopy(y, 0, bytes, 0, y.length);
            for (int i = 0; i < u.length; i++) {
                bytes[y.length + (i * 2)] = nv[i];
                bytes[y.length + (i * 2) + 1] = nu[i];
            }
            Log.d(TAG,
                    "onYuvDataReceived: frame index: "
                            + DJIVideoStreamDecoder.getInstance().frameIndex
                            + ",array length: "
                            + bytes.length);
            //screenShot(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot");
        }
    }



    private void initUI() {
        // init mVideoSurface
        Log.d(TAG, "onInitUI");
        mVideoSurface = (SurfaceView) findViewById(R.id.video_previewer_surface);
        mTakeOffBtn = (Button) findViewById(R.id.btn_take_off);
        mLandBtn = (Button) findViewById(R.id.btn_land);
        //mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        //mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);
        mTextView = (TextView) findViewById(R.id.flightControllerData_tv);
        mVideoSurfaceHolder = mVideoSurface.getHolder();

        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        mVideoSurface.setVisibility(View.VISIBLE);
        videostreamPreviewTtView.setVisibility(View.GONE);
        mVideoSurfaceHolder.addCallback(new SurfaceHolder.Callback(){
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "Surface created");
                DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), mVideoSurfaceHolder.getSurface());
                Log.d(TAG, "after DJIVideoDecoder Init");
                DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
                Log.d(TAG, "after set yuvDataListener");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });



        mTakeOffBtn.setOnClickListener(this);
        mLandBtn.setOnClickListener(this);

        //TODO RECOLOCAR JOYSTICKS
        /*
        mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener(){
            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }
                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }
                float pitchJoyControlMaxSpeed = 10;
                float rollJoyControlMaxSpeed = 10;
                mPitch = (float)(pitchJoyControlMaxSpeed * pX);
                mRoll = (float)(rollJoyControlMaxSpeed * pY);
                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                }
            }
        });
        mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener() {
            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }
                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 2;
                float yawJoyControlMaxSpeed = 30;
                mYaw = (float)(yawJoyControlMaxSpeed * pX);
                mThrottle = (float)(verticalJoyControlMaxSpeed * pY);
                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
                }
            }
        });

         */
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_take_off:
                Toast.makeText(this, "Take off pressed", Toast.LENGTH_SHORT).show();
                if (mFlightController != null){
                    Toast.makeText(this, "Flight controller not null", Toast.LENGTH_SHORT).show();
                    mFlightController.startTakeoff(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        Toast.makeText(MainActivity.this, djiError.getDescription(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Takeoff success", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                    );
                }
                break;
            case R.id.btn_land:
                Toast.makeText(this, "Land pressed", Toast.LENGTH_SHORT).show();
                if (mFlightController != null){
                    mFlightController.startLanding(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        Toast.makeText(MainActivity.this, djiError.getDescription(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Start Landing", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                    );
                }
                break;
            default:
                break;
        }
    }

}
