/*
 * Copyright 2016 Joseph Maestri
 *
 * The methods in this file were either taken directly/almost directly from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 * or created using the information from the above guide
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
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class Bluetooth extends AppCompatActivity{
    private ArrayAdapter<String> mArrayAdapter;
    private static BluetoothSocket socket = null;
    private static final UUID uuid = UUID.fromString("10101010-0101-0101-0F01-1F571A632AB9");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        Button mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mArrayAdapter.clear();
                setupBT();

                ListView newDevicesListView = (ListView)
                        findViewById(R.id.device_display);

                newDevicesListView.setAdapter(mArrayAdapter);
                newDevicesListView.setClickable(true);

                //connect2device(device);


            }
        });


        mArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_list);

        // Register the BroadcastReceiver
/*        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy*/

    }



    private void connect2device(BluetoothDevice mBluetoothAdapter) {
        socket = null;
        try {
            // MY_UUID is the app's UUID string, also used by the client code
            socket = mBluetoothAdapter.createRfcommSocketToServiceRecord(uuid);
            socket.connect();
        } catch (IOException e) { }
    }

    /**
     * The contents of this method are found at:
     * http://stackoverflow.com/questions/9596663/how-to-make-items-clickable-in-list-view
     * Modifications were made to conform to the specifications of this app
     */
    private void getDevice(){
        ListView lv = (ListView) findViewById(R.id.device_display);
        lv.setAdapter(new ArrayAdapter<String> (this, R.layout.activity_bluetooth));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg, View view, int position, long id) {
                // When clicked, show a toast with the TextView text
                Toast.makeText(getApplicationContext(), ((TextView) view).getText(),
                        Toast.LENGTH_SHORT).show();

            }

        });
    }

    private void setupBT(){
        int REQUEST_ENABLE_BT = 1;

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

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

    // https://developer.android.com/guide/topics/connectivity/bluetooth.html
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
