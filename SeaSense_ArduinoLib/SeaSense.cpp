// Created by Georges Gauthier - glgauthier@wpi.edu
// ported from the original seasense PIC code

#include "Arduino.h"
#include "globals.h"
#include "SeaSense.h"
#include "RTClib.h"
#include "SPI.h"
#include "SD.h"
#include "Cli.h"
#include "avr/wdt.h" 

// global vars (defined in globals.h)
boolean RTC_AUTOSET;
RTC_DS1307 rtc;

char cli_rxBuf [MAX_INPUT_SIZE];

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
}

// Initialize - sets up a SoftwareSerial bluetooth client. Can perform other initialization code here as well
void SeaSense::Initialize(){

    // change the bluetooth serial data rate to 9600 baud
    Serial1.begin(115200);
    Serial1.print("$");
    Serial1.print("$");
    Serial1.print("$");
    delay(100);
    Serial1.println("U,9600,N");
    Serial1.begin(9600);
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
    
}

