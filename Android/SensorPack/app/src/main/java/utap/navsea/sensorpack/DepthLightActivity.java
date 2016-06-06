package utap.navsea.sensorpack;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
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

public class DepthLightActivity extends AppCompatActivity {
    private TextView serialReceivedText;
    public LineChart chartDepth = null;
    public LineChart chartLight = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depthlight);	        //set the Uart Baudrate on BLE chip to 115200

        //serialReceivedText=(TextView) findViewById(R.id.serialReceivedText);	//initial the EditText of the received data
        chartDepth = (LineChart) findViewById(R.id.chart4); //get the first chart
        chartLight = (LineChart) findViewById(R.id.chart5); //get the second chart
        Float[] data = {80f, 255f, 3f, 4f, 200f, 150f, 125f};
        Float[] data1 = {200f, 100f, 60f, 89f};
        ArrayList<Entry> entries = loadArray(data);
        ArrayList<Entry> entries1 = loadArray(data1);
        graphTest(chartDepth, entries, "Depth", Color.RED);
        graphTest(chartLight, entries1, "Light", Color.GREEN);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Searching for devices", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        /**
         * Intent code from
         * http://stackoverflow.com/questions/6121797/android-how-to-change-layout-on-button-click
         */
        FloatingActionButton fabMA = (FloatingActionButton) findViewById(R.id.fabMA);
        assert fabMA != null;
        fabMA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentApp = new Intent(DepthLightActivity.this, MainActivity.class);
                DepthLightActivity.this.startActivity(intentApp);
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

    public void onSerialReceived(String theString) {    //Once connection data received, this function will be called

    }
}

