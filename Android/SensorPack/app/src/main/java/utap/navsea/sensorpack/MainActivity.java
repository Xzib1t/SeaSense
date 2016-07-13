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
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
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
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;

public class MainActivity  extends AppCompatActivity {
	private TextView timeDisplay;
	private ImageView compass = null;
	private ImageView seaperch = null;
	private static ArrayAdapter<String> mArrayAdapter;
    private ArrayAdapter<String> mFileNameAdapter;
	private static BluetoothSocket socket;
	private Dialog dialog;
	private Dialog fileDialog;
	private SeekBar timeSlider = null;
    private boolean rtThreadRunning = false;
	private static int btnPressCount = 0;
    private static int btnLogCount = 0;
    public static  DownloadTask Download;
    private static Bluetooth BT = new Bluetooth();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        socket = BT.getSocket(); //We use the socket from the Bluetooth class
        timeDisplay=(TextView) findViewById(R.id.time_display);
        timeDisplay.setTextSize(80f); //Set font size for clock
        timeDisplay.setGravity(Gravity.CENTER_HORIZONTAL); //This is done here because wrap_content was used for the height
        timeDisplay.setTextColor(Color.RED);
		compass = (ImageView) findViewById(R.id.compass);  //get compass
		seaperch = (ImageView) findViewById(R.id.seaperch); //get seaperch
		mArrayAdapter = new ArrayAdapter<>(this, R.layout.device_list); //Storage for BT devices
        mFileNameAdapter = new ArrayAdapter<>(this, R.layout.device_list); //Storage for filenames
		dialog = new Dialog(this); //Create dialog to hold BT device list
		fileDialog = new Dialog(this); //Create a dialog to show the files on the SD card
        FloatingActionButton fabReset = (FloatingActionButton) findViewById(R.id.fab_reset);
        FloatingActionButton fabLogfile = (FloatingActionButton) findViewById(R.id.fab_logfile);
		FloatingActionButton fabRight = (FloatingActionButton) findViewById(R.id.fab_right); //Make navigational FAB
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab); //FAB for displaying BT devices
        final Button rtButton = (Button) findViewById(R.id.rtbutton_main);

        setupSwipeDetector(); //Setup swipe detector so we can swipe to change views
        syncDisplay(rtButton);

        final DisplayObject display = new DisplayObject();
        final DataObject data = new DataObject();
        data.addObserver(display);

		rtButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
                rtButton.setText(getResources().getString(R.string.start_dl_rt));
                btnPressCount++;
                syncButton(rtButton, data);
			}
		});

        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
				getDevice();
				displayList();
			}
		});

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { //Deal with the buttons if we connect
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if(socket!=null)
                    if(isStillConnected()) {
                        rtButton.setVisibility(View.VISIBLE); //Only show the button if we're connected
                    }
            }
        });

		fileDialog.setOnDismissListener(new DialogInterface.OnDismissListener() { //Deal with the buttons if we connect
			@Override
			public void onDismiss(DialogInterface dialogInterface) {
				System.out.println("Dialog dismissed");
				Parser.dialogOpen = false;
                if(Download!=null) Download.cancel(true);
			}
		});

		assert fabRight != null;
		fabRight.setOnClickListener(new View.OnClickListener() { //Fab for changing view
			@Override
			public void onClick(View view) {
                if((btnPressCount % 2) == 0) {
                    flushStream();
                    changeActivity(TempCondActivity.class); //Switches to TempCondActivity
                }
                else Snackbar.make(view, "Stop real time display before changing screens",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
			}
		});

        assert fabReset != null;
        fabReset.setOnClickListener(new View.OnClickListener() { //Fab for changing view
            @Override
            public void onClick(View view) {
                if (socket != null && isStillConnected()) {
                    try {
                        OutputStream outStream = socket.getOutputStream();
                        Commands.sendCommand(outStream, "reset", "");
                        Snackbar.make(view, "Arduino reset",
                                Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    } catch (IOException e) {
                        Log.d("IOException", "Exception sending reset command");
                    }
                }
                else{
                    Snackbar.make(view, "Not connected",
                            Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });


        assert fabLogfile != null;
        fabLogfile.setOnClickListener(new View.OnClickListener() { //Fab for changing view
            @Override
            public void onClick(View view) {
                if (socket != null && isStillConnected()) {
                    try {
                        btnLogCount++;
                        OutputStream outStream = socket.getOutputStream();
                        Commands.sendCommand(outStream, "logfile", "");
                        if((btnLogCount % 2) != 0) //If it's odd we're logging
                            Snackbar.make(view, "Logging sensor data, press again to stop logging",
                                    Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        else Snackbar.make(view, "Stopped logging sensor data, press again to start logging" +
                                " to a new file",
                                Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    } catch (IOException e) {
                        Log.d("IOException", "Exception sending logfile");
                    }
                }
                else{
                    Snackbar.make(view, "Not connected",
                            Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });

		FloatingActionButton fabTC = (FloatingActionButton) findViewById(R.id.fabTC);
		assert fabTC != null;
		fabTC.setOnClickListener(new View.OnClickListener() { //FAB for displaying list of commands
			@Override
			public void onClick(View view) {
                if (socket != null && socket.isConnected())
                    displayFiles();
                else Snackbar.make(view, "Not connected",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
		});

		timeSlider = (SeekBar)findViewById(R.id.time_slider);
		assert timeSlider != null;
		timeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if(!Parser.getHeading().isEmpty() && !rtThreadRunning) { //If we have heading data we also have accelerometer data, make sure we aren't pulling rt data
					controlCompass(Parser.getHeading(), timeSlider); //Show heading corresponding to time

					controlSeaperch(Parser.getGyroX(), Parser.getGyroY(),
							Parser.getGyroZ(), timeSlider);

					controlClock(Parser.getTime(), timeSlider);
				}
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
	}

    private void setupSwipeDetector(){
        //Swipe detector code from http://stackoverflow.com/questions/937313/fling-gesture-detection-on-grid-layout
        ActivitySwipeDetector activitySwipeDetector = new ActivitySwipeDetector(this);
        activitySwipeDetector.setDestinations(DepthLightActivity.class, TempCondActivity.class);
        LinearLayout layout = (LinearLayout) findViewById(R.id.full_screen_main);
        layout.setOnTouchListener(activitySwipeDetector);
    }

    private void flushStream(){
        try {
            if(socket!=null)
            socket.getInputStream().skip(socket.getInputStream().available());
        } catch (IOException e) {
            System.out.println("Flush failed");
        }
    }

    /**
     * This is a workaround for detecting if we are
     * getting data or not, since reading from an InputStream is blocking
     * The solution in this method for sidestepping the blocking aspect of the read method came from:
     * http://stackoverflow.com/questions/14023374/making-a-thread-which-cancels-a-inputstream-read-call-if-x-time-has-passed
     * @return If data is being received, returns true, else false
     */
    private boolean isGettingData(){
        int available;
        long startTime = System.nanoTime();
        int timeout = 100;
        InputStream inStream = null;
        flushStream();
        try {
            if(socket!=null)
                inStream = socket.getInputStream();
            Thread.sleep(100); //The Arduino keeps sending data for 100ms after it is told to stop
        }catch(IOException | InterruptedException e){}
        try {
            while (true) {
                available = inStream.available();
                if (available > 0) {
                    return true;
                }
                Thread.sleep(1);
                long estimatedTime = System.nanoTime() - startTime;
                long estimatedMillis = TimeUnit.MILLISECONDS.convert(estimatedTime,
                        TimeUnit.NANOSECONDS);
                if (estimatedMillis > timeout){
                    return false; //timeout, no data coming
                }
            }
        }catch(IOException | InterruptedException | NullPointerException e){
            Log.d("Exception", "Exception");
        }
        return false;
    }

    /**
     * Make sure our button state is consistent with what's happening
     * on the Arduino side
     * @param rtButton This is the button for downloading real time data, we change its text in this method
     * @param data This is the data object for incoming data
     */
    private void syncButton(Button rtButton, DataObject data){
        boolean gettingData = isGettingData(); //Check if we're getting data
        if((btnPressCount % 2)!=0 && !gettingData) { //We want data and aren't getting it yet
            rtButton.setText(getResources().getString(R.string.stop_dl_rt));
            sendLogApp(); //Need to send logapp to start data transfer
            startRtDownload(data);
        }
        if((btnPressCount % 2)!=0 && gettingData) { //We want data and are already getting it
            rtButton.setText(getResources().getString(R.string.stop_dl_rt));
            startRtDownload(data); //Don't need to send logapp, data already incoming
        }
        if((btnPressCount % 2)==0 && gettingData) { //We want to stop getting data and are still getting it
            rtButton.setText(getResources().getString(R.string.start_dl_rt));
            sendLogApp(); //Need to send logapp to stop data transfer
        }
        if((btnPressCount % 2)==0 && !gettingData) { //We want to stop getting data but we already aren't getting it
            rtButton.setText(getResources().getString(R.string.start_dl_rt));
            //Don't need to send logapp, data transfer already stopped
        }
    }

    private boolean isStillConnected(){
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

    private void syncDisplay(Button rtButton){
        if(socket==null){
            showInstructions();
            rtButton.setVisibility(View.INVISIBLE);
        }
        else if(!isStillConnected()){
            showInstructions();
            rtButton.setVisibility(View.VISIBLE);
        }
        else if(isStillConnected()){
            rtButton.setVisibility(View.VISIBLE);
        }
    }

    private class DisplayObject implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            float heading = 0;
            float gyroX = 0;
            float gyroY = 0;
            float gyroZ = 0;

            if(!Parser.getHeading().isEmpty()) { //Check that we have data
                resizeArrays(Parser.getHeading(), Parser.getGyroX(),
                        Parser.getGyroY(), Parser.getGyroZ()); //Make sure arrays haven't grown too large
            if(!Parser.getHeading().isEmpty() && !Parser.getGyroX().isEmpty()
                    && !Parser.getGyroY().isEmpty() && !Parser.getGyroZ().isEmpty()) {
                heading = Parser.getHeading().get(Parser.getHeading().size() - 1);
                gyroX = Parser.getGyroX().get(Parser.getGyroX().size() - 1);
                gyroY = Parser.getGyroY().get(Parser.getGyroY().size() - 1);
                gyroZ = Parser.getGyroZ().get(Parser.getGyroZ().size() - 1);
            }
                gyroX = convert2deg(gyroX); //The values we get are in rads/sec
                gyroY = convert2deg(gyroY);
                gyroZ = convert2deg(gyroZ);

                spinCompass(compass, heading);
                controlSeaperchRt(gyroX, gyroY, gyroZ);
            }
        }
    }

    private float convert2deg(float num){
        float pi = 3.14159265358979f;
        num = num * (180f / pi);
        return num;
    }

    private void showInstructions(){
        AlertDialog.Builder helpPopup = new AlertDialog.Builder(
            this);
        helpPopup.setTitle("Instructions");
        helpPopup.setMessage("Welcome to SensorPack!\n\n" + "-To begin, connect to a " +
                        "device using the Bluetooth button in the bottom left hand" +
                        " corner of the screen (the device must first be paired with your " +
                        "Android device in the Bluetooth menu)\n" + "-You may navigate windows" +
                        " by swiping" +
                        " left or right in whitespace, or by pressing arrows at the " +
                        "bottom of the screen\n" + "-To stream real time data, simply press" +
                        " one of the real time data buttons\n" + "-To reset the Arduino, tap the " +
                        "RESET button\n" + "-To read a file from the SD card, tap the SD card button " +
                        "and select a file\n" +
                        "-To start logging sensor data to a file on the SD card, tap the download " +
                        "button above the SD card button")
                .setCancelable(true)
                .setPositiveButton("Close",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });
        helpPopup.create().show();
    }

    /**
     * Returns Bluetooth object that handles connecting and socket saving
     * @return Bluetooth object
     */
    public static Bluetooth getBT(){
        return BT;
    }

    public static void saveSocket(BluetoothSocket saveSocket){socket = saveSocket;}

	public static int getBtnState(){
		return btnPressCount;
	}

    public static void addBtDevice(String name, String address){
        mArrayAdapter.add(name + "\n" + address);
    }

    private static void resizeArrays(ArrayList<Float> heading, ArrayList<Float> gyroX,
                                     ArrayList<Float> gyroY, ArrayList<Float> gyroZ) {
            while ((heading.size()>20) || (gyroX.size()>20) || (gyroY.size()>20)
                    || (gyroZ.size()>20)) { //Shrink arrays down to 20
                Parser.removeFirst(); //Keep the arraylist only 20 samples long
        }
    }

    private class DataObject extends Observable {

        public void setValue() {
            downloadRtData();
            setChanged();
            notifyObservers();
        }
    }

    private void sendLogApp(){
        try{
            if (socket != null) {
                OutputStream outStream = socket.getOutputStream();
                Commands.sendCommand(outStream, "logapp", ""); //Send logapp command to start data transfer
            }
        } catch (IOException e) {
            Log.d("IOException", "Exception sending logapp command");
        }
    }

	private void startRtDownload(final DataObject data){
		//The following thread code in this method is modified from:
		//https://github.com/PhilJay/MPAndroidChart/blob/master/MPChartExample/src/com/xxmassdeveloper/mpchartexample/RealtimeLineChartActivity.java
		new Thread(new Runnable() {
			@Override
			public void run() {
				while ((btnPressCount % 2) != 0) { //If it's an odd button press
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							data.setValue();
						}
					});
					try {
						Thread.sleep(35);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		}).start();
	}

    /**
	 * Send command to Bluno to start data transfer
	 * Receive data
	 */
	private void downloadRtData(){
		try {
			if (socket != null) {
				InputStream inStream = socket.getInputStream();
				Parser.readRtData(inStream, "MainActivity");
			}
		} catch (IOException e) {
			Log.d("IOException", "Exception downloading real time data");
		}
	}

	/**
	 * This method creates a list of paired Bluetooth devices
	 * in a dialog box and maxes the options clickable
	 */
	private void displayList(){
		mArrayAdapter.clear();
		BT.setupBT();

		ListView newDevicesListView = (ListView)
				dialog.findViewById(R.id.device_list_display);

		newDevicesListView.setAdapter(mArrayAdapter);
		newDevicesListView.setClickable(true);
	}

    /**
     * This method creates a list of SD card filenames in a dialog
     * box and makes the options clickable
     */
    private void displayFiles(){
        fileDialog.setContentView(R.layout.file_list_popup);
        fileDialog.setCancelable(true);
        fileDialog.setTitle("SD card files");

        ListView lv = (ListView) fileDialog.findViewById(R.id.file_list_display);
        lv.setAdapter(new ArrayAdapter<String> (this, R.layout.file_list_popup));

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg, View view, int position, long id){
                if(socket!=null && isStillConnected()) {
                   //DownloadTask Download = new DownloadTask();
                    Download = new DownloadTask();
                    Download.fileName = mFileNameAdapter.getItem(position);
                    Download.selection = position;
                    Download.execute();
                }
                else{
                    Snackbar.make(view, "Not connected", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });
        fileDialog.show();
        Parser.dialogOpen = true;
        loadFileNamePopup(mFileNameAdapter); //populates popup with options
        lv.setAdapter(mFileNameAdapter);
        lv.setClickable(true);
        if(mFileNameAdapter.isEmpty()){ //Make sure we actually have names to display
            fileDialog.hide();
            if(Parser.readingTimedOut()){
                Snackbar.make(findViewById(R.id.full_screen_main),
                        "Download timed out, please try again",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }else {
                Snackbar.make(findViewById(R.id.full_screen_main),
                        "Error getting file names, please try again",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        }

        ProgressBar tempSpinner = (ProgressBar)fileDialog.findViewById(R.id.progressBar2);
        tempSpinner.setVisibility(View.INVISIBLE); //Hide the progress bar while data isn't being downloaded
    }

	/**
	 * Intent code from
	 * http://stackoverflow.com/questions/6121797/android-how-to-change-layout-on-button-click
	 */
	private void changeActivity(Class mClass){
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
		dialog.setTitle("Paired Bluetooth Devices");
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
				new ConnectTask().execute(device);
			}
		});
	}

    /**
     * This method loads the filename ArrayAdapter with the
     * filename options that the user can choose
     * @param mFileNameAdapter ArrayAdapter that holds filenames from SD card
     */
    private void loadFileNamePopup(ArrayAdapter<String> mFileNameAdapter) {
        if(mFileNameAdapter!=null) mFileNameAdapter.clear();
            ArrayList<String> fileNames = Parser.extractFileNames();
            if (!fileNames.isEmpty()){
                for (String fileName : fileNames){
                    if (mFileNameAdapter != null)
                        mFileNameAdapter.add(fileName);
                }
            }
        }

	/**
	 * This method rotates the compass image to a desired angle
	 * @param imageView The ImageView for the compass
	 * @param angle The angle we wish to rotate the compass to
     */
	private void spinCompass(ImageView imageView, float angle){
		angle += 180; //Invert compass
        if(angle>360) angle = 360 - angle;
        imageView.setRotation(angle);
	}

	/**
	 * This method rotates an image to angles corresponding to
	 * the accelerometer data
	 * @param imageView The ImageView for the seaperch
	 * @param x The angle we wish to rotate the seaperch to on x axis
	 * @param y The angle we wish to rotate the seaperch to on y axis
	 * @param z The angle we wish to rotate the seaperch to on z axis
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
	 * @param heading The heading data received from the Arduino
	 * @param slider The slider to display heading data at different timestamps
	 */
	private void controlCompass(ArrayList<Float> heading, SeekBar slider){
		if(!heading.isEmpty() && !(slider.getProgress()>=heading.size())) { //If we have data
			spinCompass(compass, heading.get(slider.getProgress()));
		}
	}

	/**
	 * Rotates the seaperch to the values correspoding to a time on the
	 * seekbar
	 * @param gyroX Gyroscope x axis data
	 * @param gyroY Gyroscope y axis data
	 * @param gyroZ Gyroscope z axis data
	 * @param slider The slider to display gyroscope data at different timestamps
	 */
	private void controlSeaperch(ArrayList<Float> gyroX, ArrayList<Float> gyroZ,
								 ArrayList<Float> gyroY, SeekBar slider){
		if(!gyroY.isEmpty() && !(slider.getProgress()>=gyroY.size())) { //If we have data
			slider.setMax(gyroZ.size() - 1); //Scale bar to size of data array
			float x = gyroX.get(slider.getProgress());
			float y = gyroY.get(slider.getProgress());
			float z = gyroZ.get(slider.getProgress());
			float sampleTime = 0.1f; //approximate, 10Hz sample rate

			x = x * sampleTime; //deg/sec * sec
			y = y * sampleTime;
			z = z * sampleTime;

			rotateSeaperch(seaperch, x, z, y); //Z and Y are reversed here
		}
	}

	/**
	 * Adjusts clock to the values correspoding to a time on the seekbar
	 * @param time ArrayList of timestamps for data collection
	 * @param slider The slider to display timestamp data
	 */
	private void controlClock(ArrayList<String> time, SeekBar slider){
		if(!time.isEmpty() && !(slider.getProgress()>=time.size())) { //If we have data
            timeDisplay.setText(""); //reset text view
			for(String timeHolder : time.get(slider.getProgress()).split("\\n")) //Ignore any newline data
			print2BT(timeHolder);
		}
	}

    /**
     * Rotates the seaperch to received real time values
     * @param x Angle we wish to rotate seaperch to on x axis
     * @param y Angle we wish to rotate seaperch to on y axis
     * @param z Angle we wish to rotate seaperch to on z axis
     */
    private void controlSeaperchRt(float x, float z, float y){
            float sampleTime = 0.1f; //approximate, 10Hz sample rate

            x = x * sampleTime; //deg/sec * sec
            y = y * sampleTime;
            z = z * sampleTime;

            rotateSeaperch(seaperch, x, z, y); //Z and Y are reversed here
    }

	/**
	 * This method prints text to a text box on the MainActivity screen
	 * @param theString Text to be printed
     */
	private void print2BT(String theString){
        timeDisplay.append(theString);		//append the text into the EditText
		((ScrollView)timeDisplay.getParent()).fullScroll(View.FOCUS_DOWN);
	}

	/**
	 * This class handles connecting to a Bluetooth device in the background
	 * while displaying a loading bar.
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
			BT.connect2device(device);
			return "done";
		}
		@Override
		protected void onPostExecute(String result) {
			spinner = (ProgressBar)dialog.findViewById(R.id.progressBar);
			spinner.setVisibility(View.INVISIBLE);

            if(socket!=null) {
                if (socket.isConnected()) //Check if we're connected after an attempt
                    Snackbar.make(dialog.findViewById(R.id.device_list_display), "Connected", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                else Snackbar.make(dialog.findViewById(R.id.device_list_display), "Connection attempt failed", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
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
		private String fileName = "";
        private int selection = 0;
		@Override
		protected void onPreExecute() {
			spinner = (ProgressBar)fileDialog.findViewById(R.id.progressBar2);
			spinner.setVisibility(View.VISIBLE);
        }
        @Override
        protected void onProgressUpdate(Integer... progress) {
            TextView progressText = (TextView)fileDialog.findViewById(R.id.progress_text);
            progressText.setText(getResources().getString(R.string.progress));
            progressText.append(progress[0] + "%" + "  ");
            progressText.append("Byes downloaded: " + Parser.getBytesDown()
                    + "/" + Parser.totalFileSize);
            spinner.setProgress(progress[0]);
            //spinner.getProgressDrawable().setColorFilter(
            //        Color.RED, android.graphics.PorterDuff.Mode.SRC_IN);
        }

		@Override
		protected String doInBackground(Integer... params) {
            try {
                Parser.readSdCat(socket.getInputStream(), 11,
                        fileName, selection);
            }catch(IOException e){
                System.out.print("Exception doing readSdCat");
            }
			return "done";
		}

        public void update(int iProgress){
            publishProgress(iProgress);
        }

		@Override
		protected void onPostExecute(String result) {
			spinner = (ProgressBar)fileDialog.findViewById(R.id.progressBar2);
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
			if(socket!=null)
			socket.close();
		}
		catch(IOException e){
			Log.d("IOException", "Exception closing socket");
		}
	}
}


