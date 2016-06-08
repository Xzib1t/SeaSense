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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.content.Intent;
import android.support.v7.widget.ListPopupWindow;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import utap.navsea.sensorpack.Bluetooth;

public class MainActivity  extends AppCompatActivity {
	private Button buttonScan;
	private Button buttonSerialSend;
	private EditText serialSendText;
	private TextView serialReceivedText;
	public LineChart chart1 = null;
	public LineChart chart2 = null;
	public ImageView compass = null;
	private float angle = 0;
	private Context context;
	private Activity activity;
	private View view;
	private int serialCount = 0;
	private String placeHolder = "Empty";
	private String lastFlag = "Empty";
	private int dataTypeIndex = 0;
	private int dataCounter = 0; //determines if disp, temp, cond...etc
	private int dataCount = 0; //individual data counters
	private ArrayList<Float> temperature = new ArrayList<Float>();
	private ArrayList<Float> depth = new ArrayList<Float>();
	private ArrayList<Float> conductivity = new ArrayList<Float>();
	private ArrayList<Float> light = new ArrayList<Float>();
	private ArrayList<Float> heading = new ArrayList<Float>();
	private ArrayList<Float> accelX = new ArrayList<Float>();
	private ArrayList<Float> accelY = new ArrayList<Float>();
	private ArrayList<Float> accelZ = new ArrayList<Float>();
	private ArrayList<Float> gyroX = new ArrayList<Float>();
	private ArrayList<Float> gyroY = new ArrayList<Float>();
	private ArrayList<Float> gyroZ = new ArrayList<Float>();
	private String downloadedStrings = new String();
	private ArrayList<String> downloadedData = new ArrayList<String>();

	private static ArrayAdapter<String> mArrayAdapter;
	private static BluetoothAdapter mBluetoothAdapter;

	private Dialog dialog;
	private static BluetoothSocket socket = null;
	private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        ArrayList<String> downloadedData = new ArrayList<String>();								//onCreate Process by BlunoLibrary

        serialReceivedText=(TextView) findViewById(R.id.serialReceivedText);	//initial the EditText of the received data
        //serialSendText=(EditText) findViewById(R.id.serialSendText);			//initial the EditText of the sending data

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		chart1 = (LineChart) findViewById(R.id.chart); //get the first chart
		chart2 = (LineChart) findViewById(R.id.chart1); //get the second chart
		compass = (ImageView) findViewById(R.id.compass);  //get compass
		Float[] data = {80f, 255f, 3f, 4f, 200f, 150f, 125f};
		Float[] data1 = {200f, 100f, 60f, 89f};
		ArrayList<Entry> entries = loadArray(data);
		ArrayList<Entry> entries1 = loadArray(data1);
		graphTest(chart1, entries, "Temperature (Deg C)", Color.RED);
		graphTest(chart2, entries1, "Watermelons", Color.GREEN);
		//setSupportActionBar(toolbar);

		mArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_list);
		dialog = new Dialog(this);


		FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Getting BT device list", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();


				getDevice();
				displayList();
				dialog.setContentView(R.layout.device_list_popup);

				//ListView lv = (ListView ) dialog.findViewById(R.id.device_list_display2);
				dialog.setCancelable(true);
				dialog.setTitle("BluetoothDevices");
				dialog.show();
			}
        });


		/**
		 * Intent code from
		 * http://stackoverflow.com/questions/6121797/android-how-to-change-layout-on-button-click
		 */
		FloatingActionButton fabTC = (FloatingActionButton) findViewById(R.id.fabTC);
		assert fabTC != null;
		fabTC.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//Intent intentApp = new Intent(MainActivity.this, TempCondActivity.class);
				Intent intentApp = new Intent(MainActivity.this, Bluetooth.class);
				MainActivity.this.startActivity(intentApp);
/*				try {
					if(socket!=null) {
						OutputStream outStream = socket.getOutputStream();
						utap.navsea.sensorpack.Bluetooth.writeData(outStream);
						InputStream inStream = socket.getInputStream();
						utap.navsea.sensorpack.Bluetooth.readData(inStream);
						graphTest(chart1, convert2Entry(temperature), "Temp", Color.RED);
					}
				} catch (IOException e) {
					//TODO
				}*/
			}
		});

	}

	private void displayList(){
		mArrayAdapter.clear();
		setupBT();

		ListView newDevicesListView = (ListView)
				findViewById(R.id.device_list_display);

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
	 * Some of the contents of this method are found at:
	 * http://stackoverflow.com/questions/9596663/how-to-make-items-clickable-in-list-view
	 * Modifications were made to conform to the specifications of this app
	 */
	private void getDevice(){
		ListView lv = (ListView) findViewById(R.id.device_list_display);
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

	private void delay(int time){
		try {
			Thread.sleep(time);
		} catch(InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
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

	private float randNumGen(){ //http://stackoverflow.com/questions/5271598/java-generate-random-number-between-two-given-values
		Random r = new Random();
		int Low = 0;
		int High = 360;
		int Result = r.nextInt(High-Low) + Low;
		return Result;
	}

	private void spinCompass(ImageView imageView, float angle){
		imageView.setRotation(angle);
	}

	private void print2BT(String theString){
		serialReceivedText.append(theString);		//append the text into the EditText
		((ScrollView)serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
	}

	/**
	 * Parse csv data sent by Arduino
	 */
	private void parseData(String input){
		int dataType = 0;
		int curIndex = 0;
		String eof = " U+1F4A9";
		ArrayList<String> parsedData = new ArrayList<String>();

		for(String splitVal : input.split(",")){
			parsedData.add(splitVal);

			switch(dataType){
				case 0:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						temperature.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 1:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						depth.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 2:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						conductivity.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 3:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						light.add(Float.parseFloat(parsedData.get(curIndex)));
					}

					break;
				case 4:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						heading.add(Float.parseFloat(parsedData.get(curIndex)));
						spinCompass(compass, Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 5:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						accelX.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 6:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						accelY.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 7:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						accelZ.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 8:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						gyroX.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 9:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						gyroY.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				case 10:
					if( !(eof.equals( parsedData.get(curIndex) ) ) ){ //make sure we don't use the eof
						gyroZ.add(Float.parseFloat(parsedData.get(curIndex)));
					}
					break;
				default:

					break;
			}
			curIndex++;
			dataType++;
			if(dataType>10) dataType = 0; //reset data counter
		}
	}

	private void downloadData(String input){
		//ArrayList<String> downloadedData = new ArrayList<String>();
		downloadedData.add(input);
	}

	private boolean check4eof(ArrayList<String> inputString){
		String input = inputString.get(inputString.size() - 1);
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
		}
		return false;
	}

	private ArrayList<Entry> convert2Entry(ArrayList<Float> input){
		Float[] floatArray = input.toArray(new Float[input.size()]);
		ArrayList<Entry> entryArray = loadArray(floatArray);
		return entryArray;
	}

	public void onSerialReceived(String theString) {	//Once connection data received, this function will be called
		//The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.
		downloadData(theString);

		boolean check = check4eof(downloadedData);

		if(check){
			for (String printStr : downloadedData) {
				downloadedStrings = downloadedStrings.concat(printStr);
			}
			parseData(downloadedStrings);
			graphTest(chart1, convert2Entry(temperature), "Temperature data", Color.RED);
			graphTest(chart2, convert2Entry(light), "Light data", Color.GREEN);

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


