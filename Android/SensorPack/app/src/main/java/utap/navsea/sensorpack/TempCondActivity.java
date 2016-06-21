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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.github.mikephil.charting.charts.LineChart;
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
import java.util.Random;

public class TempCondActivity extends AppCompatActivity {
    public LineChart chartTemp = null;
    public LineChart chartCond = null;
    private  ArrayList<Float> temperature = new ArrayList<Float>();
    private BluetoothSocket socket = Bluetooth.getSocket(); //We store the socket in the Bluetooth class
    private Observable data = new Observable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tempcond);

        //graphData();
        chartTemp = (LineChart) findViewById(R.id.chart2); //get the first chart
        temperature.add(10f);
        graphTest(chartTemp, convert2Entry(temperature), "Temperature (Deg C)", Color.RED);

        final GraphObject graph = new GraphObject();
        final DataObject data = new DataObject();
        data.addObserver(graph);
        graph.update(data, 10);

        FloatingActionButton fabLeft = (FloatingActionButton) findViewById(R.id.fab_left1);
        assert fabLeft != null;
        fabLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeActivity(MainActivity.class);
            }
        });

        FloatingActionButton fabRight = (FloatingActionButton) findViewById(R.id.fab_right1);
        assert fabRight != null;
        fabRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
        sendFirst();
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        for(;;){//int i = 0; i < 500; i++) {

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
               //changeActivity(DepthLightActivity.class);
            }
        });
    }

    private class GraphObject implements Observer {
        @Override
        public void update(Observable observable, Object data) {
            ArrayList<Float> data1 = Bluetooth.getTemp();
            if (data1.size() > 20) {
                while (data1.size() > 20) Bluetooth.removeFirst();
            }
            if (data1.size() > 0) {
                ArrayList<Entry> dataE = convert2Entry(Bluetooth.getTemp());
                chartTemp.setVisibleXRangeMaximum(20);
                chartTemp.moveViewToX(chartTemp.getData().getXValCount() - 21);

                //The following code in this method is modified from:
                //https://github.com/PhilJay/MPAndroidChart/blob/master/MPChartExample/src/com/xxmassdeveloper/mpchartexample/RealtimeLineChartActivity.java
                LineData graphData = chartTemp.getData();

                if (graphData != null) {
                    ILineDataSet set = graphData.getDataSetByIndex(0);
                    graphData.addXValue(graphData.getXValCount() + " "
                            + graphData.getXValCount());
                    graphData.addEntry(new Entry(Bluetooth.getTemp().get(Bluetooth.getTemp().size()-1), set.getEntryCount()), 0);
                    chartTemp.notifyDataSetChanged();
                    chartTemp.invalidate(); //Refresh graph
                }
            }
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
     * This class handles downloading data in the background while displaying
     * a loading bar.
     */
    class DownloadTask extends AsyncTask<ArrayList<Float>, ArrayList<Float>, String> {
        @Override
        protected void onPreExecute() {

        }
        @Override
        protected String doInBackground(ArrayList<Float>... params) {
            Random rand = new Random();

            int  n = rand.nextInt(50) + 1;
            Float p = (float) n;
            temperature.add(p);
            //downloadRtData();
            publishProgress(temperature);
            return "done";
        }
        protected void onProgressUpdate(ArrayList<Float>...values){
            System.out.println("Running");
        }
        @Override
        protected void onPostExecute(String result) {
        }
    }

    private void graphData(){
        chartTemp.notifyDataSetChanged();
        chartTemp.invalidate(); //Refresh graph
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
            //TODO
        }
    }

    private void sendFirst(){
         try{
            if (socket != null) {
                OutputStream outStream = socket.getOutputStream();
                Bluetooth.sendCommand(outStream, "logapp"); //Send logapp command to start data transfer
            }
        } catch (IOException e) {
            //TODO
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
}
