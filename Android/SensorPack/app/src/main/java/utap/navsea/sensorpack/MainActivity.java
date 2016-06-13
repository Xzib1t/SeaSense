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

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;

import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity  extends AppCompatActivity {
	private TextView serialReceivedText;
	private ImageView compass = null;
	private ImageView seaperch = null;
	private ArrayAdapter<String> mArrayAdapter;
	private ArrayAdapter<String> mCommandAdapter;
	private BluetoothSocket socket = Bluetooth.getSocket(); //We store the socket in the Bluetooth class
	private Dialog dialog;
	private Dialog dialogCommands;
	private FloatingActionButton fab = null;
	private FloatingActionButton fabRight = null;
	private SeekBar timeSlider = null;
	//Below UUID is the standard SSP UUID:
	//Also seen at https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
	private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		serialReceivedText=(TextView) findViewById(R.id.serialReceivedText);
		compass = (ImageView) findViewById(R.id.compass);  //get compass
		seaperch = (ImageView) findViewById(R.id.seaperch); //get seaperch
		mArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_list); //Storage for BT devices
		mCommandAdapter = new ArrayAdapter<String>(this, R.layout.device_list); //Storage for commands
		dialog = new Dialog(this); //Create dialog to hold BT device list
		dialogCommands = new Dialog(this); //Create dialog to hold command options
		fabRight = (FloatingActionButton) findViewById(R.id.fab_right); //Make navigational FAB
		fabRight.setVisibility(View.INVISIBLE); //Hide this FAB until BT device is selected

		fab = (FloatingActionButton) findViewById(R.id.fab); //FAB for displaying BT devices
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
				getDevice();
				displayList();
				fab.setVisibility(View.INVISIBLE); //Hide fab when we're done connecting
				fabRight.setVisibility(View.VISIBLE); //Show our navigational fab
			}
        });

		assert fabRight != null;
		fabRight.setOnClickListener(new View.OnClickListener() { //Fab for changing view
			@Override
			public void onClick(View view) {
				changeActivity(TempCondActivity.class); //Switches to TempCondActivity
				/*for(String print : Bluetooth.downloadedData)
					print2BT(print); //uncomment to show the received data*/
			}
		});

		FloatingActionButton fabTC = (FloatingActionButton) findViewById(R.id.fabTC);
		assert fabTC != null;
		fabTC.setOnClickListener(new View.OnClickListener() { //FAB for displaying list of commands
			@Override
			public void onClick(View view) {
				displayCommands(); //Show list of clickable commands
			}
		});

		timeSlider = (SeekBar)findViewById(R.id.time_slider);
		assert timeSlider != null;
		timeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if(!Bluetooth.getHeading().isEmpty()) { //If we have heading data we also have accelerometer data
					controlCompass(Bluetooth.getHeading(), timeSlider); //Show heading corresponding to time

					controlSeaperch(Bluetooth.getGyroX(), Bluetooth.getGyroY(),
							Bluetooth.getGyroZ(), timeSlider);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {

			}
		});
	}

	/**
	 * Send command to Bluno to start data transfer
	 * Receive data
	 */
	private void downloadRtData(){
		try {
			if (socket != null) {
				OutputStream outStream = socket.getOutputStream();
				Bluetooth.sendCommand(outStream, "logapp"); //Send logapp command to start data transfer
				InputStream inStream = socket.getInputStream();
				Bluetooth.readData(inStream, 12);
			}
		} catch (IOException e) {
			//TODO
		}
	}

	/**
	 * Send command to Bluno to start data transfer of SD card csv files
	 * Receive data
	 */
	private void downloadSdDump(){
		try {
			if (socket != null) {
				OutputStream outStream = socket.getOutputStream();
				Bluetooth.sendCommand(outStream, "sd_dd"); //Send sd_dd command to start data transfer
				InputStream inStream = socket.getInputStream();
				Bluetooth.readData(inStream, 11);
			}
		} catch (IOException e) {
			//TODO
		}
	}

	/**
	 * This method creates a list of paired Bluetooth devices
	 * in a dialog box and maxes the options clickable
	 */
	private void displayList(){
		mArrayAdapter.clear();
		setupBT();

		ListView newDevicesListView = (ListView)
				dialog.findViewById(R.id.device_list_display);

		newDevicesListView.setAdapter(mArrayAdapter);
		newDevicesListView.setClickable(true);
	}

	/**
	 * This method creates a list of commands in a dialog
	 * box and makes the options clickable
	 */
	private void displayCommands(){
		dialogCommands.setContentView(R.layout.command_list_popup);
		dialogCommands.setCancelable(true);
		dialogCommands.setTitle("Commands");

		ListView lv = (ListView) dialogCommands.findViewById(R.id.command_list_display);
		lv.setAdapter(new ArrayAdapter<String> (this, R.layout.command_list_popup));

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg, View view, int position, long id){
				try {
					if(socket!=null) {
						OutputStream outStream = socket.getOutputStream();
						if(position==13 || position==8){ //if logapp or sd_dd were pressed
							DownloadTask Download = new DownloadTask();
							if(position==13) Download.mode = "logapp";
							if(position==8) Download.mode = "sd_dd";
							Download.execute();
						}
						else Bluetooth.sendCommand(outStream, mCommandAdapter.getItem(position));
					}
					else{
						Snackbar.make(view, "Not connected", Snackbar.LENGTH_LONG)
								.setAction("Action", null).show();
					}
				}
				catch(IOException e){
					//TODO
				}
			}
		});

		dialogCommands.show();
		loadCommandPopup(mCommandAdapter); //populates popup with options

		ListView commandListView = (ListView)
				dialogCommands.findViewById(R.id.command_list_display);
		commandListView.setAdapter(mCommandAdapter);
		commandListView.setClickable(true);

		ProgressBar tempSpinner = (ProgressBar)dialogCommands.findViewById(R.id.progressBar1);
		tempSpinner.setVisibility(View.INVISIBLE); //Hide the progress bar while data isn't being downloaded
	}

	/**
	 * This method gets paired devices and stores them
	 */
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

	/**
	 * Intent code from
	 * http://stackoverflow.com/questions/6121797/android-how-to-change-layout-on-button-click
	 */
	void changeActivity(Class mClass){
		Intent intentApp = new Intent(MainActivity.this, mClass);
		MainActivity.this.startActivity(intentApp);
	}

	/**
	 * Some of the contents of this method are found at:
	 * http://stackoverflow.com/questions/9596663/how-to-make-items-clickable-in-list-view
	 * Modifications were made to conform to the specifications of this app
	 *
	 * This method is also responsible for creating a list of available
	 * Bluetooth devices within a dialog, then connecting to a device
	 * chosen by the user.
	 */
	private void getDevice(){
		dialog.setContentView(R.layout.device_list_popup);
		dialog.setCancelable(true);
		dialog.setTitle("Bluetooth Devices");
		dialog.show();
		ProgressBar tempSpinner = (ProgressBar)dialog.findViewById(R.id.progressBar);
		tempSpinner.setVisibility(View.INVISIBLE); //Hide the progress bar while we aren't connecting

		ListView lv = (ListView) dialog.findViewById(R.id.device_list_display);
		lv.setAdapter(new ArrayAdapter<String> (this, R.layout.device_list_popup));

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg, View view, int position, long id) {
				String address = (String) ((TextView) view).getText();
				for (String temp : address.split("\n")) {
					address = temp; //Only get address, discard name
				}
				BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
				//connect2device(device);
				new ConnectTask().execute(device);
			}
		});
	}

	/**
	 * This method opens a Bluetooth socket and connects the Android
	 * to the selected Bluetooth device from the getDevice() method
	 * @param mBluetoothAdapter
     */
	private void connect2device(BluetoothDevice mBluetoothAdapter) {
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
			Bluetooth.saveSocket(socket); //store socket
		} catch (IOException e) {
			socket = null; //reset socket if the connection fails
		}
	}

	/**
	 * This method loads the command ArrayAdapter with the
	 * options that the user can choose
	 * @param mCommandAdapter
	 */
	private void loadCommandPopup(ArrayAdapter<String> mCommandAdapter){
		mCommandAdapter.clear();
		mCommandAdapter.add("help");
		mCommandAdapter.add("#");
		mCommandAdapter.add("test");
		mCommandAdapter.add("rtc_get");
		mCommandAdapter.add("rtc_set");
		mCommandAdapter.add("sd_init");
		mCommandAdapter.add("sd_ls");
		mCommandAdapter.add("sd_cat");
		mCommandAdapter.add("sd_dd");
		mCommandAdapter.add("sd_append");
		mCommandAdapter.add("sd_create");
		mCommandAdapter.add("sd_del");
		mCommandAdapter.add("log");
		mCommandAdapter.add("logapp");
		mCommandAdapter.add("logfile");
		mCommandAdapter.add("reset");
	}

	/**
	 * This method rotates the compass image to a desired angle
	 * @param imageView
	 * @param angle
     */
	private void spinCompass(ImageView imageView, float angle){
		imageView.setRotation(angle);
	}

	/**
	 * This method rotates an image to angles corresponding to
	 * the accelerometer data
	 * @param imageView
	 * @param x
	 * @param y
	 * @param z
	 */
	private void rotateSeaperch(ImageView imageView, float x, float y, float z){
		imageView.setRotationX(x);
		imageView.setRotation(y);
		imageView.setRotationY(z);
	}

	/**
	 * Scales the seekbar to the size of heading data array
	 * Rotates the compass to the value correspoding to a time on the
	 * seekbar and prints this value to the screen
	 * @param heading
	 * @param slider
	 */
	private void controlCompass(ArrayList<Float> heading, SeekBar slider){
		print2BT(String.valueOf(heading.get(slider.getProgress())) + "\n");
		spinCompass(compass, heading.get(slider.getProgress()));
	}

	/**
	 * Rotates the seaperch to the values correspoding to a time on the
	 * @param accelX
	 * @param accelY
	 * @param accelZ
	 * @param slider
	 */
	private void controlSeaperch(ArrayList<Float> accelX, ArrayList<Float> accelY,
								 ArrayList<Float> accelZ, SeekBar slider){
		if(!accelX.isEmpty()) //If we have data
			slider.setMax(accelZ.size()-1); //Scale bar to size of data array
		rotateSeaperch(seaperch, accelX.get(slider.getProgress()),
				accelY.get(slider.getProgress()), accelZ.get(slider.getProgress()));
	}

	/**
	 * This method prints text to a text box on the MainActivity screen
	 * @param theString
     */
	private void print2BT(String theString){
		serialReceivedText.append(theString);		//append the text into the EditText
		((ScrollView)serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
	}

	/**
	 * This class handles downloading data in the background while displaying
	 * a loading bar.
	 */
	class ConnectTask extends AsyncTask<BluetoothDevice, Integer, String> {
		private ProgressBar spinner;

		@Override
		protected void onPreExecute() {
			spinner = (ProgressBar)dialog.findViewById(R.id.progressBar);
			spinner.setVisibility(View.VISIBLE);
		}
		@Override
		protected String doInBackground(BluetoothDevice...deviceArr) {
			BluetoothDevice device = deviceArr[0];
			connect2device(device);
			return "done";
		}
		@Override
		protected void onPostExecute(String result) {
			spinner = (ProgressBar)dialog.findViewById(R.id.progressBar);
			spinner.setVisibility(View.INVISIBLE);

			if(socket!=null) //Check if we're connected after an attempt
				Snackbar.make(dialog.findViewById(R.id.device_list_display), "Connected", Snackbar.LENGTH_LONG)
						.setAction("Action", null).show();

			else Snackbar.make(dialog.findViewById(R.id.device_list_display), "Connection attempt failed", Snackbar.LENGTH_LONG)
					.setAction("Action", null).show();
		}
	}

	/**
	 * This class handles downloading data in the background while displaying
	 * a loading bar.
	 */
	class DownloadTask extends AsyncTask<Integer, Integer, String> {
		private ProgressBar spinner;
		public String mode = "";
		@Override
		protected void onPreExecute() {
			spinner = (ProgressBar)dialogCommands.findViewById(R.id.progressBar1);
			spinner.setVisibility(View.VISIBLE);
		}
		@Override
		protected String doInBackground(Integer... params) {
			if(mode.equals("logapp")) {
				downloadRtData(); //TODO fix occasional download dropping
			}
			if(mode.equals("sd_dd")){
				downloadSdDump();
			}
			return "done";
		}
		@Override
		protected void onPostExecute(String result) {
			spinner = (ProgressBar)dialogCommands.findViewById(R.id.progressBar1);
			spinner.setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * Closes the Bluetooth socket
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			socket.close();
		}
		catch(IOException e){
			//TODO
		}
	}
}


