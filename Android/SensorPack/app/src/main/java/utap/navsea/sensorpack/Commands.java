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

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Commands {
    /**
     * This method sends commands to the Arduino that start desired events
     * @param outStream OutputStream
     * @param selection This is the actual command we want to send
     * @param filename This is the filename of a csv file, used for requesting specific files from the SD card
     */
    public static void sendCommand(OutputStream outStream, String selection, String filename){
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
                sendPound(mmOutStream);
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
                sendSdCat(mmOutStream, filename);
                break;
            case "sd_dd":
                sendSdDd(mmOutStream);
                break;
            case "sd_append":
                sendSdAppend(mmOutStream, filename);
                break;
            case "sd_create":
                sendSdCreate(mmOutStream, filename);
                break;
            case "sd_del":
                sendSdDel(mmOutStream, filename);
                break;
            case "reset":
                sendReset(mmOutStream);
                break;
            case "fileInfo":
                sendFileInfo(mmOutStream);
                break;
            case "dz":
                sendDz(mmOutStream);
                break;
            default:
                break;
        }
    }

    /**
     * This is "logfile" in ASCII code, it is the command that
     * automatically logs data to a file
     * It needs to be called again to stop logging
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "logapp" in ASCII code, it is the command that
     * causes the Arduino side to start sending continuous CSV data
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "log" in ASCII code, it is the command that logs data
     * to the command line (same as logapp, but tab separated values)
     * @param mmOutStream OutputStream
     */
    private static void sendLog(OutputStream mmOutStream){
        try {
            mmOutStream.write(108);
            mmOutStream.write(111);
            mmOutStream.write(103);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "help" in ASCII code
     * Provides a list of commands
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "#" in ASCII code
     * Ignores everything after the "#"
     * @param mmOutStream OutputStream
     */
    private static void sendPound(OutputStream mmOutStream){
        try {
            mmOutStream.write(35);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "test" in ASCII code, it is the command that
     * sends a user chosen array of test data to the Arduino
     * then causes the Arduino to send back this array
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "rtc_get" in ASCII code, gets current time from Arduino
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "rtc_set" in ASCII code
     * only useable if autoset is turned off, otherwise automatically sets
     * the time to the compiled time. Arduino will tell us if autoset is turned on.
     * Format: rtc_set YYYY/MM/DD HH:MM:SS
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "sd_init" in ASCII code, if the Arduino doesn't see the
     * SD card on boot we can try to force it with this
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "sd_ls" in ASCII code, it is the command that
     * lists the files on the SD card
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "sd_cat" in ASCII code, it is the command that
     * prints a file to the monitor.
     * Format: Takes in a file name sd_cat filename.csv
     * @param mmOutStream OutputStream
     * @param filename Filename of the desired csv file
     */
    private static void sendSdCat(OutputStream mmOutStream, String filename){
        byte[] filenameBytes = filename.getBytes();
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(99);
            mmOutStream.write(97);
            mmOutStream.write(116);
            mmOutStream.write(32); //space
            mmOutStream.write(filenameBytes);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "sd_dd" in ASCII code, it is the command that
     * causes the Arduino to dump all the files on the SD card
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "sd_append" in ASCII code, it is the command that
     * appends text to a chosen file.
     * Format: filename sd_appened filename text (input buffer size is 100 ASCII chars)
     * @param mmOutStream OutputStream
     * @param filename Filename of desired csv file
     */
    private static void sendSdAppend(OutputStream mmOutStream, String filename){
        byte[] filenameBytes = filename.getBytes();
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
            mmOutStream.write(filenameBytes);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "sd_create" in ASCII code, it is the command that
     * creates a new file on the SD card.
     * Format: sd_create filename
     * @param mmOutStream OutputStream
     * @param filename Filename of desired csv file
     */
    private static void sendSdCreate(OutputStream mmOutStream, String filename){
        byte[] filenameBytes = filename.getBytes();
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
            mmOutStream.write(filenameBytes);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "sd_del" in ASCII code, it is the command that deletes a chosen file
     * from the SD card
     * Format: sd_del filename
     * @param mmOutStream OutputStream
     * @param filename Filename of desired csv file
     */
    private static void sendSdDel(OutputStream mmOutStream, String filename){
        byte[] filenameBytes = filename.getBytes();
        try {
            mmOutStream.write(115);
            mmOutStream.write(100);
            mmOutStream.write(95);
            mmOutStream.write(100);
            mmOutStream.write(101);
            mmOutStream.write(108);
            mmOutStream.write(32); //space
            mmOutStream.write(filenameBytes);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "reset" in ASCII code, it is the command that
     * resets the whole system
     * @param mmOutStream OutputStream
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
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * This is "fileInfo" in ASCII code, it is the command that
     * tells us how many files are on the SD card and the size of those files
     * @param mmOutStream OutputStream
     */
    private static void sendFileInfo(OutputStream mmOutStream){
        try {
            mmOutStream.write(102);
            mmOutStream.write(105);
            mmOutStream.write(108);
            mmOutStream.write(101);
            mmOutStream.write(73);
            mmOutStream.write(110);
            mmOutStream.write(102);
            mmOutStream.write(111);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

    /**
     * ??? It's a mystery
     * @param mmOutStream OutputStream
     */
    private static void sendDz(OutputStream mmOutStream){
        try {
            mmOutStream.write(100);
            mmOutStream.write(97);
            mmOutStream.write(110);
            mmOutStream.write(103);
            mmOutStream.write(101);
            mmOutStream.write(114);
            mmOutStream.write(32);
            mmOutStream.write(122);
            mmOutStream.write(111);
            mmOutStream.write(110);
            mmOutStream.write(101);
            mmOutStream.write(13); //carriage return
        }
        catch(IOException e){
            Log.d("IOException", "Exception sending command");
        }
    }

}
