/*
 * Copyright (C) 2013 The Android Open Source Project
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

/**
 * Several of the methods in this file were copied directly from
 * https://github.com/DFRobot/BlunoBasicDemo/blob/master/Android/BlunoBasicDemo/app/src/main/java/com/dfrobot/angelo/blunobasicdemo/MainActivity.java
 */

package utap.navsea.sensorpack;

import android.content.Context;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MainActivity  extends BlunoLibrary {
	private Button buttonScan;
	private Button buttonSerialSend;
	private EditText serialSendText;
	private TextView serialReceivedText;

	public LineChart chartTemp = null;
	public LineChart chartGyroX = null;
	public ImageView compass = null;
	private float angle = 0;
	private Context context;
	private Activity activity;
	private static final int PERMISSION_REQUEST_CODE = 1;
	private View view;

	private int serialCount = 0;
	private String placeHolder = "Empty";
	private String lastFlag = "Empty";

	private int dataTypeIndex = 0;
	private int dataCounter = 0; //determines if disp, temp, cond...etc
	private int dataCount = 0; //individual data counters
	private Float[] temperature = {0f};
	private Float[] depth = {0f};
	private Float[] conductivity = {0f};
	private Float[] light = {0f};
	private Float[] heading = {0f};
	private Float[] accelX = {0f};
	private Float[] accelY = {0f};
	private Float[] accelZ = {0f};
	private Float[] gyroX = {0f};
	private Float[] gyroY = {0f};
	private Float[] gyroZ = {0f};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
        onCreateProcess();														//onCreate Process by BlunoLibrary

        serialBegin(115200);													//set the Uart Baudrate on BLE chip to 115200

        serialReceivedText=(TextView) findViewById(R.id.serialReceivedText);	//initial the EditText of the received data
        //serialSendText=(EditText) findViewById(R.id.serialSendText);			//initial the EditText of the sending data

        //buttonSerialSend = (Button) findViewById(R.id.buttonSerialSend);		//initial the button for sending the data
        //buttonSerialSend.setOnClickListener(new OnClickListener() {

/*			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub

				serialSend(serialSendText.getText().toString());				//send the data to the BLUNO
			}
		});

        //buttonScan = (Button) findViewById(R.id.buttonScan);					//initial the button for scanning the BLE device
        buttonScan.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub

				buttonScanOnClickProcess();										//Alert Dialog for selecting the BLE device
			}
		});*/

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		chartTemp = (LineChart) findViewById(R.id.chart); //get the first chart
		chartGyroX = (LineChart) findViewById(R.id.chart1); //get the second chart
		compass = (ImageView) findViewById(R.id.compass);  //get compass
		Float[] data = {80f, 255f, 3f, 4f, 200f, 150f, 125f};
		Float[] data1 = {200f, 100f, 60f, 89f};
		ArrayList<Entry> entries = loadArray(data);
		ArrayList<Entry> entries1 = loadArray(data1);
		graphTest(chartTemp, entries, "Temperature (Deg C)", Color.RED);
		graphTest(chartGyroX, entries1, "Watermelons", Color.GREEN);
		//setSupportActionBar(toolbar);

		FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Compass simulation running", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
				buttonScanOnClickProcess();										//Alert Dialog for selecting the BLE device

                //spinCompass(compass,randNumGen());
/*                  for(int i=0; i<10; i++) {
                    angle = randNumGen();
                    spinCompass(compass, angle);
                    System.out.println(angle);
                    //delay(1000);
                }*/
            }
        });

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

	/**
	 * Save incoming data from sensor pack
	 * This method is a placeholder for testing until
	 * the sensor pack can save data by itself, then dump it here
	 */
	private void saveData(){

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


	protected void onResume(){
		super.onResume();
		System.out.println("BlUNOActivity onResume");
		onResumeProcess();														//onResume Process by BlunoLibrary
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		onActivityResultProcess(requestCode, resultCode, data);					//onActivityResult Process by BlunoLibrary
		super.onActivityResult(requestCode, resultCode, data);
	}
	
    @Override
    protected void onPause() {
        super.onPause();
        onPauseProcess();														//onPause Process by BlunoLibrary
    }
	
	protected void onStop() {
		super.onStop();
		onStopProcess();														//onStop Process by BlunoLibrary
	}
    
	@Override
    protected void onDestroy() {
        super.onDestroy();	
        onDestroyProcess();														//onDestroy Process by BlunoLibrary
    }

	@Override
	public void onConectionStateChange(connectionStateEnum theConnectionState) {//Once connection state changes, this function will be called
		switch (theConnectionState) {											//Four connection state
		case isConnected:
			buttonScan.setText("Connected");
			break;
		case isConnecting:
			buttonScan.setText("Connecting");
			break;
		case isToScan:
			buttonScan.setText("Scan");
			break;
		case isScanning:
			buttonScan.setText("Scanning");
			break;
		case isDisconnecting:
			buttonScan.setText("isDisconnecting");
			break;
		default:
			break;
		}
	}

	private void print2BT(String theString){
		serialReceivedText.append(theString);		//append the text into the EditText
		((ScrollView)serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
	}

	/**
	 * Parse csv data sent by Arduino
	 */
	private ArrayList<String> parseData(String input){
		//TODO change this to deal with an array of strings so that all data can be processed once downloaded
		ArrayList<String> parsedData = new ArrayList<String>();
		for(String splitVal : input.split(",")){
			parsedData.add(splitVal);
		}

		return parsedData;
	}


	@Override
	public void onSerialReceived(String theString) {							//Once connection data received, this function will be called
		// TODO Auto-generated method stub
		String flag = theString;
		//The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.

		int dataSize = 44; //let's pretend we are sent the length of the file

		//When we call this by sending a control flag from the Android, the data should
		//stay in the same order, but right now it won't because it is constantly printing

		ArrayList<String[]> data = parseData(theString);
		for(int i=0; i<20; i++){
			for (String value : data) {
				print2BT("Value: " + value);
			}
		}

	/*	if(!flag.equals(",")){ //when actual data is sent
			switch(dataTypeIndex){
				case 0:
					temperature[dataCount] = Float.parseFloat(flag);
					break;
				case 1:
					depth[dataCount] = Float.parseFloat(flag);
					break;
				case 2:
					conductivity[dataCount] = Float.parseFloat(flag);
					break;
				case 3:
					light[dataCount] = Float.parseFloat(flag);
					break;
				case 4:
					heading[dataCount] = Float.parseFloat(flag);
					break;
				case 5:
					accelX[dataCount] = Float.parseFloat(flag);
					break;
				case 6:
					accelY[dataCount] = Float.parseFloat(flag);
					break;
				case 7:
					accelZ[dataCount] = Float.parseFloat(flag);
					break;
				case 8:
					gyroX[dataCount] = Float.parseFloat(flag);
					break;
				case 9:
					gyroY[dataCount] = Float.parseFloat(flag);
					break;
				case 10:
					gyroZ[dataCount] = Float.parseFloat(flag);
					break;
				default:

					break;
			}
			dataTypeIndex++;
			if(dataTypeIndex>=11) dataTypeIndex = 0;
			dataCounter++;
			if(dataCounter>=(dataSize-1)) {
				dataCounter = 0;

				ArrayList<Entry> temps = loadArray(temperature);
				graphTest(chartTemp, temps, "Temperature", Color.RED);
			}
		}*/

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


