/*
 * Copyright 2016 Joseph Maestri
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

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser extends AppCompatActivity{
    private static BluetoothSocket socket = null;
    private static ArrayList<String> time = new ArrayList<>();
    private static ArrayList<Float> temperature = new ArrayList<>();
    private static ArrayList<Float> depth = new ArrayList<>();
    private static ArrayList<Float> conductivity = new ArrayList<>();
    private static ArrayList<Float> light = new ArrayList<>();
    private static ArrayList<Float> heading = new ArrayList<>();
    private static ArrayList<Float> accelX = new ArrayList<>(); //The accelerometer ArrayLists are not used
    private static ArrayList<Float> accelY = new ArrayList<>();
    private static ArrayList<Float> accelZ = new ArrayList<>();
    private static ArrayList<Float> gyroX = new ArrayList<>();
    private static ArrayList<Float> gyroY = new ArrayList<>();
    private static ArrayList<Float> gyroZ = new ArrayList<>();
    private static ArrayList<String> downloadedData = new ArrayList<>(); //change this back to private
    private static int MAX_SAVED_SAMPLES = 20; //Maximum # of samples to store in the ArrayLists
    private static StringBuffer downloadedStrings = new StringBuffer();
    public static boolean dialogOpen = true;
    private static boolean readBlockTimedOut = false;
    private static int bytesDownloaded = 0;
    public static int totalFileSize = 0;
    final static private Pattern csv_pattern = Pattern.compile("\\d{8}\\/\\d{8}\\.csv"); //Regex to check if csv filenames are valid

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
    public static void readSdCat(InputStream inStream, int distinctDataPoints, String fileName,
                                 int position) {
        String[] sdInfo = getSdInfo(inStream);
        if(sdInfo[0].equals("")) return;  //Stop if we timed out or have garbage data
        int fileSize;
        int arrayPosition = (position * 2) + 2; //Calculate array index of file size from order of filenames

        try {
            fileSize = Integer.parseInt(sdInfo[arrayPosition]);
            totalFileSize = fileSize;
            System.out.println("Filesize: " + fileSize + " bytes");
        }catch(NumberFormatException | ArrayIndexOutOfBoundsException e){
            System.out.println("SD information was not received correctly");

            //Try to flush the stream
            try{
                inStream.skip(inStream.available());
            }
            catch(java.io.IOException e1){
                System.out.println("Exception thrown flushing input stream");
            }

            //Set the filesize to an invalid number and update the progress bar
            totalFileSize = -1;
            MainActivity.Download.update(-1); //Update download printout with bad value to indicate download can't be continued

            return;
        }
        if (fileSize <= 0){
            MainActivity.Download.update(-1); //Update download printout with bad value to indicate download can't be continued
            return;
        }
        fileSize += 64; //account for extra non-csv data

        StringBuilder dlStrings = new StringBuilder();
        dlStrings.setLength(0); //Reset buffer
        downloadedData.clear();
        System.out.println("SD info contents: " + Arrays.toString(sdInfo));

        try {
            OutputStream outStream = socket.getOutputStream();
            Commands.sendCommand(outStream, "sd_cat", fileName);
            resetBuffers(true); //Resets all buffers to take in new data

            int size = 1000000; //TODO handle when we need more space
            byte[] buffer = new byte[size];  //buffer store for the stream
            int count;
            int sum = 0;
            int readStop;

            if (fileSize > buffer.length) readStop = buffer.length;
            else
                readStop = fileSize; //make sure that the data size isn't larger than our buffer
            while((sum<=fileSize) && dialogOpen && !MainActivity.Download.isCancelled()) {
                if(MainActivity.Download.isCancelled())
                {
                    System.out.println("Cancel detected");
                    return;
                }
                if(timedoutReadBlocking(inStream, 2000)) { //Set a 2 second timeout for our read blocking
                    totalFileSize = -1; //Set the file size to a bad value
                    MainActivity.Download.update(-1); //Update download printout with bad value to indicate download can't be continued
                    //exit the loop if we timed out and try to salvage what we can from the data
                    //we managed to download
                    sum = fileSize + 1;
                    continue;
                    //return; //Return if we timed out
                }
                else{ //Data is waiting to be read, so we read it
                    count = inStream.read(buffer, 0, readStop);
                }
                sum += count;
                int bytesTemp = sum;
                if(bytesTemp>69) bytesTemp -= 69; //Correct for extra non-value bytes, there are 69 of them
                float curProgress = ((float)bytesTemp / (float)totalFileSize) * 100f;
                bytesDownloaded = bytesTemp;
                bytesTemp = (int) curProgress;

                MainActivity.Download.update(bytesTemp);
                downloadedData.add(new String(buffer, 0, count)); //Add new strings to arraylist
            }
            dlStrings.setLength(0); //Reset buffer
            for (String printStr : downloadedData) {
                dlStrings.append(printStr);
            }
            
            if(!dlStrings.toString().isEmpty()) {
                if(!parseData(dlStrings.toString(), distinctDataPoints)){ //Something went wrong when parsing or we timed out
                    System.out.println("Missing time data: " + dlStrings.toString());
                }
            }
        }catch(IOException | NullPointerException e){
            System.out.println("Read exception or null pointer exception");
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
            byte[] buffer = new byte[60];
            if(inStream.available()==0) return; //Make sure it's actually sending us data
            int bytes = inStream.read(buffer); //bytes returned from read()

            downloadedData.add(new String(buffer, 0, bytes)); //Add new strings to arraylist
            downloadedStrings.setLength(0); //Reset buffer

            for (String printStr : downloadedData) {
                downloadedStrings.append(printStr);
            }

            //Separate and check the data
            String[] csvData = separateLines(downloadedStrings.toString(), 0).split(",");

            if(!dataIsValid(csvData))
                return;

            if(activity.equals("TempCondActivity")) {
                updateRTdata(csvData, temperature, 1);
                updateRTdata(csvData, conductivity, 3);
            }
            if(activity.equals("DepthLightActivity")) {
                updateRTdata(csvData, depth, 2);
                updateRTdata(csvData, light, 4);
            }
            if(activity.equals("MainActivity")) {
                updateRTdata(csvData, heading, 5);
                updateRTdata(csvData, gyroX, 9);
                updateRTdata(csvData, gyroY, 10);
                updateRTdata(csvData, gyroZ, 11);
            }

        }catch(Exception e){
            System.out.println("Read exception");
        }
    }

    private static String separateLines(String input, int lineNumber){
        String line = "";
        String[] lineArray;
        if(input.length()>=20) {
            lineArray = input.split("\\n");
            if (lineNumber <= lineArray.length) line = lineArray[lineNumber];
        }
        return line;
    }

    private static boolean dataIsValid(String[] csv){
        if (csv.length == 12) { //Make sure we have an entire chunk of data
            //Throw it out if it's garbage
            if (csv[0].split(":").length == 3)
                return true;
        }
        return false;
    }

    /**
     * Parses the real time CSV data, we drop some data with this method
     * but we pull so much in it doesn't matter
     *
     * THIS IS ONLY SAFE IF dataIsValid IS CALLED AND CHECKED
     * BEFORE PASSING 'String[] csv' TO THIS METHOD
     *
     * @param csv The string of data we want to parse
     * @param arrayList The data ArrayList for whatever sensor data category we wish to populate
     * @param position The position in the csv file the data we want is (ex: Conductivity would
     *                 be position 1 in the following file: Time,Conductivity)
     */
    private static void updateRTdata(String[] csv, ArrayList<Float> arrayList, int position){
            if(!isFloat(csv[position]))
                return;

            Float currentValue = Float.parseFloat(csv[position]);
            arrayList.add(currentValue);

            while (arrayList.size() > MAX_SAVED_SAMPLES) {
                if (!arrayList.isEmpty()){
                    arrayList.remove(0);
                }
            }
    }

    /**
     * This method gets the number of files on the SD card, and
     * the sizes of those files in bytes
     * @param inStream InputStream
     * @return separatedData The parsed/separated data
     */
    private static String[] getSdInfo(InputStream inStream) {
        try {
            if (socket != null)
                socket.getInputStream().skip(socket.getInputStream().available());
        }
        catch(IOException e){
            System.out.println("Failed to skip available bytes in the input stream");
        }
                int count;
                int size = 1024; //Just in case we have a large number of files
                byte[] buffer = new byte[size];

                boolean doneDownloading = false;
                String[] separatedData = {""};
                StringBuilder sdInfo = new StringBuilder();
                sdInfo.setLength(0); //Make sure it's clear
                String fileData = "";

                    try {
                        OutputStream outStream = socket.getOutputStream();
                        Commands.sendCommand(outStream, "fileInfo", "");
                        while (!doneDownloading) {
                            try {
                                if(timedoutReadBlocking(inStream, 2000)) { //Set a 2 second timeout for our read blocking
                                    return separatedData; //Return empty if we timed out
                                }else{ //Data is waiting to be read, so we read it
                                    count = inStream.read(buffer, 0, buffer.length);
                                }
                                sdInfo.append(new String(buffer, 0, count));
                            }catch(IOException e){
                                System.out.println("No data available");
                                return separatedData;
                            }
                            String[] check = sdInfo.toString().split(",");

                            if(check.length!=0)
                            if(check[check.length-1].equals(">")) {
                                String[] firstSplit = sdInfo.toString().split("\\n\\r");
                                if(firstSplit.length>1) fileData = firstSplit[firstSplit.length-1];
                                separatedData = fileData.split(",");
                                doneDownloading = true;
                            }
                        }
                    } catch(IOException e){
                        System.out.println("Failed to send command");
                }
                    return separatedData;
            }

    /**
     * This method pulls the filename data from the SD info that we download
     * @return fileNameList The ArrayList of file names on the SD card
     */
    public static ArrayList<String> extractFileNames() {
        ArrayList<String> fileNameList = new ArrayList<>();
        try {
            if (socket != null && socket.isConnected()) {
                String[] separatedData = getSdInfo(socket.getInputStream());
                if(separatedData[0].equals("")) return fileNameList;  //Return an empty array if we timed out or have garbage data
                if (separatedData.length >= 2) {
                    for (int i = 1; i < separatedData.length - 1; i += 2) {
                        if (isFileName(separatedData[i])) //There's a filename at every odd index, except for the end
                            fileNameList.add(separatedData[i]);
                    }
                }
            }
        }catch(IOException e){
            Log.d("Exception", "Exception extracting file names");
        }
        return fileNameList;
    }

    /**
     * Checks if the string is a valid csv filename
     * Valid format: YYYYMMDD/YYMMDD##.csv, where ## is the file number
     * @param s String to test
     */
    private static boolean isFileName(String s){
        Matcher m = csv_pattern.matcher(s);

        return m.matches();
    }

    /**
     * The solution in this method for sidestepping the blocking aspect of the read method came from:
     * http://stackoverflow.com/questions/14023374/making-a-thread-which-cancels-a-inputstream-read-call-if-x-time-has-passed
     * @param inStream InputStream
     * @param timeout The amount of time we allow the read method to block for
     */
    private static boolean timedoutReadBlocking(InputStream inStream, int timeout){
        int available;
        long startTime = System.nanoTime();
        readBlockTimedOut = false; //Reset flag
        try {
            while (true) {
                available = inStream.available();
                if (available > 0) {
                    break;
                }
                Thread.sleep(1);
                long estimatedTime = System.nanoTime() - startTime;
                long estimatedMillis = TimeUnit.MILLISECONDS.convert(estimatedTime,
                        TimeUnit.NANOSECONDS);
                if (estimatedMillis > timeout){
                    Log.i("Timeout", "Download timed out");
                    readBlockTimedOut = true;
                    //Try to flush the stream so we aren't stuck reading the same garbage data
                    inStream.skip(inStream.available());
                    return true; //timeout
                }
            }
        }catch(IOException | InterruptedException e){
            Log.d("Exception", "Exception");
        }
        return false;
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

    public static ArrayList<String> getTime(){
        return time;
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

    public static int getBytesDown(){return bytesDownloaded;}

    public static boolean readingTimedOut(){
        return readBlockTimedOut;
    }

    /**
     * Parses the CSV data
     * @param input The data string we want to parse
     * @param distinctDataPoints this is the number of distinctly separated data TYPES in the file
     */
    private static boolean parseData(String input, int distinctDataPoints) {
        int dataType = 0;
        int curIndex = 0;
        String eof = "U+1F4A9";
        ArrayList<String> parsedData = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

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
                                dataType = 0; //Keep resetting until we actually get some time data
                                continue;//return false;
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
     * @param string The string we are checking
     * @return Returns true if string is a float, else false
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
     * @param string The string we are checking
     * @return Returns true if string is a time, else false
     */
    public static boolean isTime(String string)
    {
        StringBuilder check = new StringBuilder();
        for(String checkStr : string.split(":")){ //Splitting with ":+" takes an extra 5 seconds per 5 seconds of data
            check.setLength(0);
            check.append(checkStr); //Only store the last value
            if(checkStr.equals(string)) return false; //Make sure it actually has ":" in it
        }
        return isFloat(check.toString()); //The last chunk of the time string will be a number (like the 11 in 12:13:11)
    }
}