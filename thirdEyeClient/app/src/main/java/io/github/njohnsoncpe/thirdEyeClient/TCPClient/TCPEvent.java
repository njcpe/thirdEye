package io.github.njohnsoncpe.thirdEyeClient.TCPClient;


import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Observable;

public class TCPEvent {
    private TCPEventType eventType;
    private Object payload;

    public TCPEvent(TCPEventType eventType, Object payload){
        this.eventType = eventType;
        this.payload = payload;
    }

    public TCPEventType getEventType() {
        return this.eventType;
    }

    public Object getPayload() {
        return this.payload;
    }
}

