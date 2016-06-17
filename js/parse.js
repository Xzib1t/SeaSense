//https://nvd3-community.github.io/nvd3/
var testData;
var plottedData = [];
var timeStamps = [];

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Working_with_Objects
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
    $("#files").change(handleFileSelect);
    $("#newFile").click(showFileUI);
    $("#example").click(plotExampleData);
});


function showFileUI(){
    location.reload(); // reload the page (I'm having trouble getting $("#fileUI").show(500) to work using the same file as input)
}

function handleFileSelect(evt) {
    $("#fileUI").hide(500); // hide the landing page
    
    var file = evt.target.files[0]; // read in the first file (if using multiSelect)

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
        temp[index] = new value(index,data[index].Temp);
        depth[index] = new value(index,data[index].Depth);
        cond[index] = new value(index,data[index].Cond);
        light[index] = new value(index,data[index].Light);
        head[index] = new value(index,data[index].Head);
        
    }
  
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
    
    nv.addGraph(function() {
        var chart = nv.models.lineWithFocusChart();
        chart.brushExtent([50,70]);
        chart.xAxis
            .tickFormat(d3.format(',f'))
        chart.x2Axis.tickFormat(d3.format(',f'));
        chart.yAxis.tickFormat(d3.format(',.2f'));
        chart.y2Axis.tickFormat(d3.format(',.2f'));
        chart.useInteractiveGuideline(true);
        d3.select('#chart svg')
            .datum(plottedData)
            .call(chart);
        nv.utils.windowResize(chart.update);
        return chart;
    });

}