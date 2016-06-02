/* 
 * Author: 
 * Purpose: GY-80 integration
 * Year: 2016
 * Modified several independent files to use the GY-80 
 * with Arduino Bluno Mega
 *  
 *  CODE SOURCES: 
 *  OLED: https://github.com/jandelgado/arduino/blob/master/ssd1306_sample_adafruit/ssd1306_sample_adafruit.ino
 *  L3G4200D Tripple Axis Gyroscope: http://bildr.org/2011/06/l3g4200d-arduino/
 *  ADXL345 Accelerometer: http://bildr.org/2011/03/adxl345-arduino/
 *  Triple Axis Magnetometer HMC5883L: http://bildr.org/2012/02/hmc5883l_arduino/
 */
  
//Arduino 1.0+ only

#include <SPI.h>
#include <Wire.h>
#include <avr/io.h>
#include <avr/interrupt.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "ADXL345.h"
#include "HMC5883L.h"

#define OLED_MOSI  47    //D1
#define OLED_CLK   45    //D0
#define OLED_DC    51 
#define OLED_CS    49 
#define OLED_RESET 53 
#define CTRL_REG1 0x20
#define CTRL_REG2 0x21
#define CTRL_REG3 0x22
#define CTRL_REG4 0x23
#define CTRL_REG5 0x24

int L3G4200D_Address = 105; //I2C address of the L3G4200D, 69 in hex
ADXL345 adxl; //variable adxl is an instance of the ADXL345 library
HMC5883L compass;

int x;
int y;
int z;
int button = 2; //pin 2, where INT0 is 
volatile int function = 1;
volatile long isrTime = 0;
long lastIsrTime = 0;
int newFunction = 0;

Adafruit_SSD1306 display(OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS);

void setup(){
  display.begin(SSD1306_SWITCHCAPVCC);
  display.display();
  delay(1000);
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(WHITE);
  print2Screen(0, 0, "Starting up L3G4200D", 0);
  print2Screen(0, 10, "Starting up ADXL345", 0);
  print2Screen(0, 20, "Starting up HMC5883L", 1000);
  display.clearDisplay();
  
  Wire.begin();
  Serial.begin(115200);
  setupL3G4200D(2000); // Configure L3G4200  - 250, 500 or 2000 deg/sec
  delay(1500); //wait for the sensor to be ready 

  setupADXL345();  
  compass = HMC5883L(); //new instance of HMC5883L library
  setupHMC5883L(); //setup the HMC5883L

  pinMode(13,OUTPUT); //for button testing
  pinMode(button,INPUT); //for button input
}

void loop(){
  attachInterrupt(digitalPinToInterrupt(button), changeFunc, HIGH);
    
   switch(function){
      case 1:
        doL3G4200D();
        break;
      case 2:;
        doADXL345();
        break;
      case 3:
        doHMC5883L();
        break;
      default:
       //Serial.println("Defaulted to this case");
      break;
    }
}

void changeFunc(){
  newFunction = 1;
  isrTime = millis();
  if( (isrTime - lastIsrTime) > 200){ //debounce
  display.clearDisplay();
  function+=1;
  if(function>3) {
    function = 1;
  }
  display.clearDisplay();
  }
    lastIsrTime = millis();
}

void print2Screen(int x, int y, String test, int delayTime){
  display.setCursor(x,y);
  display.print(test);
  display.display();
  delay(delayTime);
}

void clearLinesGyro(){
  if(newFunction == 1) display.clearDisplay();
  newFunction = 0;
  display.setTextColor(0xFFFF, 0);
  print2Screen(10, 10,"    ", 0);
  print2Screen(50, 10,"    ", 0);
  print2Screen(90, 10,"    ", 0);
  display.setTextColor(WHITE);
}

void clearLinesADXL(){
  if(newFunction == 1) display.clearDisplay();
  newFunction = 0;
  display.setTextColor(0xFFFF, 0);
  print2Screen(45, 10,"                         ", 0);
  display.setTextColor(WHITE);
}

void clearLinesHMC(){
  if(newFunction == 1) display.clearDisplay();
  newFunction = 0;
  display.setTextColor(0xFFFF, 0);
  print2Screen(45, 10,"            ", 0);
  print2Screen(60, 20,"                         ", 0);
  display.setTextColor(WHITE);
}

void dispGyro(int x, int y, int z){
  clearLinesGyro();
  Serial.print("Gyro");  //flag for gyro data  
  print2Screen(0, 0, "Triple Axis Gyroscope", 0);
  print2Screen(0, 10, "X:", 0);
  print2Screen(10, 10, (String)x, 0);
  print2Screen(40, 10, "Y:", 0);
  print2Screen(50, 10, (String)y, 0);
  print2Screen(80, 10, "Z:", 0);
  print2Screen(90, 10, (String)z, 0);
  delay(500);
}

void dispADXL(String currentStatus){ //delay for this segment is in the doADXL fnct
  clearLinesADXL();  
  Serial.print("ADXL");  //flag for adxl data
  print2Screen(0, 0, "ADXL Accelerometer", 0);
  print2Screen(0, 10, "Status:", 0);
  print2Screen(45, 10, currentStatus, 0);
}

void dispHMC(float heading){
  clearLinesHMC();  
  Serial.print("Compass");  //flag for compass data
  print2Screen(0, 0, "Tri Axis Magnetometer", 0);
  print2Screen(0, 10, "Heading:", 0);
  print2Screen(45, 10, (String)heading, 0);
  print2Screen(0, 20, "Direction:", 0);
  dispDirection(heading);
  //delay(1500);
}

void clearLine(int x, int y){
  display.setTextColor(BLACK);
  print2Screen(x, y, "", 0);
  display.setTextColor(WHITE);
}

void doHMC5883L(){
  float heading = getHeading();
  dispHMC(heading);
  Serial.print(heading);
  delay(100); //only here to slow down the serial print
}

void doL3G4200D(){ //Package data differently for better results
      getGyroValues();  // This will update x, y, and z with new values
      dispGyro(x,y,z);
      //Serial.print("X:");
      Serial.print("X");  //flag for X value
      Serial.print(x);
      //Serial.print(" Y:");
      Serial.print("Y");  //flag for Y value
      Serial.print(y);
      //Serial.print(" Z:");
      Serial.print("Z");
      Serial.print(z);
      delay(100); //Just here to slow down the serial to make it more readable
}

void doADXL345(){
   //Boring accelerometer stuff 
  dispADXL("Inactivity");  
  int x,y,z;  
  adxl.readAccel(&x, &y, &z); //read the accelerometer values and store them in variables  x,y,z

  // Output x,y,z values - Commented out
  //Serial.print(x);
  //Serial.print(y);
  //Serial.println(z);

  //Fun Stuff!    
  //read interrupts source and look for triggered actions
  
  //getInterruptSource clears all triggered actions after returning value
  //so do not call again until you need to recheck for triggered actions
   byte interrupts = adxl.getInterruptSource();
  
  // freefall
  if(adxl.triggered(interrupts, ADXL345_FREE_FALL)){
    Serial.print("freefall");
    dispADXL("Freefall");
    //add code here to do when freefall is sensed
  } 
  
  //inactivity
  if(adxl.triggered(interrupts, ADXL345_INACTIVITY)){
    Serial.print("inactivity");
    dispADXL("Inactivity");
     //add code here to do when inactivity is sensed
  }
  
  //activity
  if(adxl.triggered(interrupts, ADXL345_ACTIVITY)){
    Serial.print("activity"); 
    dispADXL("Activity");
     //add code here to do when activity is sensed
  }
  
  //double tap
  if(adxl.triggered(interrupts, ADXL345_DOUBLE_TAP)){
    Serial.print("double tap");
    dispADXL("Double tap");
     //add code here to do when a 2X tap is sensed
  }
  
  //tap
  if(adxl.triggered(interrupts, ADXL345_SINGLE_TAP)){
    Serial.print("tap");
    dispADXL("Tap");
     //add code here to do when a tap is sensed
  } 
  delay(500);
}

void setupADXL345(){
  adxl.powerOn();

  //set activity/ inactivity thresholds (0-255)
  adxl.setActivityThreshold(75); //62.5mg per increment
  adxl.setInactivityThreshold(75); //62.5mg per increment
  adxl.setTimeInactivity(10); // how many seconds of no activity is inactive?
 
  //look of activity movement on this axes - 1 == on; 0 == off 
  adxl.setActivityX(1);
  adxl.setActivityY(1);
  adxl.setActivityZ(1);
 
  //look of inactivity movement on this axes - 1 == on; 0 == off
  adxl.setInactivityX(1);
  adxl.setInactivityY(1);
  adxl.setInactivityZ(1);
 
  //look of tap movement on this axes - 1 == on; 0 == off
  adxl.setTapDetectionOnX(0);
  adxl.setTapDetectionOnY(0);
  adxl.setTapDetectionOnZ(1);
 
  //set values for what is a tap, and what is a double tap (0-255)
  adxl.setTapThreshold(50); //62.5mg per increment
  adxl.setTapDuration(15); //625μs per increment
  adxl.setDoubleTapLatency(80); //1.25ms per increment
  adxl.setDoubleTapWindow(200); //1.25ms per increment
 
  //set values for what is considered freefall (0-255)
  adxl.setFreeFallThreshold(7); //(5 - 9) recommended - 62.5mg per increment
  adxl.setFreeFallDuration(45); //(20 - 70) recommended - 5ms per increment
 
  //setting all interupts to take place on int pin 1
  //I had issues with int pin 2, was unable to reset it
  adxl.setInterruptMapping( ADXL345_INT_SINGLE_TAP_BIT,   ADXL345_INT1_PIN );
  adxl.setInterruptMapping( ADXL345_INT_DOUBLE_TAP_BIT,   ADXL345_INT1_PIN );
  adxl.setInterruptMapping( ADXL345_INT_FREE_FALL_BIT,    ADXL345_INT1_PIN );
  adxl.setInterruptMapping( ADXL345_INT_ACTIVITY_BIT,     ADXL345_INT1_PIN );
  adxl.setInterruptMapping( ADXL345_INT_INACTIVITY_BIT,   ADXL345_INT1_PIN );
 
  //register interupt actions - 1 == on; 0 == off  
  adxl.setInterrupt( ADXL345_INT_SINGLE_TAP_BIT, 1);
  adxl.setInterrupt( ADXL345_INT_DOUBLE_TAP_BIT, 1);
  adxl.setInterrupt( ADXL345_INT_FREE_FALL_BIT,  1);
  adxl.setInterrupt( ADXL345_INT_ACTIVITY_BIT,   1);
  adxl.setInterrupt( ADXL345_INT_INACTIVITY_BIT, 1);
}

void setupHMC5883L(){
 //Setup the HMC5883L, and check for errors
 int error; 
 error = compass.SetScale(1.3); //Set the scale of the compass.
 if(error != 0); //Serial.println(compass.GetErrorText(error)); //check if there is an error, and print if so

 error = compass.SetMeasurementMode(Measurement_Continuous); // Set the measurement mode to Continuous
 if(error != 0); //Serial.println(compass.GetErrorText(error)); //check if there is an error, and print if so
}

float getHeading(){
 //Get the reading from the HMC5883L and calculate the heading
 MagnetometerScaled scaled = compass.ReadScaledAxis(); //scaled values from compass.
 float heading = atan2(scaled.YAxis, scaled.XAxis);

 // Correct for when signs are reversed.
 if(heading < 0) heading += 2*PI;
 if(heading > 2*PI) heading -= 2*PI;

 return heading * RAD_TO_DEG; //radians to degrees
}

void getGyroValues(){

  byte xMSB = readRegister(L3G4200D_Address, 0x29);
  byte xLSB = readRegister(L3G4200D_Address, 0x28);
  x = ((xMSB << 8) | xLSB);

  byte yMSB = readRegister(L3G4200D_Address, 0x2B);
  byte yLSB = readRegister(L3G4200D_Address, 0x2A);
  y = ((yMSB << 8) | yLSB);

  byte zMSB = readRegister(L3G4200D_Address, 0x2D);
  byte zLSB = readRegister(L3G4200D_Address, 0x2C);
  z = ((zMSB << 8) | zLSB);
}

int setupL3G4200D(int scale){
  //From  Jim Lindblom of Sparkfun's code

  // Enable x, y, z and turn off power down:
  writeRegister(L3G4200D_Address, CTRL_REG1, 0b00001111);

  // If you'd like to adjust/use the HPF, you can edit the line below to configure CTRL_REG2:
  writeRegister(L3G4200D_Address, CTRL_REG2, 0b00000000);

  // Configure CTRL_REG3 to generate data ready interrupt on INT2
  // No interrupts used on INT1, if you'd like to configure INT1
  // or INT2 otherwise, consult the datasheet:
  writeRegister(L3G4200D_Address, CTRL_REG3, 0b00001000);

  // CTRL_REG4 controls the full-scale range, among other things:

  if(scale == 250){
    writeRegister(L3G4200D_Address, CTRL_REG4, 0b00000000);
  }else if(scale == 500){
    writeRegister(L3G4200D_Address, CTRL_REG4, 0b00010000);
  }else{
    writeRegister(L3G4200D_Address, CTRL_REG4, 0b00110000);
  }

  // CTRL_REG5 controls high-pass filtering of outputs, use it
  // if you'd like:
  writeRegister(L3G4200D_Address, CTRL_REG5, 0b00000000);
}

void writeRegister(int deviceAddress, byte address, byte val) {
    Wire.beginTransmission(deviceAddress); // start transmission to device 
    Wire.write(address);       // send register address
    Wire.write(val);         // send value to write
    Wire.endTransmission();     // end transmission
}

int readRegister(int deviceAddress, byte address){

    int v;
    Wire.beginTransmission(deviceAddress);
    Wire.write(address); // register to read
    Wire.endTransmission();

    Wire.requestFrom(deviceAddress, 1); // read a byte

    while(!Wire.available()) {
        // waiting
    }

    v = Wire.read();
    return v;
}

void dispDirection(float heading){
  if(heading>=0 && heading<11.25){
    print2Screen(60, 20, "N", 0);  
  }
  if(heading>=11.25 && heading<22.50){
    print2Screen(60, 20, "NbE", 0);  
  }
  if(heading>=22.50 &&  heading<33.75){
    print2Screen(60, 20, "NNE", 0);  
  }
  if(heading>=33.75 &&  heading<45){
    print2Screen(60, 20, "NEbN", 0);  
  }
  if(heading>=45 && heading<56.25){
    print2Screen(60, 20, "NE", 0);  
  }
  if(heading>=56.26 && heading<67.50){
    print2Screen(60, 20, "NEbE", 0);  
  }
  if(heading>=67.50 && heading<78.75){
    print2Screen(60, 20, "ENE", 0);  
  }
  if(heading>=78.75 && heading<90){
    print2Screen(60, 20, "EbN", 0);  
  }
  if(heading>=90 && heading<101.25){
    print2Screen(60, 20, "E", 0);  
  }
  if(heading>=101.25 && heading<112.50){
    print2Screen(60, 20, "EbS", 0);  
  }
  if(heading>=112.50 && heading<123.75){
    print2Screen(60, 20, "ESE", 0);  
  }
  if(heading>=123.75 && heading<135){
    print2Screen(60, 20, "SEbE", 0);  
  }
  if(heading>=135 && heading<146.25){
    print2Screen(60, 20, "SE", 0);  
  }
  if(heading>=146.25 && heading<157.50){
    print2Screen(60, 20, "SEbS", 0);  
  }
  if(heading>=157.50 && heading<168.75){
    print2Screen(60, 20, "SSE", 0);  
  }
  if(heading>=168.75 && heading<180){
    print2Screen(60, 20, "SbE", 0);  
  }
  if(heading>=180 && heading<191.25){
    print2Screen(60, 20, "S", 0);  
  }
  if(heading>=191.25 && heading<202.50){
    print2Screen(60, 20, "SbW", 0);  
  }
  if(heading>=202.50 && heading<213.75){
    print2Screen(60, 20, "SSW", 0);  
  }
  if(heading>=213.75 && heading<225){
    print2Screen(60, 20, "SWbS", 0);  
  }
  if(heading>=225 && heading<236.25){
    print2Screen(60, 20, "SW", 0);  
  }
  if(heading>=236.25 && heading<247.50){
    print2Screen(60, 20, "SWbW", 0);  
  }
  if(heading>=247.50 && heading<258.75){
    print2Screen(60, 20, "WSW", 0);  
  }
  if(heading>=258.75 && heading<270){
    print2Screen(60, 20, "WbS", 0);  
  }
  if(heading>=270 && heading<281.25){
    print2Screen(60, 20, "W", 0);  
  }
  if(heading>=281.25 && heading<292.50){
    print2Screen(60, 20, "WbN", 0);  
  }
  if(heading>=292.50 && heading<303.75){
    print2Screen(60, 20, "WNW", 0);  
  }
  if(heading>=303.75 && heading<315){
    print2Screen(60, 20, "NWbW", 0);  
  }
  if(heading>=315 && heading<326.25){
    print2Screen(60, 20, "NW", 0);  
  }
  if(heading>=326.25 && heading<337.50){
    print2Screen(60, 20, "NWbN", 0);  
  }
  if(heading>=337.50 && heading<348.75){
    print2Screen(60, 20, "NNW", 0);  
  }
  if(heading>=348.75 && heading<366){
    print2Screen(60, 20, "NbW", 0);  
  }
}
