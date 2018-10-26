package io.github.njohnsoncpe.thirdEyeClient;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.ReceiverCallNotAllowedException;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.Log;
import android.util.TimingLogger;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import io.github.njohnsoncpe.thirdEyeClient.TCPClient.TCPClient;
import io.github.njohnsoncpe.thirdEyeClient.TCPClient.TCPClientState;
import io.github.njohnsoncpe.thirdEyeClient.TCPClient.TCPEvent;
import org.opencv.*;
import org.opencv.android.OpenCVLoader;

public class MainActivity extends Activity implements SurfaceHolder.Callback, RtspServer.CallbackListener, Session.Callback, Observer{

    private final static String TAG = "MainActivity";
    private SurfaceView mSurfaceView, boxesSurfaceView;
    private Session mSession;
    private Paint boxPainter;
    private TextPaint labelPainter;
    private TCPClient client;

    public static final int REQUEST_PERMISSION = 200;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_PERMISSION);
        }

        Button getJson = findViewById(R.id.getJson);

        getJson.setOnClickListener(new View.OnClickListener() {
           public void onClick(View v) {
                   ArrayList<JSONObject> temp = client.getDrawData();
                   if(temp != null){
                       int i = 0;
                       while (i < temp.size()) {
                           Snackbar.make(mSurfaceView, temp.get(i)/*.getJSONArray("object_location").get(0)*/.toString(), Snackbar.LENGTH_LONG)
                                   .show();
                           i++;
                       }

                   }
           }
        });

        Switch offloadToggle = findViewById(R.id.offload_toggle);
        offloadToggle.setChecked(true);

        offloadToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    Toast.makeText(MainActivity.this, "Offloading Enabled", Toast.LENGTH_SHORT).show();


                    if(client != null){
                        client.connect();
                    }


                }else{
                    Toast.makeText(MainActivity.this, "Offloading Disabled", Toast.LENGTH_SHORT).show();
                    if(client != null){
                        client.sendMessage("GOODBYE");
                        client.disconnect();
                        mSession.stop();
                        mSession.release();
                    }
                }
            }
        });


        offloadToggle = findViewById(R.id.offload_toggle);
        mSurfaceView = findViewById(R.id.surface);
        boxesSurfaceView = findViewById(R.id.boxDrawSurface);

        //Create Box Painter
        boxPainter = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPainter.setARGB(255,255,0,0);
        boxPainter.setStyle(Paint.Style.STROKE);
        boxPainter.setStrokeWidth(10);

        //Create Label TextPainter
        labelPainter = new TextPaint();
        labelPainter.setAntiAlias(true);
        labelPainter.setTextSize(16*getResources().getDisplayMetrics().density);
        labelPainter.setColor(Color.WHITE);


//        double avgRTT = client.getLatency(TCPServerAddr, 20, 0.2);
//        Log.e(TAG, "SERVER RTT: " + avgRTT + " ms");

        // Sets the port of the RTSP server to 1234
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(1234));
        editor.apply();


    }

    @Override
    public void onResume() {
        Log.e(TAG, "Application Resumed");
        super.onResume();
        //Start DRP Client
        int TCPServerPort = 20004;
        String TCPServerAddr = "192.168.0.156";
        client = new TCPClient(TCPServerAddr, TCPServerPort);
        client.addObserver(this);
        client.connect();
        // Configures the SessionBuilder
        mSession = SessionBuilder.getInstance()
                .setCallback(this)
                .setSurfaceView(mSurfaceView)
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(640,480,10,5000))
                .build();

        mSession.getVideoTrack().setStreamingMethod(MediaStream.MODE_MEDIACODEC_API_2);

        mSurfaceView.getHolder().addCallback(this);
        mSurfaceView.setAspectRatioMode(net.majorkernelpanic.streaming.gl.SurfaceView.ASPECT_RATIO_PREVIEW);

        boxesSurfaceView.getHolder().addCallback(this);
        boxesSurfaceView.setAspectRatioMode(net.majorkernelpanic.streaming.gl.SurfaceView.ASPECT_RATIO_PREVIEW);
        boxesSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);

        // Starts the RTSP server
        getApplicationContext().startService(new Intent(getApplicationContext(), RtspServer.class));
        mSession.startPreview(); //camera preview on phone surface
        mSession.start();

    }

    @Override
    protected void onPause() {
        Log.e(TAG, "Application Paused");
        super.onPause();
        mSession.stopPreview();
        mSession.stop();
        getApplicationContext().stopService(new Intent(getApplicationContext(), RtspServer.class));
        if(client.getState() == TCPClientState.CONNECTED){
            client.sendMessage("GOODBYE");
        }else {
            Log.e(TAG, "Client is not connected so disconnection could not be completed");
        }
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "Application Stopped");
        super.onStop();
        client.disconnect();

    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "Application Destroyed");
        super.onDestroy();

    }

    //RTSP Session Handling Methods
    @Override
    public void onError(RtspServer server, Exception e, int error) {
        Log.e("Server", e.toString());
    }

    @Override
    public void onMessage(RtspServer server, int message) {
        Log.e("Server", "unkown message");
    }

    @Override
    public void onBitrateUpdate(long bitrate) {
//`        Log.d(TAG, "RTSP Event: Bitrate Updated to " + bitrate);
    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        Log.e(TAG, "RTSP Event: Session Error. Info: " + reason + " " + streamType);
        e.printStackTrace();
    }

    @Override
    public void onPreviewStarted() {
        Log.e(TAG, "Preview Started");
    }

    @Override
    public void onSessionConfigured() {
        Log.e(TAG, "Session Configured");
    }

    @Override
    public void onSessionStarted() {
        client.sendMessage("READY");
        Log.e(TAG, "Session Started");
    }

    @Override
    public void onSessionStopped() {
        Log.e(TAG, "Session Stopped");
    }


    //SurfaceHolder Callback Methods
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "Surface Created");
        //tryDrawing(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //tryDrawing(holder);
        Log.d(TAG, "surfaceChanged: " + client.hasChanged());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e(TAG, "Surface Destroyed");
    }

    //TCP Client Methods
    @Override
    public void update(Observable o, Object arg) {
        TCPEvent event = (TCPEvent)arg;
//        Log.i(TAG, "update: " + event.getEventType() );
        switch (event.getEventType()){
            case DATA_RECEIVED:
                TimingLogger timingLogger = new TimingLogger("TIMER", "TCP update: ");
                timingLogger.addSplit("Total Time to execute Draw Logic");
//                Log.i(TAG, "DATA RECEIVED: " + event.getPayload());
                try {
                    ArrayList<JSONObject> temp = client.getDrawData();
//                    Log.i(TAG, "DRAW QUEUE SIZE: "+ client.getDrawQueueSize());
                    if(temp != null){
//                        Log.i(TAG, "Drawing New Detection Data...");
                        //timingLogger.addSplit("tryDrawing()");
                        tryDrawing(boxesSurfaceView.getHolder(), temp);

                    }else{
//                        Log.i(TAG, "No Queued Detection Data!");
                        //timingLogger.addSplit("tryErasing()");
                        tryErasing(boxesSurfaceView.getHolder());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                timingLogger.dumpToLog();
                break;

            case EMPTY_PACKET:
                Log.v(TAG, "No detection Data in frame! Clearing...");
                tryErasing(boxesSurfaceView.getHolder());
            case MESSAGE_RECEIVED:
                Log.v(TAG, "MESSAGE RECEIVED: " + event.getPayload());
                break;
            case CONNECTION_FAILED:
                Log.v(TAG, "Connection Failed");
//                this.client.reconnect()
                break;
            case CONNECTION_ESTABLISHED:
                Log.v(TAG, "Connected!");
                if(client.getState() == TCPClientState.CONNECTED){
                    client.sendMessage("HELLO");
                }
                break;
            case DISCONNECTED:
                Log.e(TAG, "Client Disconnected");
                mSession.release();
                mSurfaceView.getHolder().removeCallback(this);
                boxesSurfaceView.getHolder().removeCallback(this);

        }
    }

    private void tryErasing(SurfaceHolder holder){
        Log.v(TAG, "Trying to Erase...");
        Canvas canvas = holder.lockCanvas();
        if(canvas == null){
            Log.e(TAG, "Cannot draw onto the canvas as it's null");
        } else{
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        holder.unlockCanvasAndPost(canvas);
    }

    private void tryDrawing(SurfaceHolder holder, ArrayList<JSONObject> someBoxArray) throws JSONException {
        Log.i(TAG, "Trying to draw...");
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            Log.e(TAG, "Cannot draw onto the canvas as it's null");
        } else if(someBoxArray != null){
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            int i = 0;
            while (i < someBoxArray.size()){
                JSONArray location = someBoxArray.get(i).getJSONArray("object_location");
                JSONArray label = someBoxArray.get(i).getJSONArray("object_data");
                //This "1,0,3,2" ordering is due to the 90 deg image transform that is performed at the server. TODO: Get rid of this
                drawAndAnnotate(canvas, ((double)location.get(1)*1080), ((double)location.get(0)*1731), ((double)location.get(3)*1080), ((double)location.get(2)*1731), label.toString());
                i++;
            }
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawAndAnnotate(final Canvas canvas, double x1, double y1, double x2, double y2, String label) {
        Log.v(TAG, "Drawing...");
        canvas.drawRect((float)x1,(float)y1, (float)x2, (float)y2, boxPainter);
        canvas.drawText(label, (float)x1,(float)(y1 - 10), labelPainter);
    }
}
//endregion
