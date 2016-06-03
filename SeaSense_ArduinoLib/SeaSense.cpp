// Created by Georges Gauthier - glgauthier@wpi.edu
// ported from the original seasense PIC code

#include "Arduino.h"
#include "globals.h"
#include "SeaSense.h"
#include "RTClib.h"
#include "SPI.h"
#include "SD.h"
#include "Cli.h"
#include "dataCollection.h"
#include "avr/wdt.h" 
#include <avr/io.h>
#include <avr/interrupt.h>

// global vars (defined in globals.h)
boolean RTC_AUTOSET;
boolean logData; 
boolean sd_logData;
boolean app_logData;
RTC_DS1307 rtc;
char Timestamp[9];
double Temp = 0.0;
unsigned int Depth = 0;
int Cond = 0;
int Light = 0;
int Head = 0;
int AccelX = 0,AccelY = 0,AccelZ = 0;
int GyroX = 0,GyroY = 0,GyroZ = 0;
File SDfile;

char cli_rxBuf [MAX_INPUT_SIZE];
int count;

// Initializations are done here automatically,
SeaSense::SeaSense(int output){
    // disable the watchdog timer
    wdt_disable();
    
    // LED pin
    pinMode(output,OUTPUT);
    _output = output;
    
    // default to setting the RTC based on prog compile time
    // this var can be changed from within the arduino sketch
    RTC_AUTOSET = true;
    
    // if an Arduino Mega is used with ethernet shield, this segment
    // will disable the ethernet chip and allow for the SD card to be 
    // read from (addresses this bug http://forum.arduino.cc/index.php?topic=28763.0)
    pinMode(10, OUTPUT); // per SD install instructions
    pinMode(10, OUTPUT); // per SD install instructions
    digitalWrite(10, HIGH); 
    
    // don't enable data logging by default
    logData = false;
    sd_logData = false;
    app_logData = false;
}

// Initialize - sets up a SoftwareSerial bluetooth client. Can perform other initialization code here as well
void SeaSense::Initialize(){

    // change the bluetooth serial data rate to 9600 baud
    //Serial1.begin(115200);
    //Serial1.print("$");
    //Serial1.print("$");
    //Serial1.print("$");
    //delay(100);
    //Serial1.println("U,9600,N");
    Serial1.begin(115200);
    delay(5);
    Serial1.println("Bluetooth successfully configured");
    
    Serial1.print("Initializing SD card ...");
    if (!SD.begin(SD_CS))
        Serial1.println(" failed");
    else
        Serial1.println(" done");
    
    // set up the RTC
    if (! rtc.begin()) {
        Serial1.println("Error: Couldn't find RTC. Try a system reset");
        while (1);
    }

    if ((! rtc.isrunning()) & (RTC_AUTOSET == 1)) {
        Serial1.println("RTC is NOT running!");
        // following line sets the RTC to the date & time this sketch was compiled
        rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
        Serial1.println("RTC Autoset");
    }
    else if ((! rtc.isrunning()) & (RTC_AUTOSET == 0)) {
        Serial1.println("RTC unconfigured - please use rtc_set to configure the RTC");
    }
    else
        Serial1.println("RTC successfully initialized");
    
    // initialize ISR(s)
    cli();             // disable global interrupts
    TCCR1A = 0;        // set entire TCCR1A register to 0
    TCCR1B = 0;
 
    // set compare match register to desired timer count:
    OCR1A = 6249; // 1/10 second per int at 256 prescale (62500/(6249+1))
    // turn on CTC mode:
    TCCR1B |= (1 << WGM12);
    // Set CS10 and CS12 bits for 256 prescaler:
    TCCR1B |= (1 << CS12);
    // enable timer compare interrupt:
    TIMSK1 |= (1 << OCIE1A);
    // enable global interrupts:
    sei();
    
    newCli = true; // will write '>' for bt CLI if true
    init = true;
    digitalWrite(_output,HIGH); // indicate config complete
    
    Serial1.print("Type in ");
    Serial1.print((char)0x22); 
    Serial1.print("help"); 
    Serial1.print((char)0x22); 
    Serial1.println(" for a list of commands");
}

// BluetoothClient - reads in new characters from the bluetooth 
// serial port and parses them as an input string upon detecting
// '\n'. I've tested this to work with PuTTY
void SeaSense::BluetoothClient(){
    // if a new char is detected from the serial port, save it
    if(Serial1.available())
    {
        _i++; // leave room for '>'
        if (_i == (MAX_INPUT_SIZE-1)) _i = 0; // wrap around if overflow
        char rxChar = (char)Serial1.read(); // read in new char
        switch(rxChar){
            case '\r': 
                _rxCmdSize = _i;
                newCli = true;
                Serial1.println('\0'); // jump to a new line
              break;
            default: 
                cli_rxBuf[_i] = rxChar;
                Serial1.print(cli_rxBuf[_i]);
        }
    }
    if (newCli == true)
    {
        processCMD(&cli_rxBuf[1],_rxCmdSize); // look for a command (see Cli.cpp)
        while(_i>0){ // clear out command from buffer
            cli_rxBuf[_i] = '\0';
            _i--;
        }
        cli_rxBuf[0] = '>'; // print '>' char
        Serial1.print(cli_rxBuf); 
        
        newCli = false;
    }
    return;
}

void SeaSense::CollectData()
{
    getTime();
    return;
}

// Interrupt is called once a second, 
ISR(TIMER1_COMPA_vect) 
{
  // log data to SD card
  if(sd_logData & SDfile){
      SDfile.print(Timestamp); SDfile.print(",");
      SDfile.print(Temp); SDfile.print(",");
      SDfile.print(Depth); SDfile.print(",");
      SDfile.print(Cond); SDfile.print(",");
      SDfile.print(Light); SDfile.print(",");
      SDfile.print(Head); SDfile.print(",");
      SDfile.print(AccelX); SDfile.print(",");
      SDfile.print(AccelY); SDfile.print(",");
      SDfile.print(AccelZ); SDfile.print(",");
      SDfile.print(GyroX); SDfile.print(",");
      SDfile.print(GyroY); SDfile.print(",");
      SDfile.println(GyroZ);
      return;
  }    
    
  // reset count at slightly faster than 1hz (bluetooth data log frequency)
  if(count<9) 
      count++;
  else count = 0;
    
  // log data over the bluetooth port
  if(!SDfile & (count == 0))
  {
      if(logData){
          Serial1.print(Timestamp); Serial1.print("\t");
          Serial1.print(Temp); Serial1.print("\t");
          Serial1.print(Depth); Serial1.print("\t");
          Serial1.print(Cond); Serial1.print("\t");
          Serial1.print(Light); Serial1.print("\t");
          Serial1.println(Head);
          return;
      }
      else if(app_logData){
          Serial1.print(Timestamp); Serial1.print(",");
          Serial1.print(Temp); Serial1.print(",");
          Serial1.print(Depth); Serial1.print(",");
          Serial1.print(Cond); Serial1.print(",");
          Serial1.print(Light); Serial1.print(",");
          Serial1.print(Head); Serial1.print(",");
          Serial1.print(AccelX); Serial1.print(",");
          Serial1.print(AccelY); Serial1.print(",");
          Serial1.print(AccelZ); Serial1.print(",");
          Serial1.print(GyroX); Serial1.print(",");
          Serial1.print(GyroY); Serial1.print(",");
          Serial1.print(GyroZ); Serial1.print(",");
          Serial1.print("U+1F4A9");
          return;
      }
  }

}
