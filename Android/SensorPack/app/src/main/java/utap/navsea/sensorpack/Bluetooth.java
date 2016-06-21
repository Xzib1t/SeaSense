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
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.widget.ArrayAdapter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;
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
    private static StringBuffer downloadedStrings = new StringBuffer();
    private static StringBuffer downloadedStrings1 = new StringBuffer();
    private static int dataType = 0;
    private static int curIndex = 0;

    private static int newLineCount = 0;
    private static int lastNl = 0;

    private static int index = 0;

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
    public static void readRtData(InputStream inStream, String currentActivity) {
        try {
            resetBuffers(false);
            StringBuffer sBuffer = new StringBuffer();
            byte[] buffer = new byte[30];
            int bytes = inStream.read(buffer); //bytes returned from read()

            downloadedData.add(new String(buffer, 0, bytes)); //Add new strings to arraylist
            downloadedStrings.setLength(0); //Reset buffer

            for (String printStr : downloadedData) {
                downloadedStrings.append(printStr);
            }
            parseRtData(downloadedStrings.toString(), temperature, 10, 1);
            parseRtData(downloadedStrings.toString(), conductivity, 10, 3);
            parseRtData(downloadedStrings.toString(), depth, 10, 2);
            parseRtData(downloadedStrings.toString(), light, 100, 4);


        }catch(Exception e){
            System.out.println("Read exception");
        }
    }

    private static String separateLines(String input, int lineNumber){
        String line = "";
        String[] lineArray = {""};
        if(input.length()>=20) {
            //TODO if length is <20, save end of string, mash together with start of next buffer reading
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
            if (!arrayList.isEmpty()) {
                Float lastValue = arrayList.get(arrayList.size() - 1);
                if ((Float.parseFloat(csvData[position]) < lastValue + errorRange) &&
                        (Float.parseFloat(csvData[position]) > lastValue - errorRange)) //shouldn't change by more than 10 deg between samples, or it's garbage data
                    arrayList.add(Float.parseFloat(csvData[position]));
            }else{
                arrayList.add(Float.parseFloat(csvData[position]));
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
     * This method sends commands to the Bluno that start desired events
     * @param outStream
     * @param selection
     */
    public static void sendCommand(OutputStream outStream, String selection){
        DataOutputStream mmOutStream = new DataOutputStream(outStream);

        switch(selection){
            case "logfile":
                sendLogFile(mmOutStream);
                break;
            case "logapp":
                sendLogApp(mmOutStream);
                break;
            case "log":
                sendLog(mmOutStream);
                break;
            case "help":
                sendHelp(mmOutStream);
                break;
            case "#":
                sendHashtag(mmOutStream);
                break;
            case "test":
                byte[] testData = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 100, 50};
                sendTest(mmOutStream, testData);
                break;
            case "rtc_get":
                sendRtcGet(mmOutStream);
                break;
            case "rtc_set":
                sendRtcSet(mmOutStream);
                break;
            case "sd_init":
                sendSdInit(mmOutStream);
                break;
            case "sd_ls":
                sendSdLs(mmOutStream);
                break;
            case "sd_cat":
                sendSdCat(mmOutStream);
                break;
            case "sd_dd":
                sendSdDd(mmOutStream);
                break;
            case "sd_append":
                sendSdAppend(mmOutStream);
                break;
            case "sd_create":
                sendSdCreate(mmOutStream);
                break;
            case "sd_del":
                sendSdDel(mmOutStream);
                break;
            case "reset":
                sendReset(mmOutStream);
                break;
            default:
                break;
        }

    }

    /**
     * This is "logfile" in ASCII code, it is the command that
     * automatically logs data to a file
     * all log files need to be called again to stop them
     * can run other commands while this command is running
     * @param mmOutStream
     */
    private static void sendLogFile(OutputStream mmOutStream){
        try {
            mmOutStream.write(108);
            mmOutStream.write(111);
            mmOutStream.write(103);
            mmOutStream.write(102);
            mmOutStream.write(105);
            mmOutStream.write(108);
            mmOutStream.write(101);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "logapp" in ASCII code, it is the command that
     * causes the Arduino side to start sending continuous CSV data
     * @param mmOutStream
     */
    private static void sendLogApp(OutputStream mmOutStream){
        try {
            mmOutStream.write(108);
            mmOutStream.write(111);
            mmOutStream.write(103);
            mmOutStream.write(97);
            mmOutStream.write(112);
            mmOutStream.write(112);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "log" in ASCII code, it is the command that logs data
     * to the command line (same as logapp, but tab separated values)
     * @param mmOutStream
     */
    private static void sendLog(OutputStream mmOutStream){
        try {
            mmOutStream.write(108);
            mmOutStream.write(111);
            mmOutStream.write(103);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "help" in ASCII code
     * Provides a list of commands
     * @param mmOutStream
     */
    private static void sendHelp(OutputStream mmOutStream){
        try {
            mmOutStream.write(104);
            mmOutStream.write(101);
            mmOutStream.write(108);
            mmOutStream.write(112);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "#" in ASCII code
     * Ignores everything after the "#"
     * @param mmOutStream
     */
    private static void sendHashtag(OutputStream mmOutStream){
        try {
            mmOutStream.write(35);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "test" in ASCII code, it is the command that
     * sends a user chosen array of test data to the Bluno
     * then causes the Bluno to send back this array
     * @param mmOutStream
     */
    private static void sendTest(OutputStream mmOutStream, byte[] dataArray){
        try {
            mmOutStream.write(116);
            mmOutStream.write(101);
            mmOutStream.write(115);
            mmOutStream.write(116);
            mmOutStream.write(32); //space
            mmOutStream.write(dataArray); //test data
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "rtc_get" in ASCII code, gets current time from Bluno
     * @param mmOutStream
     */
    private static void sendRtcGet(OutputStream mmOutStream){
        try {
            mmOutStream.write(114);
            mmOutStream.write(116);
            mmOutStream.write(99);
            mmOutStream.write(95);
            mmOutStream.write(103);
            mmOutStream.write(101);
            mmOutStream.write(116);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "rtc_set" in ASCII code
     * only useable if autoset is turned off, otherwise automatically sets
     * the time to the compiled time. Bluno will tell you if autoset is turned on.
     * Format: rtc_set YYYY/MM/DD HH:MM:SS
     * @param mmOutStream
     */
    private static void sendRtcSet(OutputStream mmOutStream){
        //TODO add support for user input time and date
        byte[] testDate = {10, 47, 17}; //MM/DD HH:MM:SS
        byte[] testTime = {20, 58, 27, 58, 59};
        try {
            mmOutStream.write(114);
            mmOutStream.write(116);
            mmOutStream.write(99);
            mmOutStream.write(95);
            mmOutStream.write(115);
            mmOutStream.write(101);
            mmOutStream.write(116);
            mmOutStream.write(32); //space
            mmOutStream.write(1783);  //YYYY
            mmOutStream.write(testDate); //slash
            mmOutStream.write(32); //space
            mmOutStream.write(testTime);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "sd_init" in ASCII code, if the Bluno doesn't see the
     * SD card on boot we can try to force it with this
     * @param mmOutStream
     */
    private static void sendSdInit(OutputStream mmOutStream){
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(105);
            mmOutStream.write(110);
            mmOutStream.write(105);
            mmOutStream.write(116);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "sd_ls" in ASCII code, it is the command that
     * lists the files on the SD card
     * @param mmOutStream
     */
    private static void sendSdLs(OutputStream mmOutStream){
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(108);
            mmOutStream.write(115);
            mmOutStream.write(32); //space
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "sd_cat" in ASCII code, it is the command that
     * prints a file to the monitor.
     * Format: Takes in a file name sd_cat filename.csv
     * @param mmOutStream
     */
    private static void sendSdCat(OutputStream mmOutStream){
        //TODO add support for user input filenames
        byte[] test = {116, 101, 115, 116, 46, 116, 120, 116}; //test.txt this file is always on the SD card
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(99);
            mmOutStream.write(97);
            mmOutStream.write(116);
            mmOutStream.write(32); //space
            mmOutStream.write(test); //test.txt
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "sd_dd" in ASCII code, it is the command that
     * causes the Arduino to dump all the files on the SD card
     * @param mmOutStream
     */
    private static void sendSdDd(OutputStream mmOutStream){
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(100);
            mmOutStream.write(100);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "sd_append" in ASCII code, it is the command that
     * appends text to a chosen file.
     * Format: filename sd_appened filename text (input buffer size is 100 ASCII chars)
     * @param mmOutStream
     */
    private static void sendSdAppend(OutputStream mmOutStream){
        //TODO add support for user input append text
        byte test[] = {116, 101, 115, 116};
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(97);
            mmOutStream.write(112);
            mmOutStream.write(112);
            mmOutStream.write(101);
            mmOutStream.write(110);
            mmOutStream.write(100);
            mmOutStream.write(32); //space
            mmOutStream.write(test);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "sd_create" in ASCII code, it is the command that
     * creates a new file on the SD card.
     * Format: sd_create filename
     * @param mmOutStream
     */
    private static void sendSdCreate(OutputStream mmOutStream){
        //TODO add support for user input filename
        byte test[] = {116, 101, 115, 116, 46, 116, 120, 116}; //test.txt
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(99);
            mmOutStream.write(114);
            mmOutStream.write(101);
            mmOutStream.write(97);
            mmOutStream.write(116);
            mmOutStream.write(101);
            mmOutStream.write(32); //space
            mmOutStream.write(test); //text.txt
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "sd_del" in ASCII code, it is the command that deletes a chosen file
     * from the SD card
     * Format: sd_del filename
     * @param mmOutStream
     */
    private static void sendSdDel(OutputStream mmOutStream){
        //TODO add support for user input filename
        byte test[] = {116, 101, 115, 116, 46, 116, 120, 116}; //test.txt
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(100);
            mmOutStream.write(101);
            mmOutStream.write(108);
            mmOutStream.write(32); //space
            mmOutStream.write(test); //text.txt
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
    }

    /**
     * This is "reset" in ASCII code, it is the command that
     * resets the whole system
     * @param mmOutStream
     */
    private static void sendReset(OutputStream mmOutStream){
        try {
            mmOutStream.write(114);
            mmOutStream.write(101);
            mmOutStream.write(115);
            mmOutStream.write(101);
            mmOutStream.write(116);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            //TODO
        }
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