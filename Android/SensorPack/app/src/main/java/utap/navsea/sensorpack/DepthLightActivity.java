/*
 * Copyright 2016 Joseph Maestri
 *
 * This class uses the MPAndroidChart Library
 * This library can be found at: https://github.com/PhilJay/MPAndroidChart
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

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

public class DepthLightActivity extends AppCompatActivity {
    private LineChart chartDepth = null;
    private LineChart chartLight = null;
    private BluetoothSocket socket = MainActivity.getBT().getSocket(); //We use the socket from the Bluetooth class
    private static int btnPressCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depthlight);

        chartDepth = (LineChart) findViewById(R.id.chart4); //get the first chart
        chartLight = (LineChart) findViewById(R.id.chart5); //get the second chart

        Graph.graphData(chartDepth, Graph.convert2Entry(Parser.getDepth()), "Depth", Color.RED);
        chartDepth.invalidate(); //Refresh graph
        Graph.graphData(chartLight, Graph.convert2Entry(Parser.getLight()), "Light", Color.GREEN);
        chartLight.invalidate(); //refresh graph

        final GraphObject graph = new GraphObject();
        final DataObject data = new DataObject();
        data.addObserver(graph);

        setupSwipeDetector(); //Setup swipe detector so we can swipe to change views

        final Button rtButton = (Button) findViewById(R.id.rtbutton_depthlight);
        if(socket!=null) rtButton.setVisibility(View.VISIBLE); //Only show the button if we're connected
        else rtButton.setVisibility(View.INVISIBLE);
        rtButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rtButton.setText(getResources().getString(R.string.graph_rt));
                btnPressCount++;
                syncButton(rtButton, data);
            }
        });

        FloatingActionButton fabLeft = (FloatingActionButton) findViewById(R.id.fab_left2);
        assert fabLeft != null;
        fabLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if((btnPressCount % 2) == 0) {
                    flushStream();
                    changeActivity(TempCondActivity.class);
                }
                else Snackbar.make(view, "Stop real time display before changing screens",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        FloatingActionButton fabRight = (FloatingActionButton) findViewById(R.id.fab_right2);
        assert fabRight != null;
        fabRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if((btnPressCount % 2) == 0) {
                    flushStream();
                    changeActivity(MainActivity.class);
                }
                else Snackbar.make(view, "Stop real time display before changing screens",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    private void setupSwipeDetector(){
        //Swipe detector code from http://stackoverflow.com/questions/937313/fling-gesture-detection-on-grid-layout
        ActivitySwipeDetector activitySwipeDetector = new ActivitySwipeDetector(this);
        activitySwipeDetector.setDestinations(TempCondActivity.class, MainActivity.class);
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.full_screen_depthlight);
        layout.setOnTouchListener(activitySwipeDetector);
    }

    private void flushStream(){
        try {
            if(socket!=null)
            socket.getInputStream().skip(socket.getInputStream().available());
        } catch (IOException e) {
            Log.d("IOException", "Exception flushing stream");
        }
    }

    /**
     * This is a workaround for detecting if we are
     * getting data or not, since reading from an InputStream is blocking
     * @return If data is being received, returns true, else false
     */
    private boolean isGettingData(){
        try{
            if(socket.getInputStream().available()>0) {
                flushStream(); //Flush stream to restart estimate
                Thread.sleep(100); //Give us time to see if we still get data after a flush
            }
            if(socket.getInputStream().available()>0) //We check here again after the delay
                return true;
            else return false;

        }catch(IOException | InterruptedException e){
            return false; //Couldn't read stream because we aren't getting data
        }
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
            rtButton.setText(getResources().getString(R.string.stop_graph_rt));
            sendLogApp(); //Need to send logapp to start data transfer
            startRtDownload(data);
        }
        if((btnPressCount % 2)!=0 && gettingData) { //We want data and are already getting it
            rtButton.setText(getResources().getString(R.string.stop_graph_rt));
            startRtDownload(data); //Don't need to send logapp, data already incoming
        }
        if((btnPressCount % 2)==0 && gettingData) { //We want to stop getting data and are still getting it
            rtButton.setText(getResources().getString(R.string.graph_rt));
            sendLogApp(); //Need to send logapp to stop data transfer
        }
        if((btnPressCount % 2)==0 && !gettingData) { //We want to stop getting data but we already aren't getting it
            rtButton.setText(getResources().getString(R.string.graph_rt));
            //Don't need to send logapp, data transfer already stopped
        }
    }

    private class GraphObject implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            ArrayList<Float> dataCond = Parser.getDepth();
            ArrayList<Float> dataLight = Parser.getLight();
            graphRtData(dataCond, Parser.getDepth(), chartDepth); //Graph temp
            graphRtData(dataLight, Parser.getLight(), chartLight); //Graph conductivity
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
            Log.d("IOException", "Exception sending logapp");
        }
    }

    public static int getBtnState(){
        return btnPressCount;
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
                    Parser.readRtData(inStream, "DepthLightActivity");
             }
        } catch (IOException e) {
            Log.d("IOException", "Exception downloading real time data");
        }
    }

    private void graphRtData(ArrayList<Float> data, ArrayList<Float> sensorData, LineChart chart){
        if (data.size() > 20) {
            while (data.size() > 20) Parser.removeFirst(); //Keep the arraylist only 20 samples long
        }
        if (data.size() > 0 && chart!=null) {
            chart.setVisibleXRangeMaximum(20); //Make the graph window only 20 samples wide
            chart.moveViewToX(chart.getData().getXValCount() - 21); //Follow the data with the graph

            //The following code in this method is modified from:
            //https://github.com/PhilJay/MPAndroidChart/blob/master/MPChartExample/src/com/xxmassdeveloper/mpchartexample/RealtimeLineChartActivity.java
            LineData graphData = chart.getData();

            if (graphData != null) {
                ILineDataSet set = graphData.getDataSetByIndex(0);
                graphData.addXValue(graphData.getXValCount() + " "
                        + graphData.getXValCount());
                graphData.addEntry(new Entry(sensorData.get(sensorData.size()-1), set.getEntryCount()), 0);
                chart.notifyDataSetChanged();
                chart.invalidate(); //Refresh graph
            }
        }
    }

    /**
     * Intent code from
     * http://stackoverflow.com/questions/6121797/android-how-to-change-layout-on-button-click
     */
    void changeActivity(Class mClass){
        Intent intentApp = new Intent(DepthLightActivity.this, mClass);
        DepthLightActivity.this.startActivity(intentApp);
    }
}

