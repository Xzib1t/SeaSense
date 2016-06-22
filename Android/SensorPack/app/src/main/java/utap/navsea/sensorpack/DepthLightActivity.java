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
import android.support.v7.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;

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

public class DepthLightActivity extends AppCompatActivity {
    private LineChart chartDepth = null;
    private LineChart chartLight = null;
    private BluetoothSocket socket = Bluetooth.getSocket(); //We store the socket in the Bluetooth class
    private boolean activityRunning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depthlight);

        chartDepth = (LineChart) findViewById(R.id.chart4); //get the first chart
        chartLight = (LineChart) findViewById(R.id.chart5); //get the second chart

        graphTest(chartDepth, convert2Entry(Bluetooth.getDepth()), "Depth", Color.RED);
        chartDepth.invalidate(); //Refresh graph
        graphTest(chartLight, convert2Entry(Bluetooth.getLight()), "Light", Color.GREEN);
        chartLight.invalidate(); //refresh graph

        final GraphObject graph = new GraphObject();
        final DataObject data = new DataObject();
        data.addObserver(graph);
        graph.update(data, 10);

        Button rtButton = (Button) findViewById(R.id.rtbutton_depthlight);

        assert rtButton != null;
        rtButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //The following thread code in this method is modified from:
                //https://github.com/PhilJay/MPAndroidChart/blob/master/MPChartExample/src/com/xxmassdeveloper/mpchartexample/RealtimeLineChartActivity.java
                new Thread(new Runnable() { //TODO make sure this doesn't run more than once

                    @Override
                    public void run() {
                        try {
                            Commands.sendCommand(socket.getOutputStream(), "logapp");
                            System.out.println("Logapp sent");
                        }catch(IOException e){}

                        while(activityRunning){

                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    data.setValue(); //TODO add timeout
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
        });

        FloatingActionButton fabLeft = (FloatingActionButton) findViewById(R.id.fab_left2);
        assert fabLeft != null;
        fabLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Commands.sendCommand(socket.getOutputStream(), "logapp");
                    System.out.println("Logapp sent");
                }catch(IOException e){}
                activityRunning = false;
                changeActivity(TempCondActivity.class);
            }
        });

        FloatingActionButton fabRight = (FloatingActionButton) findViewById(R.id.fab_right2);
        assert fabRight != null;
        fabRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeActivity(MainActivity.class);
            }
        });
    }

    private class GraphObject implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            ArrayList<Float> dataCond = Bluetooth.getDepth();
            ArrayList<Float> dataLight = Bluetooth.getLight();
            graphRtData(dataCond, Bluetooth.getDepth(), chartDepth); //Graph temp
            graphRtData(dataLight, Bluetooth.getLight(), chartLight); //Graph conductivity
        }
    }

    private class DataObject extends Observable {

        public void setValue() {
            downloadRtData();
            setChanged();
            notifyObservers();
        }
    }

    private void sendFirst(){
        try{
            if (socket != null) {
                OutputStream outStream = socket.getOutputStream();
                Commands.sendCommand(outStream, "logapp"); //Send logapp command to start data transfer
            }
        } catch (IOException e) {
            //TODO
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
                    Bluetooth.readRtData(inStream, "DepthLightActivity");
             }
        } catch (IOException e) {
            //TODO
        }
    }

    private void graphRtData(ArrayList<Float> data, ArrayList<Float> sensorData, LineChart chart){
        if (data.size() > 20) {
            while (data.size() > 20) Bluetooth.removeFirst(); //Keep the arraylist only 20 samples long
        }
        if (data.size() > 0) {
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

    private ArrayList<Entry> convert2Entry(ArrayList<Float> input){
        Float[] floatArray = input.toArray(new Float[input.size()]);
        ArrayList<Entry> entryArray = loadArray(floatArray);
        return entryArray;
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
        chart.setMaxVisibleValueCount(0);
        chart.setBorderColor(Color.BLACK);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setPosition(Legend.LegendPosition.ABOVE_CHART_LEFT);
    }
}

