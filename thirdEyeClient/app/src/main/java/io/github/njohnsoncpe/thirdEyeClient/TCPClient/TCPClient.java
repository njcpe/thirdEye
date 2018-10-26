package io.github.njohnsoncpe.thirdEyeClient.TCPClient;

import android.util.Log;
import android.util.TimingLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Observable;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
//import net.razorvine.pickle.*;
//
//import io.github.njohnsoncpe.stream.DetectionData.DetectionBox;

public class TCPClient extends Observable {
    private static final String TAG = "TCPClient";
    private String message = "";
    private String address;
    private Integer port;
    int maxDrawObjects = 4;

    private TCPClientState state = TCPClientState.DISCONNECTED;

    private PrintWriter bufferOut;

    private BufferedReader bufferIn;

    private Socket socket;
    //KEEP THIS for when Networking gets faster.

    private Queue<ArrayList> drawQueue = new LinkedList<>();
    public TCPClient() {  }

    public TCPClient(String address, int port) {
        this.address = address;
        this.port = port;

    }


    /**
     * Returns the latency to a given server in mili-seconds by issuing a ping command.
     * system will issue NUMBER_OF_PACKTETS ICMP Echo Request packet each having size of 56 bytes
     * every second, and returns the avg latency of them.
     * Returns 0 when there is no connection
     * @param ipAddress
     * @param numPackets
     * @param interval
     * @return avgRTT
    **/
    public double getLatency(String ipAddress, int numPackets, double interval){
        String pingCommand = "/system/bin/ping -c " + numPackets + " -i " + interval + " " + ipAddress;
        String inputLine = "";
        double avgRtt = 0;

        try {
            // execute the command on the environment interface
            Process process = Runtime.getRuntime().exec(pingCommand);
            // gets the input stream to get the output of the executed command
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            inputLine = bufferedReader.readLine();
            while ((inputLine != null)) {
                if (inputLine.length() > 0 && inputLine.contains("avg")) {  // when we get to the last line of executed ping command
                    break;
                }
                inputLine = bufferedReader.readLine();
            }
        }
        catch (IOException e){
            Log.e(TAG, "getLatency: EXCEPTION");
            e.printStackTrace();
        }

        // Extracting the average round trip time from the inputLine string
        String afterEqual = inputLine.substring(inputLine.indexOf("="), inputLine.length()).trim();
        String afterFirstSlash = afterEqual.substring(afterEqual.indexOf('/') + 1, afterEqual.length()).trim();
        String strAvgRtt = afterFirstSlash.substring(0, afterFirstSlash.indexOf('/'));
        avgRtt = Double.valueOf(strAvgRtt);

        return avgRtt;
    }

    private void fireEvent(TCPEvent event) {
        setChanged();
        notifyObservers(event);
        clearChanged();
    }

    public TCPClientState getState() {
        return state;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        if(state == TCPClientState.CONNECTED) {
            throw new RuntimeException("Cannot change port while connected!");
        }
        this.port = port;
    }

    public int getDrawQueueSize(){
        return drawQueue.size();
    }

    //KEEP THIS for when Networking gets faster.
    public ArrayList<JSONObject> getDrawData(){

        if(drawQueue.size() != 0){
            return drawQueue.remove();
        }
        return null;
    }

    public void connect(){
        if(state == TCPClientState.DISCONNECTED || state == TCPClientState.FAILED){
            if(address == null || port == null){
                throw new RuntimeException("Address or Port Missing");
            }
            new ConnectThread().start();
        }else {
            throw new  RuntimeException("This client is already connected or connecting");
        }
    }


    /**
     * <h4>TCP Send Message Routine</h4>
     *
     * <p>
     *     This Method is used to set up session with offloading server and... TODO define session variables.
     * </p>
     * Valid Messages:
     * <ul>
     *     <li>HELLO - Used to initiate session with remote server. No arguments.</li>
     *     <li>READY - Used to signify session is ready, RTSP server has received connection, and Client is ready to receive detection data. No Arguments (max packets in flight to be added)</li>
     *     <li>GOODBYE - Used to close session.</li>
     * </ul>
     *
     * @param message
     */
    public void sendMessage(String message) {
        if(state == TCPClientState.CONNECTED) {
            new SendMessageThread(message).start();
            Log.e(TAG, "sendMessage: " + message);
        } else {
            Log.e(TAG, "sendMessage: Could not send message");
            //throw new RuntimeException("This client is not connected, and cannot send any message");
        }
    }

    public void disconnect() {
        new DisconnectThread().run();
    }

    private class ConnectThread extends Thread{


        @Override
        public void run() {
            try {
                state = TCPClientState.CONNECTING;
                fireEvent(new TCPEvent(TCPEventType.CONNECTION_STARTED, null));

                socket = new Socket();
                Integer timeout = 2000;
                socket.connect(new InetSocketAddress(InetAddress.getByName(address), port), timeout);

                bufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                bufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //streamIn = socket.getInputStream();
                state = TCPClientState.CONNECTED;
                fireEvent(new TCPEvent(TCPEventType.CONNECTION_ESTABLISHED, null));

                new ReceiveMessagesThread().start();

            } catch(SocketTimeoutException e) {
                fireEvent(new TCPEvent(TCPEventType.CONNECTION_FAILED, e));

                Log.e(TAG, "Socket timed out: " + e);
                state = TCPClientState.FAILED;
            } catch(IOException e) {
                fireEvent(new TCPEvent(TCPEventType.CONNECTION_FAILED, e));

                Log.e(TAG, "Could not connect to host: " + e);
                state = TCPClientState.FAILED;
            }
        }
    }

    private class ReceiveMessagesThread extends Thread {
        @Override
        public void run() {
            while(state == TCPClientState.CONNECTED) {
                try {
                    TimingLogger timingLogger = new TimingLogger("TIMER", "Data Receiver: ");
                    timingLogger.addSplit("Total Receiver Frame Time");
                    String part = bufferIn.readLine();
                    if(part != null) {
                        message = message.concat(part);

                        if(message.contains("}]")) {
                            fireEvent(new TCPEvent(TCPEventType.DATA_RECEIVED, message));
//                            long tArrival= System.currentTimeMillis();
                            try {
                                String[] splitMessage = message.split("#", 2);
                                /*TODO: Figure out why tDelta is negative, and why tSend is being rounded.*/
//                                double tSend = Double.valueOf(splitMessage[0]);
//                                long tSend_L = (long)(tSend);
//                                Log.e(TAG, splitMessage[0] + ", " + tSend);
//                                Log.e(TAG, "TIMING: \ntSend:" + tSend_L + "\ntArrival: " + tArrival + "\ntDelta: " + Long.toString(tArrival - tSend_L));
                                ArrayList<JSONObject> objectsInFrame = new ArrayList<JSONObject>();
                                //Log.v(TAG, "JSON: " + message);
                                //timingLogger.addSplit("Create JSONArray");
                                JSONArray msgArray = new JSONArray(splitMessage[1]);
                                //timingLogger.addSplit("get all objects");
                                for(int index = 0; index < msgArray.length() /*TODO min(msgArray.length(), this.maxObjectsInFrame)*/ ; index++){
                                    objectsInFrame.add(msgArray.getJSONObject(index));
                                }
                                //timingLogger.addSplit("enqueue");
                                drawQueue.add(objectsInFrame);
                                //timingLogger.dumpToLog();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            message = "";
                        }else if(message.contains("[]")){
                            fireEvent(new TCPEvent(TCPEventType.EMPTY_PACKET, message));
                            message = "";
                        }else if(message.contains("HELLO")||message.contains("READY")){
                            fireEvent(new TCPEvent(TCPEventType.MESSAGE_RECEIVED, message));
                            message = "";
                        }
                    }
                } catch(IOException e) {
                    fireEvent(new TCPEvent(TCPEventType.CONNECTION_LOST, null));
                    try {
                        bufferOut.flush();
                        bufferOut.close();

                        bufferIn.close();

                        socket.close();
                    } catch (IOException er) {
                        Log.e(TAG, "Error clearing connection: " + er);
                    }

                    state = TCPClientState.DISCONNECTED;
                }
            }
        }
    }

    private class SendMessageThread extends Thread {
        private String messageLine;

        SendMessageThread(String message) {
            this.messageLine = message + "\n";
        }

        @Override
        public void run() {
            if(bufferOut.checkError()) {
                try {
                    bufferOut.flush();
                    bufferOut.close();

                    bufferIn.close();
                } catch(IOException e) {
                    Log.e(TAG, "Error sending this message: " + e);
                }
            } else {
                bufferOut.print(messageLine);
                bufferOut.flush();
                fireEvent(new TCPEvent(TCPEventType.MESSAGE_SENT, messageLine.toString()));
            }
        }
    }

    private class DisconnectThread extends Thread {
        @Override
        public void run() {
            try {
                drawQueue.clear();

                bufferOut.flush();
                bufferOut.close();

                bufferIn.close();

                socket.close();
            } catch(IOException e) {
                Log.e(TAG, "Error disconnecting this client: " + e);
            }

            fireEvent(new TCPEvent(TCPEventType.DISCONNECTED, null));
        }
    }


}


