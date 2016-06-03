// ensure this library description is only included once
#ifndef SeaSense_h
#define SeaSense_h

#include "Arduino.h"
#include "globals.h"
#include "RTClib.h"
#include "SPI.h"
#include "SD.h"
#include "Cli.h"
#include "dataCollection.h"
#include "avr/wdt.h"
#include <avr/io.h>
#include <avr/interrupt.h>


class SeaSense
{
    public:
        SeaSense(int output);
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

};

#endif