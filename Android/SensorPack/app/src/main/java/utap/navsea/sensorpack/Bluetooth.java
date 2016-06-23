/*
 * Copyright 2016 Joseph Maestri
 *
 * The following link was frequently consulted in the creation of this file:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 *
 * https://github.com/prefanatic/BME-363-Lab was also consulted in the creation
 * of this file
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utap.navsea.sensorpack;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ArrayAdapter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

public class Bluetooth extends AppCompatActivity{
    private static ArrayAdapter<String> mArrayAdapter;
    private static BluetoothSocket socket = null;
    //Below UUID is the standard SSP UUID:
    //Also seen at https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothAdapter mBluetoothAdapter;
    private static ArrayList<String> time = new ArrayList<String>();
    private static ArrayList<Float> temperature = new ArrayList<Float>();
    private static ArrayList<Float> depth = new ArrayList<Float>();
    private static ArrayList<Float> conductivity = new ArrayList<Float>();
    private static ArrayList<Float> light = new ArrayList<Float>();
    private static ArrayList<Float> heading = new ArrayList<Float>();
    private static ArrayList<Float> accelX = new ArrayList<Float>();
    private static ArrayList<Float> accelY = new ArrayList<Float>();
    private static ArrayList<Float> accelZ = new ArrayList<Float>();
    private static ArrayList<Float> gyroX = new ArrayList<Float>();
    private static ArrayList<Float> gyroY = new ArrayList<Float>();
    private static ArrayList<Float> gyroZ = new ArrayList<Float>();
    private static ArrayList<String> downloadedData = new ArrayList<String>(); //change this back to private
    public static StringBuffer downloadedStrings = new StringBuffer(); //TODO back to private

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
    }

    /**
     * Some pieces of this method were taken from:
     * http://stackoverflow.com/questions/25443297/how-to-read-from-the-inputstream-of-a-bluetooth-on-android
     * Modifications were made to conform to the specifications of this app
     */
    public static void readData(InputStream inStream, int distinctDataPoints) {
        try {
            boolean eofFound = false;
            StringBuffer downloadedStrings = new StringBuffer();
            resetBuffers(true); //Resets all buffers to take in new data

            while (!eofFound) {
                byte[] buffer = new byte[1024];  //buffer store for the stream
                int bytes = inStream.read(buffer); //bytes returned from read()
                downloadedData.add(new String(buffer, 0, bytes)); //Add new strings to arraylist
                boolean check = check4eof(downloadedData);

                if (check) {
                    downloadedStrings.setLength(0); //Reset buffer
                    for (String printStr : downloadedData) {
                        downloadedStrings.append(printStr);
                    }
                    if(parseData(downloadedStrings.toString(), distinctDataPoints)){
                        eofFound = true;
                    }
                    else{ //Something went wrong when parsing or we timed out
                        return; //Stop reading
                    }
                }
            }
        }catch(Exception e){
            System.out.println("Read exception");
        }
    }

    /**
     * Some pieces of this method were taken from:
     * http://stackoverflow.com/questions/25443297/how-to-read-from-the-inputstream-of-a-bluetooth-on-android
     * Modifications were made to conform to the specifications of this app
     */
    public static void readRtData(InputStream inStream, String activity) {
        try {
            resetBuffers(false);
            byte[] buffer;
            if(activity.equals("MainActivity")) buffer = new byte[60]; //need a larger buffer to read gyro data
            else buffer = new byte[30]; //need a smaller buffer for better speed
            if(inStream.available()==0) return; //Make sure it's actually sending us data
            int bytes = inStream.read(buffer); //bytes returned from read()

            downloadedData.add(new String(buffer, 0, bytes)); //Add new strings to arraylist
            downloadedStrings.setLength(0); //Reset buffer

            for (String printStr : downloadedData) {
                downloadedStrings.append(printStr);
            }
            if(activity.equals("TempCondActivity")) {
                parseRtData(downloadedStrings.toString(), temperature, 10, 1);
                parseRtData(downloadedStrings.toString(), conductivity, 10, 3);
            }
            if(activity.equals("DepthLightActivity")) {
                parseRtData(downloadedStrings.toString(), depth, 50, 2);
                parseRtData(downloadedStrings.toString(), light, 100, 4);
            }
            if(activity.equals("MainActivity")) {
                parseRtData(downloadedStrings.toString(), heading, 100, 5);
                parseRtData(downloadedStrings.toString(), gyroX, 100, 9);
                parseRtData(downloadedStrings.toString(), gyroY, 100, 10);
                parseRtData(downloadedStrings.toString(), gyroZ, 100, 11);
            }

        }catch(Exception e){
            System.out.println("Read exception");
        }
    }

    private static String separateLines(String input, int lineNumber){
        String line = "";
        String[] lineArray = {""};
        if(input.length()>=20) {
            lineArray = input.split("\\n");
            if (lineNumber <= lineArray.length) line = lineArray[lineNumber];
        }
        return line;
    }

    /**
     * Parses the real time CSV data, we drop some data with this method
     * but we pull so much in it doesn't matter
     * @param input
     * @param arrayList
     * @param errorRange
     * @param position
     */
    private static void parseRtData(String input, ArrayList<Float> arrayList, int errorRange, int position){
        if(separateLines(input, 0)!="") { //If we have data
            String[] csvData = separateLines(input, 0).split(",");
            if(csvData.length > position) { //Make sure we have enough data
                if (!arrayList.isEmpty()) {
                    Float lastValue = arrayList.get(arrayList.size() - 1);
                    if ((Float.parseFloat(csvData[position]) < lastValue + errorRange) &&
                            (Float.parseFloat(csvData[position]) > lastValue - errorRange)) //shouldn't change by more than 10 deg between samples, or it's garbage data
                        arrayList.add(Float.parseFloat(csvData[position]));
                } else {
                    arrayList.add(Float.parseFloat(csvData[position]));
                }
            }
        }
    }

    public static void resetBuffers(boolean includeSensors){
        downloadedStrings.setLength(0);
        downloadedData.clear();
        time.clear();
        if(includeSensors) {
            temperature.clear();
            depth.clear();
            conductivity.clear();
            light.clear();
            heading.clear();
            accelX.clear();
            accelY.clear();
            accelZ.clear();
            gyroX.clear();
            gyroY.clear();
            gyroZ.clear();
        }
    }

    public static void saveSocket(BluetoothSocket saveSocket){socket = saveSocket;}

    public static BluetoothSocket getSocket() {return socket;}

    public static ArrayList<String> getTime(){
        return time;
    }

    public static void removeFirst(){
        if(temperature.size()>20) temperature.remove(0);
        if(conductivity.size()>20) conductivity.remove(0);
        if(depth.size()>20) depth.remove(0);
        if(light.size()>20) light.remove(0);
        if(heading.size()>20) heading.remove(0);
        if(gyroX.size()>20) gyroX.remove(0);
        if(gyroY.size()>20) gyroY.remove(0);
        if(gyroZ.size()>20) gyroZ.remove(0);
    }

    public static ArrayList<Float> getTemp(){
        return temperature;
    }

    public static ArrayList<Float> getLight(){
        return light;
    }

    public static ArrayList<Float> getCond(){
        return conductivity;
    }

    public static ArrayList<Float> getDepth(){
        return depth;
    }

    public static ArrayList<Float> getHeading(){
        return heading;
    }

    public static ArrayList<Float> getGyroX(){
        return gyroX;
    }

    public static ArrayList<Float> getGyroY(){
        return gyroY;
    }

    public static ArrayList<Float> getGyroZ(){
        return gyroZ;
    }

    /**
     * This method checks whether or not the eof has been reached
     * @param inputString
     * @return
     */
    private static boolean check4eof(ArrayList<String> inputString){
        StringBuffer buffer = new StringBuffer();
        buffer.setLength(0);

        for (String printStr : inputString) {
            buffer.append(printStr);
        }

        for(String splitVal : buffer.toString().split(",")) {
            String[] newlineSplit = splitVal.split("\\n?\\r");
            if (!(isFloat(splitVal) && isTime(buffer.toString()))){ //ignore the command data
                if(splitVal.trim().equals("U+1F4A9")) return true; //check for the eof
            }
            if(newlineSplit.length>=2) //Bounds check
                if(newlineSplit[newlineSplit.length-1].trim().equals("U+1F4A9")) return true;
        }
        return false;
    }

    /**
     * Parses the CSV data
     * @param input
     * @param distinctDataPoints this is the number of distinctly separated data TYPES in the file
     */
    private static boolean parseData(String input, int distinctDataPoints) {
        int dataType = 0;
        int curIndex = 0;
        String eof = "U+1F4A9";
        ArrayList<String> parsedData = new ArrayList<String>();
        StringBuffer buffer = new StringBuffer();

        for (String splitVal : input.split(",")) {
            if (isFloat(splitVal) || isTime(splitVal)) { //Check if it's data we actually want
                parsedData.add(splitVal);
                switch (dataType) {
                    case 0:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            for(String bufferStr : parsedData.get(curIndex).split("\\n?\\r")) { //ignore newlines and carriage returns
                                buffer.setLength(0); //Reset buffer
                                buffer.append(bufferStr); //When we exit the loop, only time will be here
                            }
                            if(isTime(buffer.toString())) //Only take if it's a time value
                                time.add(buffer.toString()); //Grab the time value
                            else{
                                return false; //TODO BACK UP HERE AND FIND THE REAL TIME, FIX PARSING
                            }

                            for(String bufferStr : parsedData.get(curIndex).split("\\n?\\r")) { //ignore time data
                                buffer.setLength(0); //Reset buffer
                                buffer.append(bufferStr); //Get gyroZ data
                                break; //Only run once to get the first value
                            }
                            if(isFloat(buffer.toString())) //Make sure it's not garbage or parsing logapp data
                                gyroZ.add(Float.parseFloat(buffer.toString())); //Grab the gyro value
                        }
                        break;
                    case 1:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            temperature.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 2:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            depth.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 3:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            conductivity.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 4:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            light.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 5:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            heading.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 6:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            accelX.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 7:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            accelY.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 8:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            accelZ.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 9:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            gyroX.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 10:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            gyroY.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 11: //This case will never be reached if distinctDataPoints<12
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            gyroZ.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    default:

                        break;
                }
                curIndex++;
                dataType++;
                if (dataType > (distinctDataPoints-1)) dataType = 0; //reset data counter after last datapoint is filled
            }
        }
        return true;
    }

    /**
     * This method checks if the input string is a float
     * @param string
     * @return
     */
    public static boolean isFloat(String string)
    {
        try {
            Float.parseFloat(string);
        }
        catch(NumberFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * This method checks if the input string contains time data
     * @param string
     * @return
     */
    public static boolean isTime(String string)
    {
        StringBuffer check = new StringBuffer();
        for(String checkStr : string.split(":")){ //Splitting with ":+" takes an extra 5 seconds per 5 seconds of data
            check.setLength(0);
            check.append(checkStr); //Only store the last value
            if(checkStr.equals(string)) return false; //Make sure it actually has ":" in it
        }

        if(isFloat(check.toString())) return true; //The last chunk of the time string will be a number (like the 11 in 12:13:11)
        else return false;
    }

    /**
     * Much of this method was taken from:
     * https://developer.android.com/guide/topics/connectivity/bluetooth.html
     * Modifications were made to conform to the specifications of this app
     */
    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };
}