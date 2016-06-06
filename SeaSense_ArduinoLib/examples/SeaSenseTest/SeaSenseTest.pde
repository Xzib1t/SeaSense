// Created by Georges Gauthier - glgauthier@wpi.edu
// ported from the original seasense PIC code

#include <SeaSense.h>

// create a new instanse of the SeaSense library "ss1"
// pin 13 = indicator LED
SeaSense ss1(13,47,6,7,A15);

void setup(){
    // initialize serial coms (FOR DEBUG ONLY)
    Serial.begin(9600);
    
    // set the RTC based on the program compile time
    // set to false to use the rtc_set command
    RTC_AUTOSET = false;
    
    // Initialize the sensor suite
    ss1.Initialize();
}

void loop(){
    // Scan the bluetooth port for new data packets
    ss1.BluetoothClient();
    ss1.CollectData();
}