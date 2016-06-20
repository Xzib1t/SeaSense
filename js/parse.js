// Created by Georges Gauthier - glgauthier@wpi.edu

/* Based on the NVD3.js Line with View Finder example 
*  http://nvd3.org/examples/lineWithFocus.html
*  https://nvd3-community.github.io/nvd3/examples/documentation.html#lineWithFocusChart
*  Styled using Bootstrap - getbootstrap.com
*  Support for CSV file input using papaparse.js - papaparse.com
*/

var chart; // d3 graph - global so it can be updated otf

var plottedData = []; // data currently being plotted
var timeStamps = []; // timestamps matching the current data set(s)
var stamp; // year/month/day
var min,max; // brush extent (used in calculating averages)
var stats = 0;
var parser = d3.time.format("%Y/%m/%d %H:%M:%S"); // convert timestamps to d3 time format

// data interpolation methods
var methods = ['basis','bundle','step','cardinal','monotone','linear'];
var methodNum = 4; // current interpolation method (cardinal by default)

var filename = "example.csv"; // current filename (example by default)

// object constructor function used in creating d3-graphable datasets
function dataSet(area,key,values,disabled) {
  this.area = area;
  this.key = key;
  this.values = values;
  this.disabled = disabled;
}

// object constructor function used in creating individual datasets
function value(x,y) {
    this.x = x;
    this.y = y;
}

// event listeners
$(document).ready(function(){
    // run when the page is opened
     $("#avg-data").hide();
    $("#chartObj").text(methods[methodNum]);
    $("#filename").text(filename);
    //run based on user input
    $("#files").change(handleFileSelect);
    $("#newFile").click(showFileUI);
    $("#example").click(plotExampleData);
    $("#interp").click(interpolateData);
    $("#stats").click(showStats);
});

// used to reload the page for a new file input
// I'm having trouble getting $("#fileUI").show(500) to work using the same file as input, so I've resorted to a location.reload()
function showFileUI(){
    location.reload(); // reload the page 
}

function showStats(){
    stats = !stats;
    if(stats){
        document.getElementById("chart").style.width = "calc(100% - 150px)";
        $("#avg-data").show(500);
        $( "#statistics" ).empty();
        $( "#statistics" ).append("on");
        
    }
    else{
       document.getElementById("chart").style.width = "100%"; 
        $("#avg-data").hide(500);
        $( "#statistics" ).empty();
        $( "#statistics" ).append("off");
    }
    d3.select('#chart svg')
            .datum(plottedData)
            .call(chart)
            .transition().duration(1000);
    nv.utils.windowResize(chart.update);
}

// used for graphing a new file input
function handleFileSelect(evt) {
    $("#fileUI").hide(0); // hide the landing page
    
    // read in the first file (if using multiSelect)
    var file = evt.target.files[0]; 
    
    // write the filename underneath the "Load New Data" button
    $( "#filename" ).empty();
    $( "#filename" ).append(file.name);
    
    // turn filename into YYYY/MM/DD timestamp
    stamp = file.name.substr(0,4) + '/' + file.name.substr(4,2) + '/' + file.name.substr(6,2);
  
    Papa.parse(file, { // parse the file as CSV using PapaParse.js
      worker: true,
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
    $("#fileUI").hide(0); 
    stamp = "1992/01/20"; // Ice Cube's good day
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

    // create an array of <values> for each dataset
    timeStamps = data.map(function(d){return parser.parse(stamp+' '+d.Time)});
    temp = data.map(function(d,i){return new value(i,d.Temp)});
    depth = data.map(function(d,i){return new value(i,d.Depth)});
    cond = data.map(function(d,i){return new value(i,d.Cond)});
    light = data.map(function(d,i){return new value(i,d.Light)});
    head = data.map(function(d,i){return new value(i,d.Head)});

    // find the minimum and maximum timestamp (can be used for domain(x))
    xMin = timeStamps[0];
    xMax = timeStamps[timeStamps.length-1];
    //var xScale = d3.time.scale().domain([xMin,xMax]);
    
    // create a plottable dataset from each array of xy points
    // arg1 = show area under curve
    // arg2 = dataset name (displayed on plot)
    // arg3 = dataset xy points (array of objects)
    // arg4 = show/hide line on startup (true = hidden)
    var Temperature = new dataSet(false,'Temperature',temp,false);
    var Depth = new dataSet(false,"Depth",depth,false);
    var Conductivity = new dataSet(false,"Conductivity",cond,false);
    var Light = new dataSet(false,"Light",light,false);
    var Heading = new dataSet(false,"Heading",head,false);
    
    // add all of the datasets to the array that will be passed to the graphing function
    plottedData[0]=Temperature;
    plottedData[1]=Depth;
    plottedData[2]=Conductivity;
    plottedData[3]=Light;
    plottedData[4]=Heading;

    // plot the dataset using the lineWithFocusChart template
    nv.addGraph(function() {
        chart = nv.models.lineWithFocusChart();
        // default to showing points x[35],y[35] to x[65],y[65] 
        chart.brushExtent([35,65]);
        
        //xAxis ticks correspond to HH:MM:SS from timeStamps(tick)
        chart.xAxis
            .tickFormat(function(d) { 
                return d3.time.format('%H:%M:%S')(new Date(timeStamps[d])) 
            });
        
        //x2Axis ticks correspond to mm/dd/yyyy from timeStamps(tick)
        chart.x2Axis
            .tickFormat(function(d) { 
                return d3.time.format('%m/%d/%Y')(new Date(timeStamps[d])) 
            })
        
        chart.yAxis.tickFormat(d3.format(',.2f'));
        chart.y2Axis.tickFormat(d3.format(',.2f'));
        chart.useInteractiveGuideline(true);
        chart.interpolate(methods[methodNum]);
        
        // calculate the plotted domain when the zoom level is changed
        chart.dispatch.on('brush.update', function(b) { 
                 min = Math.ceil(b.extent[0]);
                 max = Math.ceil(b.extent[1]);
                 getStats(min,max);
        });
        
        // draw the chart
        d3.select('#chart svg')
            .datum(plottedData)
            .call(chart)
        nv.utils.windowResize(chart.update);
        return chart;
    });
}

// used to calculate statistics accross current displayed range
function getStats(min,max){
    var index,obj;
    var avgTemp=0,avgDepth=0,avgCond=0,avgLight=0,avgHead=0;
    var temp = [];
    var depth = [];
    var cond = [];
    var light = [];
    var head = [];
    
    // find which variables are currently displayed
    var enabled = plottedData.map(function(d) { return !d.disabled });
    
    // read each category's y-data into an array
    for(obj=0;obj<plottedData.length;obj++){
        switch(obj){
            case 0:
                temp = plottedData[obj].values.map(function(d){return d.y}).slice(min,max);
                break;
            case 1:
                depth = plottedData[obj].values.map(function(d){return d.y}).slice(min,max);
                break;
            case 2: 
                cond = plottedData[obj].values.map(function(d){return d.y}).slice(min,max);
                break;
            case 3: 
                light = plottedData[obj].values.map(function(d){return d.y}).slice(min,max);
                break;
            case 4: 
                head = plottedData[obj].values.map(function(d){return d.y}).slice(min,max);
                break;
        }
    }
    
    // only calculate/update stats when viewer is on
    if(stats){ 
        // clear out old statistics
        $("#avg-data").empty();
        // calculate & display temperature information
        $("#avg-data").append(
            (enabled[0] == 1 ? 
            '<h4>Temperature</h4><p>'+
            "min: "+ d3.min(temp).toFixed(2) + " &deg<small>C</small>"+
            '</p><p>'+
            "max: "+ d3.max(temp).toFixed(2) + " &deg<small>C</small>"+
            '</p><p>'+
            "mean: "+ d3.mean(temp).toFixed(2) + " &deg<small>C</small>"+
            '</p><p>'+
            "stdDev: "+ d3.deviation(temp).toFixed(2) + " &deg<small>C</small>"+
//            '</p><p>'+
//            'extent: ' + d3.extent(temp) + " &deg<small>C</small>"+
            '</p>': '')
        ); 
        // calculate & display depth information
        $("#avg-data").append(
            (enabled[1] == 1 ? 
            '<h4>Depth</h4><p>' +
            "min: "+ d3.min(depth).toFixed(2) + " <small>cm</small>"+
            '</p><p>'+
            "max: "+ d3.max(depth).toFixed(2) + " <small>cm</small>"+
            '</p><p>'+
            "mean: "+ d3.mean(depth).toFixed(0) + " <small>cm</small>"+
            '</p><p>' +
            "stdDev: "+ d3.deviation(depth).toFixed(0) + " <small>cm</small>"+
//            '</p><p>'+
//            'extent: ' + d3.extent(depth) +" <small>cm</small>"+
            '</p>': '')
        );
        // calculate & display conductivity information
        $("#avg-data").append(
            (enabled[2] == 1 ?
            '<h4>Conductivity</h4><p>' +
            "min: "+ d3.min(cond).toFixed(2) + " <small>S/m</small>"+
            '</p><p>'+
            "max: "+ d3.max(cond).toFixed(2) + " <small>S/m</small>"+
            '</p><p>'+
            "mean: "+ d3.mean(cond).toFixed(0) + " <small>S/m</small>"+
            '</p><p>' +
            "stdDev: "+ d3.deviation(cond).toFixed(0) + " <small>S/m</small>"+
//            '</p><p>'+
//            'extent: ' + d3.extent(cond) +" <small>S/m</small>"+
            '</p>': '')
        );
        // calculate & display light information
        $("#avg-data").append(
            (enabled[3] == 1 ?
            '<h4>Light</h4><p>' +
            "min: "+ d3.min(light).toFixed(2) + " <small>lx</small>"+
            '</p><p>'+
            "max: "+ d3.max(light).toFixed(2) + " <small>lx</small>"+
            '</p><p>' +
            "mean: "+ d3.mean(light).toFixed(0) + " <small>lx</small>"+
            '</p><p>'+
            "stdDev: "+ d3.deviation(light).toFixed(0) + " <small>lx</small>"+
//            '</p><p>'+
//            'extent: ' + d3.extent(light) +" <small>lx</small>"+
            '</p>': '')
        );
        // calculate & display heading information
        var avgHead = d3.mean(head).toFixed(0);
        $("#avg-data").append(
            (enabled[4] == 1 ?
            '<h4>Heading</h4><p>'+
            "min: "+ d3.min(head).toFixed(2) + "&deg"+
            '</p><p>'+
            "max: "+ d3.max(head).toFixed(2) + "&deg"+
            '</p><p>' +
            "mean: "+  avgHead + '&deg ('+ degToCompass(avgHead) + ')</p><p>'+
            "stdDev: "+ d3.deviation(head).toFixed(0) + '&deg'+
//            '</p><p>'+
//            'extent: ' + d3.extent(head) + '&deg'+
            '</p>' : '')
        );
    }
    return;
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
            .call(chart)
            .transition().duration(1000);
    nv.utils.windowResize(chart.update);
    
    // display the chosen method under the "Interpolate" nav bar button
    $( "#chartObj" ).empty();
    $( "#chartObj" ).append(methods[methodNum]);
}

// return a string representing compass direction for a given value in degrees
function degToCompass(num) {
    var val = Math.floor((num / 22.5) + 0.5);
    var arr = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"];
    return arr[(val % 16)];
}