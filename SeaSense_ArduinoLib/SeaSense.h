/** @file SeaSense.h Header for file containing all main source code for the SeaSense Arduino lib. 
* All externally accessible library functions are contained within
* this file, along with all register configurations and ISRs.
* @author Georges Gauthier, glgauthier@wpi.edu
* @date May-July 2016
*/

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

/**
* @breif SeaSense Arduino library class.
*/
class SeaSense
{
    public:
        /** Preliminary initialization function for the sensor suite.
        *   This function runs when the line "SeaSense <name>; is called in an arduino sketch.
        */
        SeaSense();
    
        /** Main initialization function for the sensor suite.
        *- used to configure all I/O and ISRs.
        * - initializes serial comms on the bluetooth port (serial1).
        * - configures Timer5 for hardware edge counting (light sensor).
        * - configures a 10Hz Timer1 interrupt (for writing data to serial/SD).
        * - configures an ADC interrupt routine.
        * - initializes the SD card and RTC.
        */
        void Initialize();
    
        /** Reads in new characters from the bluetooth 
        * serial port and parses them as an input string upon detecting a carriage return.
        * Tested working with PuTTY, Arduino's serial monitor, and the Android app
        * @see processCMD(char *command, int size)
        */
        void BluetoothClient();
    
        /** Lowest priority code used for sending pre-gathered sensor data to
        * a serial log or file and clearing the ADC for more interrupts.
        * This code should be run from the main loop of your arduino sketch
        */
        void CollectData();
    
        /** Replacement for Arduino's analogRead so that analog pins can be read indipendently from the ADC ISR.
        *This function will disable interrupts, store the current ADC register settings, read from the given pin, 
        *and then revert the ADC register settings and re-enable the ADC isr.
        * THIS CODE IS EXPERIMENTAL AND NOT TESTED FULLY WORKING
        *- semi based on analogRead source code: http://garretlab.web.fc2.com/en/arduino/inside/arduino/wiring_analog.c/analogRead.html
        * @param pin A given analog pin
        * @return A 10-bit integer corresponding to the analog voltage on the given pin.
        */
        int ReadAnalogPin(int pin);

    private:
        /**Gets the current state of the hall effect sensor and toggles low power mode*/
        void getHallEffect();
        boolean init; /** indicator for initialization complete */
        boolean newCli; /** indicator for new bluetooth client command recieved */ 
        int _i; /** current location in bluetooth input character buffer */
        int _rxCmdSize; /** size of bluetooth command buffer upon command rx */
        char rxChar; /** current char being recieved from the bluetooth serial port */
        char* _prevCmd; /** previous command entered (only stores argv[0]) */
};

#endif