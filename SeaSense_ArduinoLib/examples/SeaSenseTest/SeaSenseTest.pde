// Created by Georges Gauthier - glgauthier@wpi.edu
// ported from the original seasense PIC code

// NOTE - This library requires the installation of adafruit's RTClib in order to work

#include <SeaSense.h>

/* create a new instanse of the SeaSense library "ss1"
*  Pins automatically used by the library include the following:
*   - Pin 47: Timer5 hardware edge counter (light sensor)
*   - Pins 18 and 19: Serial1 Tx and Rx to bluetooth module
*   - ADC10, ADC11, ADC12 for the temperature, pressure, and conductivity sensors
*   - Pins 4, 10, 11, 12, and 13 used for the SD card and SPI interface
*  Input arguments for "SeaSense NAME(ARG1,ARG2,ARG3)" :
*   - ARG0: LED indicator (lit upon successful initialization)
*   - ARG1 and ARG2: S0 and S1 pins for use with older TSL230R sensor
*/ 
SeaSense ss1(13,48,49);

void setup(){
    // Initialize USB serial coms (FOR DEBUG ONLY)
    Serial.begin(9600);
    
    // Initialize the sensor suite
    ss1.Initialize();
}

void loop(){
    // Scan the bluetooth port for new data packets
    ss1.BluetoothClient();
    
    // Process sensor data for logging 
    ss1.CollectData();
}