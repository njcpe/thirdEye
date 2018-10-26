//
//
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.List;
//
///**
//Desc: Holder class for one frame of detection data from the server.
// Contains a list of detection Boxes for the current frame.
// **/
//public class FrameData {
//    public ArrayDeque<DetectionBox> detectionsInFrame;
//
//
//    public FrameData(ArrayDeque<DetectionBox> detectionsInFrame) {
//        this.detectionsInFrame = detectionsInFrame;
//    }
//
//    public void enqueueDetectionBox(DetectionBox detectionBox){
//        this.detectionsInFrame.add(detectionBox);
//    }
//
//    //CAN RETURN NULL TODO fix this or add null checking
//    public DetectionBox dequeueDetectionBox(){
//        return this.detectionsInFrame.remove();
//    }
//
//
//
//}
//
//public class DetectionBox {
//    private final ObjectLocation objectLocation;
//    private final List<ObjectData> possibleObjects;
//
//    public DetectionBox(int x1, int y1, int x2, int y2, ArrayList<ObjectData> possibleObjects) {
//        this.objectLocation = new ObjectLocation(x1, y1, x2, y2);
//        this.possibleObjects = new ArrayList<ObjectData>();
//
//
//    }
//}
//
///**
// * Class for Object Location
// */
//class ObjectLocation{
//    private int[] location;
//
//
//    public ObjectLocation(int x1, int y1, int x2, int y2) {
//        this.location = new int[]{x1, y1, x2, y2};
//    }
//
//    public int[] getLocation(){
//        return this.location;
//    }
//}
//
//class ObjectData{
//    private final String Label;
//    private final int probability;
//
//    public ObjectData(String label, int probability) {
//        Label = label;
//        this.probability = probability;
//    }
//
//    public String getLabel() {
//        return Label;
//    }
//
//    public int getProbability() {
//        return probability;
//    }
//
//}

package io.github.njohnsoncpe.thirdEyeClient.DetectionData;

import org.json.JSONObject;

class DetectionBox extends JSONObject{
    private int[] Location;



}