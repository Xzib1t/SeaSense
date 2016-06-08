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
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity  extends AppCompatActivity {
	private TextView serialReceivedText;
	public LineChart chart1 = null;
	public LineChart chart2 = null;
	public ImageView compass = null;
	private float angle = 0;
	private static ArrayAdapter<String> mArrayAdapter;
	private static BluetoothAdapter mBluetoothAdapter;
	private Dialog dialog;
	private static BluetoothSocket socket = null;
	private FloatingActionButton fab = null;
	private FloatingActionButton fabRight = null;
	private ProgressBar spinner;
	//Below UUID is the standard SSP UUID:
	//Also seen at https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
	private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		spinner = (ProgressBar)findViewById(R.id.progressBar1); //loading bar
		spinner.setVisibility(View.INVISIBLE);

        //ArrayList<String> downloadedData = new ArrayList<String>(); //onCreate Process by BlunoLibrary

		//Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		chart1 = (LineChart) findViewById(R.id.chart); //get the first chart
		chart2 = (LineChart) findViewById(R.id.chart1); //get the second chart
		compass = (ImageView) findViewById(R.id.compass);  //get compass
		//setSupportActionBar(toolbar);

		mArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_list);
		dialog = new Dialog(this);

		fabRight = (FloatingActionButton) findViewById(R.id.fab_right); //Make navigational FAB
		fabRight.setVisibility(View.INVISIBLE); //Hide this FAB until BT device is selected

		fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Getting BT device list", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
				getDevice();
				displayList();
				fab.setVisibility(View.INVISIBLE); //Hide fab when we're done connecting
				fabRight.setVisibility(View.VISIBLE); //Show our navigational fab
			}
        });

		assert fabRight != null;
		fabRight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				changeActivity(TempCondActivity.class);
			}
		});

		FloatingActionButton fabTC = (FloatingActionButton) findViewById(R.id.fabTC);
		assert fabTC != null;
		fabTC.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if(socket!=null) {
					spinner.setVisibility(View.VISIBLE); //This doesn't display until click event exit
					downloadData();
					spinner.setVisibility(View.INVISIBLE);
				}

			}
		});
	}

	private void downloadData(){
		try {
			if (socket != null) {
				OutputStream outStream = socket.getOutputStream();
				Bluetooth.writeData(outStream);
				InputStream inStream = socket.getInputStream();
				Bluetooth.readData(inStream);
				graphTest(chart1, convert2Entry(Bluetooth.getTemp()), "Temp", Color.RED);
				chart1.invalidate(); //Refresh graph
				graphTest(chart2, convert2Entry(Bluetooth.getLight()), "Light", Color.GREEN);
				chart2.invalidate(); //refresh graph
			}
		} catch (IOException e) {
			//TODO
		}
	}

	private void displayList(){
		mArrayAdapter.clear();
		setupBT();

		ListView newDevicesListView = (ListView)
				dialog.findViewById(R.id.device_list_display);

		newDevicesListView.setAdapter(mArrayAdapter);
		newDevicesListView.setClickable(true);
	}

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
	 */
	private void getDevice(){
		dialog.setContentView(R.layout.device_list_popup);
		dialog.setCancelable(true);
		dialog.setTitle("Bluetooth Devices");

		ListView lv = (ListView) dialog.findViewById(R.id.device_list_display);
		lv.setAdapter(new ArrayAdapter<String> (this, R.layout.device_list_popup));

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg, View view, int position, long id) {
				String address = (String) ((TextView) view).getText();
				for (String temp : address.split("\n")) {
					address = temp; //Only get address, discard name
				}
				BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);

				connect2device(device);
			}
		});

		dialog.show();
	}

	private void connect2device(BluetoothDevice mBluetoothAdapter) {
		socket = null;
		try {
			socket = mBluetoothAdapter.createRfcommSocketToServiceRecord(uuid);
			socket.connect();
		} catch (IOException e) { }
	}


	private ArrayList<Entry> loadArray(Float[] data){
		ArrayList<Entry> entries = new ArrayList<>(); //figure out how to index values
		for(int i=0; i<data.length; i++){
			entries.add(i, new Entry(data[i], i));
		}
		return entries;
	}

	private void graphTest(LineChart chart, ArrayList<Entry> yData, String dataLabel, int color){
		if(yData != null) {
			ArrayList<Entry> tempC = yData;//new ArrayList<Entry>();

			LineDataSet setTempC = new LineDataSet(tempC, dataLabel);
			setTempC.setAxisDependency(YAxis.AxisDependency.LEFT);
			setTempC.setColor(color);

			formatChart(chart); //colors, lines...etc

			ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
			dataSets.add(setTempC);

			ArrayList xVals = setupXaxis(tempC); //Scale x axis to incoming data

			ArrayList<String> yVals = new ArrayList<String>();
			yVals.add("Data");

			LineData data = new LineData(xVals, dataSets);
			chart.setData(data);
			chart.invalidate(); // refresh
		}
	}

	private ArrayList<String> setupXaxis(ArrayList<Entry> tempC){
		ArrayList<String> xVals = new ArrayList<String>();
		String[] tempXvals = new String[tempC.size()];

		for(int i=0; i<tempC.size(); i++) {
			tempXvals[i] = Integer.toString(i);
		}
		for(int j=0; j<tempXvals.length; j++){
			xVals.add(tempXvals[j]);
		}
		return xVals;
	}

	private void formatChart(LineChart chart){
		chart.setEnabled(true);
		chart.setTouchEnabled(true);
		chart.setDescription("Data over 24 hours");

		XAxis xAxis = chart.getXAxis();
		xAxis.setEnabled(true);
		xAxis.setDrawAxisLine(true);
		xAxis.setDrawGridLines(true);
		xAxis.setDrawLabels(true);
		xAxis.setGridColor(Color.BLACK);
		xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
		xAxis.setTextSize(10f);
		xAxis.setTextColor(Color.BLACK);

		YAxis leftAxis = chart.getAxisLeft();
		leftAxis.setTextColor(Color.BLACK);
		leftAxis.setAxisLineColor(Color.BLACK);
		leftAxis.setEnabled(true);

		chart.setDrawGridBackground(true);
		chart.setDrawBorders(true);
		chart.setBorderColor(Color.BLACK);
	}

	private void spinCompass(ImageView imageView, float angle){
		imageView.setRotation(angle);
	}

	private void print2BT(String theString){
		serialReceivedText.append(theString);		//append the text into the EditText
		((ScrollView)serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
	}


	private ArrayList<Entry> convert2Entry(ArrayList<Float> input){
		Float[] floatArray = input.toArray(new Float[input.size()]);
		ArrayList<Entry> entryArray = loadArray(floatArray);
		return entryArray;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			socket.close();
		}
		catch(IOException e){
			//TODO
			//Snackbar.make(view, "Getting BT device list", Snackbar.LENGTH_LONG)
			//.setAction("Action", null).show();
		}
	}

	/**
	 * This method only works when used with a BLE chip (connecting to the Bluno works), not
	 * regular Bluetooth
     */
	public void onSerialReceived(String theString) {	//Once connection data received, this function will be called

		//uncomment below code to run regular program
/*		if(lastFlag.equals("Gyro")){
			//print2BT(theString);
			if(!theString.equals("Gyro")){ //double check that this is the data we want
				Float[] temp = {20f, 21f, 22f, 23f};
				ArrayList<Entry> temps = loadArray(temp);
				graphTest(chartTemp, temps, "Temp", Color.RED);
				if(theString.equals("X")){
					
				}
			}
			lastFlag = "Empty"; //not really necessary
		}
		if(lastFlag.equals("ADXL")){
			print2BT(theString);
			if(!theString.equals("ADXL")){ //double check that this is the data we want

			}
			lastFlag = "Empty"; //not really necessary
		}
		if(lastFlag.equals("Compass")){
			//print2BT(theString);
			if(!theString.equals("Compass")){ //&& !theString.equals("ADXL") && !theString.equals("Gyro")){ //double check that this is the data we want
				angle = Float.parseFloat(theString);
				spinCompass(compass, angle);
			}
			lastFlag = "Empty"; //not really necessary
		}

		switch(flag) {
			case "Gyro":
				//print2BT("Gyro ");
				//print2BT(theString);
				lastFlag = "Gyro";
				break;
			case "ADXL":
				//print2BT("ADXL ");
				lastFlag = "ADXL";
				break;
			case "Compass":
				//print2BT("Compass");
				lastFlag = "Compass";
				break;
			default:
				//spinCompass(compass, 0);
				break;
		}*/
	}
}


