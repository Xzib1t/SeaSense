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
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

public class TempCondActivity extends AppCompatActivity {
    private LineChart chartTemp = null;
    private LineChart chartCond = null;
    private BluetoothSocket socket = Bluetooth.getSocket(); //We store the socket in the Bluetooth class
    private static int btnPressCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tempcond);

        final GraphObject graph = new GraphObject();
        final DataObject data = new DataObject();
        data.addObserver(graph);

        chartTemp = (LineChart) findViewById(R.id.chart2); //get the first chart
        //TODO add time on the x axis
        graphTest(chartTemp, convert2Entry(Bluetooth.getTemp()), "Temperature (Deg C)", Color.RED);
        chartTemp.invalidate(); //Refresh graph

        chartCond = (LineChart) findViewById(R.id.chart3); //get the first chart
        graphTest(chartCond, convert2Entry(Bluetooth.getCond()), "Conductivity (S/m)", Color.GREEN);
        chartCond.invalidate(); //Refresh graph

        setupSwipeDetector(); //Setup swipe detector so we can swipe to change views

        final Button rtButton = (Button) findViewById(R.id.rtbutton_tempcond);
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

        FloatingActionButton fabLeft = (FloatingActionButton) findViewById(R.id.fab_left1);
        assert fabLeft != null;
        fabLeft.setOnClickListener(new View.OnClickListener() {
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

        FloatingActionButton fabRight = (FloatingActionButton) findViewById(R.id.fab_right1);
        assert fabRight != null;
        fabRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if((btnPressCount % 2) == 0) {
                    flushStream();
                    changeActivity(DepthLightActivity.class);
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
        activitySwipeDetector.setDestinations(MainActivity.class, DepthLightActivity.class);
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.full_screen_tempcond);
        layout.setOnTouchListener(activitySwipeDetector);
    }

    private void flushStream(){
        try {
            if(socket!=null)
            socket.getInputStream().skip(socket.getInputStream().available());
        } catch (IOException e) {
        }
    }

    private class GraphObject implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            ArrayList<Float> dataTemp = Bluetooth.getTemp();
            ArrayList<Float> dataCond = Bluetooth.getCond();
            graphRtData(dataTemp, Bluetooth.getTemp(), chartTemp); //Graph temp
            graphRtData(dataCond, Bluetooth.getCond(), chartCond); //Graph conductivity
        }
    }

    private class DataObject extends Observable {

        public void setValue() {
            downloadRtData();
            setChanged();
            notifyObservers();
        }
    }

    /**
     * This is a workaround for detecting if we are
     * getting data or not, since reading from an InputStream is blocking
     * @return
     */
    private boolean isGettingData(){
        try{
            if(socket.getInputStream().available()>0) {
                flushStream(); //Flush stream to restart estimate
                Thread.sleep(200); //Give us time to see if we still get data after a flush
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
     * @param rtButton
     * @param data
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

    public static int getBtnState(){
        return btnPressCount;
    }

    private void startRtDownload(final DataObject data){
        //The following thread code in this method is modified from:
        //https://github.com/PhilJay/MPAndroidChart/blob/master/MPChartExample/src/com/xxmassdeveloper/mpchartexample/RealtimeLineChartActivity.java
        new Thread(new Runnable() { //TODO make sure this doesn't run more than once
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

    private void graphRtData(ArrayList<Float> data, ArrayList<Float> sensorData, LineChart chart){
        if (data.size() > 20) {
            while (data.size() > 20) Bluetooth.removeFirst(); //Keep the arraylist only 20 samples long
        }
        if (data.size() > 0 && chart!=null) {
            ArrayList<Entry> dataE = convert2Entry(Bluetooth.getTemp());
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
     * Send command to Bluno to start data transfer
     * Receive data
     */
    private void downloadRtData(){
        try {
            if (socket != null) {
                InputStream inStream = socket.getInputStream();
                Bluetooth.readRtData(inStream, "TempCondActivity");
            }
        } catch (IOException e) {
            System.out.println("Exception thrown");
        }
    }

    private void sendLogApp(){
         try{
            if (socket != null) {
                OutputStream outStream = socket.getOutputStream();
                Commands.sendCommand(outStream, "logapp", ""); //Send logapp command to start data transfer
            }
        } catch (IOException e) {
             System.out.println("Exception thrown, output");
        }
    }

    /**
     * Intent code from
     * http://stackoverflow.com/questions/6121797/android-how-to-change-layout-on-button-click
     */
    void changeActivity(Class mClass){
        Intent intentApp = new Intent(TempCondActivity.this, mClass);
        TempCondActivity.this.startActivity(intentApp);
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

    private ArrayList<Entry> convert2Entry(ArrayList<Float> input){
        Float[] floatArray = input.toArray(new Float[input.size()]);
        ArrayList<Entry> entryArray = loadArray(floatArray);
        return entryArray;
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

        XAxis xAxis = chart.getXAxis();
        xAxis.setEnabled(true);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawLabels(true);
        xAxis.setGridColor(Color.BLACK);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.RED);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.RED);
        leftAxis.setAxisLineColor(Color.BLACK);
        leftAxis.setEnabled(true);

        chart.setDrawGridBackground(true);
        chart.setDrawBorders(true);
        chart.setBorderColor(Color.BLACK);
        chart.setMaxVisibleValueCount(0);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setPosition(Legend.LegendPosition.ABOVE_CHART_LEFT);
        legend.setTextColor(Color.RED);
    }
}
