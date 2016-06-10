// Created by Georges Gauthier - glgauthier@wpi.edu
// Last updated June 2016

#ifndef SeaSense_h
#define SeaSense_h

#include "Arduino.h"
#include "globals.h"
//#include "RTClib.h"
#include "SPI.h"
//#include "SD.h"
#include "Cli.h"
#include "dataCollection.h"
//#include "Adafruit_Sensor.h"
//#include "Adafruit_HMC5883_U.h"
//#include "Adafruit_ADXL345_U.h"
#include "avr/wdt.h"
#include <avr/io.h>
#include <avr/interrupt.h>
#include <string.h>


class SeaSense
{
    public:
        SeaSense(int output, int light_s0, int light_s1);
        void Initialize();
        void BluetoothClient();
        void CollectData();

    private:
        boolean init; // initialization complete
        boolean newCli; // bt command client display 
        int _output;
        int _i;
        int _rxCmdSize;
        char rxChar;   
        int _s0, _s1;

};

#endif