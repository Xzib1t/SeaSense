//https://nvd3-community.github.io/nvd3/
var testData;
var plottedData = [];
var timeStamps = [];
var stamp,xMin,xMax;
var parser = d3.time.format.utc("%Y/%m/%d %H:%M:%S");

// interpolation methods
var methods = ['basis','bundle','step-before','step-after','cardinal','monotone','linear'];
var methodNum = 4; // show cardinal by default
var chart;

function dataSet(area,key,values) {
  this.area = area;
  this.key = key;
  this.values = values;
}

function value(x,y) {
    this.x = x;
    this.y = y;
}

$(document).ready(function(){
    $("#chartObj").text(methods[methodNum]);
    $("#files").change(handleFileSelect);
    $("#newFile").click(showFileUI);
    $("#example").click(plotExampleData);
    $("#interp").click(interpolateData);
});


function showFileUI(){
    location.reload(); // reload the page (I'm having trouble getting $("#fileUI").show(500) to work using the same file as input)
}

function handleFileSelect(evt) {
    $("#fileUI").hide(500); // hide the landing page
    
    var file = evt.target.files[0]; // read in the first file (if using multiSelect)

    // turn filename into YYYY/MM/DD timestamp
    stamp = file.name.substr(0,4) + '/' + file.name.substr(4,2) + '/' + file.name.substr(6,2);
  
    console.log("File Date: %s",stamp);
    Papa.parse(file, { // parse the file as CSV using PapaParse.js
      header: true,
      dynamicTyping: true,
      comments: "#",
      complete: function(results) {
        testData = results;
          parseData(testData.data);
      }
    });
}


function plotExampleData(){
    $("#fileUI").hide(500); // hide the landing page
    parseData(exampleData);
}

function parseData(data){
    var temp = [];
    var depth = [];
    var cond = [];
    var light = [];
    var head = [];
    
    var index;
    for(index=0; index<(data.length-1);index++){
       
        timeStamps[index] = parser.parse(stamp+ ' ' + data[index].Time);
         temp[index] = new value(index,data[index].Temp);
        depth[index] = new value(index,data[index].Depth);
        cond[index] = new value(index,data[index].Cond);
        light[index] = new value(index,data[index].Light);
        head[index] = new value(index,data[index].Head);
        
    }
    xMin = timeStamps[0];
    xMax = timeStamps[timeStamps.length-1];
    
    var Temperature = new dataSet(false,"Temperature",temp);
    var Depth = new dataSet(false,"Depth (cm)",depth);
    var Conductivity = new dataSet(false,"Conductivity",cond);
    var Light = new dataSet(false,"Light",light);
    var Heading = new dataSet(false,"Heading",head);
    
    plottedData[0]=Temperature;
    plottedData[1]=Depth;
    plottedData[2]=Conductivity;
    plottedData[3]=Light;
    plottedData[4]=Heading;
   
    console.log(plottedData);
    
    //var xScale = d3.time.scale().domain([xMin,xMax]).range(0,$(window).width());
    
    //http://stackoverflow.com/questions/17446122/with-nvd3-js-nv-models-linewithfocuschart-how-do-you-set-specific-ticks-on-x
    //http://cmaurer.github.io/angularjs-nvd3-directives/line.chart.html
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

function interpolateData(){
    if(methodNum<(methods.length-1)) methodNum++;
    else methodNum = 0;
    chart.interpolate(methods[methodNum]);
    d3.select('#chart svg')
            .datum(plottedData)
            .call(chart);
    nv.utils.windowResize(chart.update);
     $( "#chartObj" ).empty();
    $( "#chartObj" ).append(methods[methodNum]);
}
