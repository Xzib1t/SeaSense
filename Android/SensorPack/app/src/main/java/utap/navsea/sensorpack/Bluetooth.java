/*
 * Copyright 2016 Joseph Maestri
 *
 * The methods in this file were either taken directly/almost directly from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 * or created using the information from the above guide
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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class Bluetooth extends AppCompatActivity{
    private static ArrayAdapter<String> mArrayAdapter;
    private static BluetoothSocket socket = null;
    //Below UUID is the standard SSP UUID:
    //Also seen at https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothAdapter mBluetoothAdapter;
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
    public static ArrayList<String> downloadedData = new ArrayList<String>(); //change this back to private

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        Button buttonList = (Button) findViewById(R.id.button_list);
        buttonList.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mArrayAdapter.clear();
                setupBT();

                ListView newDevicesListView = (ListView)
                        findViewById(R.id.device_display);

                newDevicesListView.setAdapter(mArrayAdapter);
                newDevicesListView.setClickable(true);
            }
        });

        getDevice();
        mArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_list);

        Button buttonRead = (Button) findViewById(R.id.button_read);
        buttonRead.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    if(socket!=null) {
                        InputStream inStream = socket.getInputStream();
                        readData(inStream);
                    }
                } catch (IOException e) {
                    //TODO
                }
            }
        });

        Button buttonWrite = (Button) findViewById(R.id.button_write);
        buttonWrite.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    if(socket!=null) {
                        OutputStream outStream = socket.getOutputStream();
                        sendCommand(outStream, "logapp");
                    }
                } catch (IOException e) {
                    //TODO
                }
            }
        });
    }

    /**
     * Some pieces of this method were taken from:
     * http://stackoverflow.com/questions/25443297/how-to-read-from-the-inputstream-of-a-bluetooth-on-android
     * Modifications were made to conform to the specifications of this app
     */
    public static void readData(InputStream inStream) {
        try {
            boolean eofFound = false;
            String downloadedStrings = new String();
            resetBuffers(); //Resets all buffers to take in new data

            while (!eofFound) {
                byte[] buffer = new byte[256];  //buffer store for the stream
                int bytes; // bytes returned from read()
                bytes = inStream.read(buffer);
                String readMessage = new String(buffer, 0, bytes);
                downloadedData.add(readMessage); //Add new strings to arraylist
                boolean check = check4eof(downloadedData);

                if (check) {
                    for (String printStr : downloadedData) {
                        downloadedStrings = downloadedStrings.concat(printStr);
                    }
                    parseData(downloadedStrings);
                    eofFound = true;
                }
            }
        }catch(Exception e){
            //TODO
        }
    }

    private static void resetBuffers(){
        downloadedData.clear();
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

    private void printAllData(){
        //print2BT(downloadedStrings + "\n");
        print2BT("Temperature: " + temperature.toString() + "\n");
        print2BT("Depth: " + depth.toString() + "\n");
        print2BT("Conductivity: " + conductivity.toString() + "\n");
        print2BT("Light: " + light.toString() + "\n");
        print2BT("Heading: " + heading.toString() + "\n");
        print2BT("Accelerometer X: " + accelX.toString() + "\n");
        print2BT("Accelerometer Y: " + accelY.toString() + "\n");
        print2BT("Accelerometer Z: " + accelZ.toString() + "\n");
        print2BT("Gyroscope X: " + gyroX.toString() + "\n");
        print2BT("Gyroscope Y: " + gyroY.toString() + "\n");
        print2BT("Gyroscope Z: " + gyroZ.toString());
    }

    public static void connect2device(BluetoothDevice mBluetoothAdapter) {
        socket = null;
        try {
            socket = mBluetoothAdapter.createRfcommSocketToServiceRecord(uuid);
            socket.connect();
        } catch (IOException e) { }
    }

    /**
     * Some of the contents of this method are found at:
     * http://stackoverflow.com/questions/9596663/how-to-make-items-clickable-in-list-view
     * Modifications were made to conform to the specifications of this app
     */
    private void getDevice(){
        ListView lv = (ListView) findViewById(R.id.device_display);
        lv.setAdapter(new ArrayAdapter<String> (this, R.layout.activity_bluetooth));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg, View view, int position, long id) {
                String address = (String) ((TextView) view).getText();
                for(String temp : address.split("\n")) {
                    address = temp; //Only get address, discard name
                }
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

                connect2device(device);
            }

        });
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

    private static boolean check4eof(ArrayList<String> inputString){
        String buffer = "";

        for (String printStr : inputString) {
            buffer = buffer.concat(printStr);
        }

        for(String splitVal : buffer.split(",")) {
            if (!(splitVal.equals("logapp" + '\n' + '\r' + '>'))) { //ignore the command data
                if(splitVal.equals("U+1F4A9")) return true; //check for the eof
            }
        }

        return false;
    }

    private static void parseData(String input){
        int dataType = 0;
        int curIndex = 0;
        String eof = "U+1F4A9";
        ArrayList<String> parsedData = new ArrayList<String>();

        for(String splitVal : input.split(",")){
            if(!(splitVal.equals("logapp" + '\n' + '\r' + '>'))) { //ignore the command data
                parsedData.add(splitVal);
                switch (dataType) {
                    case 0:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            temperature.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 1:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            depth.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 2:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            conductivity.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 3:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            light.add(Float.parseFloat(parsedData.get(curIndex)));
                        }

                        break;
                    case 4:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            heading.add(Float.parseFloat(parsedData.get(curIndex)));
                            //spinCompass(compass, Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 5:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            accelX.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 6:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            accelY.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 7:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            accelZ.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 8:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            gyroX.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 9:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            gyroY.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    case 10:
                        if (!(eof.equals(parsedData.get(curIndex)))) { //make sure we don't use the eof
                            gyroZ.add(Float.parseFloat(parsedData.get(curIndex)));
                        }
                        break;
                    default:

                        break;
                }
                curIndex++;
                dataType++;
                if (dataType > 10) dataType = 0; //reset data counter
            }
        }
    }

    private void print2BT(String theString){
        TextView bluetoothLog = (TextView) findViewById(R.id.bluetooth_log);
        bluetoothLog.append(theString);	//append the text into the EditText
        ((ScrollView)bluetoothLog.getParent()).fullScroll(View.FOCUS_DOWN);
    }

    /**
     * Much of this method was taken from:
     * https://developer.android.com/guide/topics/connectivity/bluetooth.html
     * Modifications were made to conform to the specifications of this app
     */
    private void setupBT(){
        int REQUEST_ENABLE_BT = 1;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
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