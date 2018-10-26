package io.github.njohnsoncpe.thirdEyeClient;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;

class ConnectionThread extends Thread {

    private ConnectivityManager mConnMan;

    private final Socket mSocket;

    private final String mHost;

    private final int mPort;

    private volatile boolean mAbort = false;

    public ConnectionThread(final String host, final int port) {
        mHost = host;
        mPort = port;
        mSocket = new Socket();
    }

    @Override
    public void run() {
        final Socket s = mSocket;

        final long startTime = System.currentTimeMillis();

        String TAG = "ConnectionThread";
        try {
            // Now we can say that the service is started.
            //setStarted(true);

            // Connect to server.
            s.connect(new InetSocketAddress(mHost, mPort), 20000);

            Log.e(TAG, "Connection established to " + s.getInetAddress() + ":" + mPort);

            final DataOutputStream dos = new DataOutputStream(s.getOutputStream());
            final BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));

            // Send the login data.
            final JSONObject login = new JSONObject();

            // Send the login message.
            dos.write((login.toString() + "\r\n").getBytes());

            // Wait until we receive something from the server.
            String receivedMessage;
            while ((receivedMessage = in.readLine()) != null) {
                Log.e(TAG, "Received data: " + receivedMessage);
                //processMessagesFromServer(dos, receivedMessage);
            }

            if (!mAbort) {
                Log.e(TAG, "Server closed connection unexpectedly.");
            }
        } catch (final IOException e) {
            Log.e(TAG, "Unexpected I/O error.", e);
        } catch (final Exception e) {
            Log.e(TAG, "Exception occurred.", e);
        } finally {
            //setLoggedIn(false);
            //stopKeepAlives();

            if (mAbort) {
                Log.e(TAG, "Connection aborted, shutting down.");
            } else {
                try {
                    s.close();
                } catch (final IOException e) {
                    // Do nothing.
                }
            }
        }
    }

    /**
     * Sends the PING word to the server.
     *
     * @throws java.io.IOException    if an error occurs while writing to this stream.
     * @throws org.json.JSONException
     */
    public void sendKeepAlive(final Boolean forced) throws IOException, JSONException {
        final JSONObject ping = new JSONObject();

        mSocket.getOutputStream().write((ping.toString() + "\r\n").getBytes());
    }

    /**
     * Aborts the connection with the server.
     */
    public void abort(boolean manual) {
        mAbort = manual;

        try {
            // Close the output stream.
            mSocket.shutdownOutput();
        } catch (final IOException e) {
            // Do nothing.
        }

        try {
            // Close the input stream.
            mSocket.shutdownInput();
        } catch (final IOException e) {
            // Do nothing.
        }

        try {
            // Close the socket.
            mSocket.close();
        } catch (final IOException e) {
            // Do nothing.
        }

        while (true) {
            try {
                join();
                break;
            } catch (final InterruptedException e) {
                // Do nothing.
            }
        }
    }
}