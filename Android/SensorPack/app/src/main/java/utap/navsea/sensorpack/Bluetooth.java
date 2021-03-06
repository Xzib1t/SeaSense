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
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class Bluetooth extends AppCompatActivity{
    //Below UUID is the standard SSP UUID:
    //Also seen at https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
    private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothSocket socket = null; //We store the socket in the Bluetooth class

    /**
     * This method gets paired devices and stores them
     *
     * Much of this method was taken from:
     * https://developer.android.com/guide/topics/connectivity/bluetooth.html
     * Modifications were made to conform to the specifications of this app
     */
    public void setupBT(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                MainActivity.addBtDevice(device.getName(), device.getAddress());
            }
        }
    }

    /**
     * This method opens a Bluetooth socket and connects the Android
     * to the selected Bluetooth device from the getDevice() method
     * @param mBluetoothAdapter adapter that holds BT devices
     */
    public void connect2device(BluetoothDevice mBluetoothAdapter) {
        if(socket!=null){
            try{
                socket.close(); //try to clear the socket
            }
            catch(IOException e){
                socket = null;
            }
        }
        try {
            socket = mBluetoothAdapter.createRfcommSocketToServiceRecord(uuid);
            socket.connect();
            Parser.saveSocket(socket); //store socket
            MainActivity.saveSocket(socket); //store socket
        } catch (IOException e) {
            socket = null; //reset socket if the connection fails
        }
    }

    public static boolean isStillConnected(){
        try {
            int testData = 13;
            OutputStream oStream = socket.getOutputStream();
            oStream.flush();
            oStream.write(testData);
        } catch (IOException | NullPointerException e) {
            System.out.println("Exception thrown, not connected");
            try {
                socket.close();
            } catch (IOException | NullPointerException e1) {
                return false;
            }
            return false;
        }
        return true;
    }

    /**
     * Returns Bluetooth socket on request
     * @return socket this is the Bluetooth socket
     */
    public BluetoothSocket getSocket() {return socket;}
}
