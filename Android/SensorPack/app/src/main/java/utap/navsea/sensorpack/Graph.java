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

import android.graphics.Color;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;

public class Graph {
    public static ArrayList<Entry> convert2Entry(ArrayList<Float> input){
        return loadArray(input.toArray(new Float[input.size()]));
    }

    private static ArrayList<Entry> loadArray(Float[] data){
        ArrayList<Entry> entries = new ArrayList<>(); //figure out how to index values
        for(int i=0; i<data.length; i++){
            entries.add(i, new Entry(data[i], i));
        }
        return entries;
    }

    public static void graphData(LineChart chart, ArrayList<Entry> yData, String dataLabel, int color){
        if(yData != null) {
            LineDataSet dataSet = new LineDataSet(yData, dataLabel);
            dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            dataSet.setColor(color);
            formatChart(chart); //colors, lines...etc
            ArrayList<ILineDataSet> dataSets = new ArrayList<>();
            dataSets.add(dataSet);
            ArrayList xVals = setupXaxis(yData); //Scale x axis to incoming data
            ArrayList<String> yVals = new ArrayList<>();
            yVals.add("Data");
            LineData data = new LineData(xVals, dataSets);
            chart.setData(data);
            chart.invalidate(); // refresh
        }
    }

    private static ArrayList<String> setupXaxis(ArrayList<Entry> entries){
        ArrayList<String> xVals = new ArrayList<>();
        String[] tempXvals = new String[entries.size()];

        for(int i=0; i<entries.size(); i++) {
            tempXvals[i] = Integer.toString(i);
        }
        for(String temp : tempXvals){
            xVals.add(temp);
        }
        return xVals;
    }

    private static void formatChart(LineChart chart){
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
        chart.setDescription("");

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setPosition(Legend.LegendPosition.ABOVE_CHART_LEFT);
        legend.setTextColor(Color.RED);
    }
}
