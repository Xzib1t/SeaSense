// Created by Georges Gauthier - glgauthier@wpi.edu

/* Based on the NVD3.js Line with View Finder example 
*  http://nvd3.org/examples/lineWithFocus.html
*  Styled using Bootstrap - getbootstrap.com
*  Support for CSV file input using papaparse.js - papaparse.com
*/

var chart; // d3 graph - global so it can be updated otf

var plottedData = []; // data currently being plotted
var timeStamps = []; // timestamps matching the current data set(s)
var stamp,xMin,xMax; // year/month/day, first and last timestamps

var parser = d3.time.format.utc("%Y/%m/%d %H:%M:%S"); // convert timestamps to d3 time format

// data interpolation methods
var methods = ['basis','bundle','step-before','step-after','cardinal','monotone','linear'];
var methodNum = 4; // current interpolation method (cardinal by default)

var filename = "example.csv"; // current filename (example by default)

// object constructor function used in creating d3-graphable datasets
function dataSet(area,key,values) {
  this.area = area;
  this.key = key;
  this.values = values;
}

// object constructor function used in creating individual datasets
function value(x,y) {
    this.x = x;
    this.y = y;
}

// event listeners
$(document).ready(function(){
    // run when the page is opened
    $("#chartObj").text(methods[methodNum]);
    $("#filename").text(filename);
    //run based on user input
    $("#files").change(handleFileSelect);
    $("#newFile").click(showFileUI);
    $("#example").click(plotExampleData);
    $("#interp").click(interpolateData);
});

// used to reload the page for a new file input
// I'm having trouble getting $("#fileUI").show(500) to work using the same file as input, so I've resorted to a location.reload()
function showFileUI(){
    location.reload(); // reload the page 
}

// used for graphing a new file input
function handleFileSelect(evt) {
    $("#fileUI").hide(500); // hide the landing page
    
    // read in the first file (if using multiSelect)
    var file = evt.target.files[0]; 
    
    // write the filename underneath the "Load New Data" button
    $( "#filename" ).empty();
    $( "#filename" ).append(file.name);
    
    // turn filename into YYYY/MM/DD timestamp
    stamp = file.name.substr(0,4) + '/' + file.name.substr(4,2) + '/' + file.name.substr(6,2);
  
    Papa.parse(file, { // parse the file as CSV using PapaParse.js
      header: true,
      dynamicTyping: true,
      comments: "#",
      complete: function(results) {
          parseData(results.data); // parse the data so that it can be read by d3, then plot it
      }
    });
}

// hide the landing page and plot the example data stored in exampleData.json
function plotExampleData(){
    $("#fileUI").hide(500); 
    parseData(exampleData);
}

// parse an input CSV file's data so that it's readable by d3
function parseData(data){
    // create an empty array for each dataset (will hold [values.x, values.y])
    var temp = [];
    var depth = [];
    var cond = [];
    var light = [];
    var head = [];
    
    var index; // index in object array
    // iterate through the input object array and create an array of <values> for each dataset
    for(index=0; index<(data.length-1);index++){ 
        timeStamps[index] = parser.parse(stamp+ ' ' + data[index].Time);
        temp[index] = new value(index,data[index].Temp);
        depth[index] = new value(index,data[index].Depth);
        cond[index] = new value(index,data[index].Cond);
        light[index] = new value(index,data[index].Light);
        head[index] = new value(index,data[index].Head);
        
    }
    
    // find the minimum and maximum timestamp (can be used for domain(x))
    xMin = timeStamps[0];
    xMax = timeStamps[timeStamps.length-1];
//    var xScale = d3.time.scale().domain([xMin,xMax]).range(0,$(window).width());
    
    // create a plottable dataset from each array of xy points
    // arg1 = show area under curve
    // arg2 = dataset name (displayed on plot)
    // arg3 = dataset xy points (array of objects)
    var Temperature = new dataSet(false,"Temperature",temp);
    var Depth = new dataSet(false,"Depth (cm)",depth);
    var Conductivity = new dataSet(false,"Conductivity",cond);
    var Light = new dataSet(false,"Light",light);
    var Heading = new dataSet(false,"Heading",head);
    
    // add all of the datasets to the array that will be passed to the graphing function
    plottedData[0]=Temperature;
    plottedData[1]=Depth;
    plottedData[2]=Conductivity;
    plottedData[3]=Light;
    plottedData[4]=Heading;
   
    // log the data array for debug
//    console.log(plottedData);
    
    
    
    //http://stackoverflow.com/questions/17446122/with-nvd3-js-nv-models-linewithfocuschart-how-do-you-set-specific-ticks-on-x
    //http://cmaurer.github.io/angularjs-nvd3-directives/line.chart.html
    // plot the dataset using the lineWithFocusChart template
    nv.addGraph(function() {
        chart = nv.models.lineWithFocusChart();
        chart.brushExtent([50,70]);
        //chart.xAxis.tickFormat(d3.time.format('%H:%M:%S'));
        chart.xAxis.tickFormat(d3.format(',f'));
        //chart.xAxis.scale(xScale);
        chart.x2Axis.tickFormat(d3.format(',f'));
        chart.yAxis.tickFormat(d3.format(',.2f'));
        chart.y2Axis.tickFormat(d3.format(',.2f'));
        chart.useInteractiveGuideline(true);
        chart.interpolate(methods[methodNum]);
        d3.select('#chart svg')
            .datum(plottedData)
            .call(chart);
        nv.utils.windowResize(chart.update);
        return chart;
    });

}

// Interpolate and redisplay data based on navBar input
function interpolateData(){
    // iterate through the available interpolation methods
    if(methodNum<(methods.length-1)) methodNum++;
    else methodNum = 0;
    
    // interpolate based on the chosen method
    chart.interpolate(methods[methodNum]);
    
    // update the chart data
    d3.select('#chart svg')
            .datum(plottedData)
            .call(chart);
    nv.utils.windowResize(chart.update);
    
    // display the chosen method under the "Interpolate" nav bar button
    $( "#chartObj" ).empty();
    $( "#chartObj" ).append(methods[methodNum]);
}
