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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

public class Bluetooth extends AppCompatActivity{
    private static ArrayAdapter<String> mArrayAdapter;
    private static BluetoothSocket socket = null;
    //Below UUID is the standard SSP UUID:
    //Also seen at https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothAdapter mBluetoothAdapter;

    public static ArrayList<Float> temperature = new ArrayList<Float>();
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
    private static String downloadedStrings = new String();
    private static ArrayList<String> downloadedData = new ArrayList<String>();

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
                        writeData(outStream);
                    }
                } catch (IOException e) {
                    //TODO
                }
            }
        });
    }

    /**
     * Most of this method was taken from:
     * http://stackoverflow.com/questions/25443297/how-to-read-from-the-inputstream-of-a-bluetooth-on-android
     * Modifications were made to conform to the specifications of this app
     */
    public static void readData(InputStream inStream) {
        try {
            DataInputStream mmInStream = new DataInputStream(inStream);

            // Read from the InputStream

            boolean eofFound = false;
            //TODO reset downloadedData here

            while (!eofFound) {

                byte[] buffer = new byte[256];  // buffer store for the stream
                int bytes; // bytes returned from read()
                bytes = inStream.read(buffer);
                String readMessage = new String(buffer, 0, bytes);

                downloadedData.add(readMessage);

                boolean check = check4eof(downloadedData);

                if (check) {
                    for (String printStr : downloadedData) {
                        downloadedStrings = downloadedStrings.concat(printStr);
                    }
                    //print2BT(downloadedStrings + "\n");
                    parseData(downloadedStrings);

/*                    print2BT("Temperature: " + temperature.toString() + "\n");
                    print2BT("Depth: " + depth.toString() + "\n");
                    print2BT("Conductivity: " + conductivity.toString() + "\n");
                    print2BT("Light: " + light.toString() + "\n");
                    print2BT("Heading: " + heading.toString() + "\n");
                    print2BT("Accelerometer X: " + accelX.toString() + "\n");
                    print2BT("Accelerometer Y: " + accelY.toString() + "\n");
                    print2BT("Accelerometer Z: " + accelZ.toString() + "\n");
                    print2BT("Gyroscope X: " + gyroX.toString() + "\n");
                    print2BT("Gyroscope Y: " + gyroY.toString() + "\n");
                    print2BT("Gyroscope Z: " + gyroZ.toString());*/

                    eofFound = true;
                }
            }
        }catch(Exception e){
            //TODO
        }
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



    public static void writeData(OutputStream outStream){

        try {
            DataOutputStream mmOutStream = new DataOutputStream(outStream);
            mmOutStream.write(108); //This is "logapp" in ASCII code, it is the command
            mmOutStream.write(111); //that causes the Arduino side to start sending data
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

    private static boolean check4eof(ArrayList<String> inputString){
        /*String input = inputString.get(inputString.size() - 1); //Read last entry in array list
        char[] eof = {'U','+','1','F','4','A','9'};
        int eofLength = 7;
        char[] charCheck = new char[eofLength];
        if(input.length() >= eofLength){
            int iterate = 0;
            for(int i=input.length()-eofLength; i<input.length(); i++){
                charCheck[iterate] = input.charAt(i);
                iterate++;
            }

            if(Arrays.equals(charCheck,eof)) return true;

        }*/

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
        bluetoothLog.append(theString);		//append the text into the EditText
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