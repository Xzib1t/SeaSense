package utap.navsea.sensorpack;

/**
 * Several of the methods in this file were copied directly from
 * https://github.com/DFRobot/BlunoBasicDemo/blob/master/Android/BlunoBasicDemo/app/src/main/java/com/dfrobot/angelo/blunobasicdemo/MainActivity.java
 */

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;

public class TempCondActivity extends BlunoLibrary {
    private TextView serialReceivedText;
    public LineChart chartTemp = null;
    public LineChart chartCond = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tempcond);
        onCreateProcess();
        serialBegin(9600);//115200);													//set the Uart Baudrate on BLE chip to 115200

        //serialReceivedText=(TextView) findViewById(R.id.serialReceivedText);	//initial the EditText of the received data
        chartTemp = (LineChart) findViewById(R.id.chart2); //get the first chart
        chartCond = (LineChart) findViewById(R.id.chart3); //get the second chart
        Float[] data = {80f, 255f, 3f, 4f, 200f, 150f, 125f};
        Float[] data1 = {200f, 100f, 60f, 89f};
        ArrayList<Entry> entries = loadArray(data);
        ArrayList<Entry> entries1 = loadArray(data1);
        graphTest(chartTemp, entries, "Temperature (Deg C)", Color.RED);
        graphTest(chartCond, entries1, "Conductivity", Color.GREEN);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Searching for devices", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                buttonScanOnClickProcess();										//Alert Dialog for selecting the BLE device
            }
        });

        /**
         * Intent code from
         * http://stackoverflow.com/questions/6121797/android-how-to-change-layout-on-button-click
         */
        FloatingActionButton fabDL = (FloatingActionButton) findViewById(R.id.fabDL);
        assert fabDL != null;
        fabDL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentApp = new Intent(TempCondActivity.this, DepthLightActivity.class);
                TempCondActivity.this.startActivity(intentApp);
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
        //TODO
    }

    @Override
    public void onSerialReceived(String theString) {    //Once connection data received, this function will be called

    }
}
