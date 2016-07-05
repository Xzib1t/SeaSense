// Created by Georges Gauthier - glgauthier@wpi.edu
// Last updated June 2016

#ifndef SeaSense_h
#define SeaSense_h

#include "Arduino.h"
#include "globals.h"
#include "SPI.h"
#include "Cli.h"
#include "dataCollection.h"
#include "display.h"
#include "avr/wdt.h"
#include <avr/io.h>
#include <avr/interrupt.h>
#include <avr/sleep.h>
#include <string.h>


class SeaSense
{
    public:
        SeaSense();
        void Initialize();
        void BluetoothClient();
        void CollectData();
        int ReadAnalogPin(int pin);

    private:
        void getHallEffect();
        boolean init; // indicator for initialization complete
        boolean newCli; // indicator for new bluetooth client command recieved 
        int _i; // current location in bluetooth input character buffer
        int _rxCmdSize; // size of bluetooth command buffer upon command rx
        char rxChar; // current char being recieved from the bluetooth serial port
        char* _prevCmd; // previous command entered (only stores argv[0])
};

#endif