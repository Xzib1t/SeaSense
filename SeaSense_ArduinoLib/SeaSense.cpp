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
#include <string.h>

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
unsigned int Light = 0;
int Head = 0;
int AccelX = 0,AccelY = 0,AccelZ = 0;
int GyroX = 0,GyroY = 0,GyroZ = 0;
File SDfile;

char cli_rxBuf [MAX_INPUT_SIZE];
int count; // used in timer1 interrupt


// used for ADC interrupt routine
byte adc_channel = 10; // first ADC channel to be read from 
int adcBuf[ADC_BUFFER_SIZE]; // global buffer for incoming ADC readings
byte adc_pos = 0; // position in adcBuf

int count2 = 0; // temporarily here for use with testing the android app

// set up variables using the SD utility library functions:
Sd2Card card;

// Initializations are done here automatically,
SeaSense::SeaSense(int output, int light_s0, int light_s1){
    // disable the watchdog timer
    wdt_disable();
    
    _s0 = light_s0;
    _s1 = light_s1;
    
    // LED pin
    pinMode(output,OUTPUT);
    _output = output;
    
    // default to setting the RTC based on prog compile time
    // this var can be changed from within the arduino sketch
    RTC_AUTOSET = true;
    
    // if an Arduino Mega is used with ethernet shield, this segment
    // will disable the ethernet chip and allow for the SD card to be 
    // read from (addresses this bug http://forum.arduino.cc/index.php?topic=28763.0)
    pinMode(10, OUTPUT); // per SD install instructions (ethernet chip CS)
    digitalWrite(10, HIGH); 
    pinMode(53, OUTPUT);    // SS pin on arduino mega
    pinMode(SD_CS, OUTPUT);
    
    // don't enable data logging by default
    logData = false;
    sd_logData = false;
    app_logData = false;
}

/* Initialize - used to configure all I/O and ISRs
* - initializes serial comms on the bluetooth port (serial1)
* - configures Timer5 for hardware edge counting (light sensor)
* - configures a 10Hz Timer1 interrupt (for writing data to serial/SD)
* - configures an ADC interrupt routine
* - initializes the SD card and RTC
*/ 
void SeaSense::Initialize(){
    
    /* Perform all critical init with interrupts disabled */
    cli(); // disable global interrupts
    
    Serial1.begin(115200); // turn on serial to bluetooth module
    
    // Timer 5 hardware hardware pulse count (used for light sensor)
    // see http://forum.arduino.cc/index.php?topic=259063.0
    TCCR5A = 0; // set entire TCCR5A register to 0
    TCCR5B |= ((1 << CS52)|(1 << CS51)|(1 << CS50)); // enable external clock source on rising edge (pin 47)
    TIMSK5 |= (1 << TOIE5); // enable overflow isr
    
    // Timer1 interrupt routine for logging data
    TCCR1A = 0; // set entire TCCR1A register to 0
    TCCR1B = 0; // set entire TCCR1B register to 0
    OCR1A = 6249; // 1/10 second per int at 256 prescale (62500/(6249+1))
    TCCR1B |= (1 << WGM12); // turn on CTC mode
    TCCR1B |= (1 << CS12);  // Set CS10 and CS12 bits for 256 prescaler:
    TIMSK1 |= (1 << OCIE1A);    // enable timer compare interrupt:
    
    // ADC conversion ISR
    // REFS0 - AVCC reference with external cap at AREF pin
    // ADLAR - left adjust result
    // MUX[5:0] = 100010 = ADC channel 10
    ADMUX |= ((1 << REFS0)|(1 << ADLAR)|(1 << MUX1));
    ADCSRB |= (1 << MUX5);
    
    // ADC status register
    // enable ADC (ADEN), enable ADC interrupt (ADIE) 
    // clear ADPSx prescale and set to 250kHz samp rate
    ADCSRA |= ((1 << ADEN)|(1 << ADIE)|(1 << ADPS2)|(1 << ADPS1));
    
    sei(); // enable global interrupts:
    
    ADCSRA |= (1 << ADSC);  // Start A2D Conversions
    
    Serial1.println("System Initialization...");
    
    // initialize the light sensor (only matters if using TSL230r sensor)
    light_sensor_init(_s0,_s1);
    
    // initialize the SD card
    Serial1.print("\tSearching for SD card ...");
   // if (!SD.begin(SD_CS))
    if (!card.init(SPI_HALF_SPEED, SD_CS)) 
        Serial1.println(" card not found");
    else {
        Serial1.println(" card found");
        Serial1.print("\tInitializing SD card ...");
        if (!SD.begin(SD_CS))
            Serial1.println(" failed");
        else
            Serial1.println(" done");
    }
    
    
    // initialize the RTC
    if (! rtc.begin()) {
        Serial1.println("\tError: Couldn't find RTC. Try a system reset");
        while(1); // wait for RTC to init (could cause an issue if no RTC connected)
    }
    if ((! rtc.isrunning()) & (RTC_AUTOSET == 1)) {
        Serial1.println("\tRTC is NOT running!");
        // following line sets the RTC to the date & time this sketch was compiled
        rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
        Serial1.println("\tRTC Autoset");
    }
    else if ((! rtc.isrunning()) & (RTC_AUTOSET == 0)) {
        Serial1.println("\tRTC unconfigured - please use rtc_set to configure the RTC");
    }
    else
        Serial1.println("\tRTC successfully initialized");
    
    newCli = true; // will write '>' for bt CLI if true
    init = true; // indicate that initilization is complete (not actually used)
    digitalWrite(_output,HIGH); // indicate config complete with LED on pin 13
    
    /* prompt users to enter a new command */
    Serial1.print("\tType in ");
    Serial1.print((char)0x22); 
    Serial1.print("help"); 
    Serial1.print((char)0x22); 
    Serial1.println(" for a list of commands");
}

/* BluetoothClient - reads in new characters from the bluetooth 
* serial port and parses them as an input string upon detecting
* '\r'. I've tested this to work with PuTTY and arduino's serial monitor
*/
void SeaSense::BluetoothClient(){
    if(Serial1.available()) // if a new byte has been transmitted 
    {
        _i++; // leave room for '>' in the CLI character buffer
        if (_i == (MAX_INPUT_SIZE-1)) _i = 0; // wrap around if overflow
        byte rxChar = Serial1.read(); // read in new char
        
        /* perform different operations based on the character recieved */
        switch(rxChar){
            /* if a carriage return is detected */
            case 0x0D: 
                _rxCmdSize = _i;
                newCli = true;
                /*COMMENT OUT FOR JOE*/
                Serial1.print("\n\r"); // jump to a new line
              break;
                
            /* if the backspace key is pressed, remove the char and realign the index */
            case 0x7F: 
                if(_i<=1){ // don't allow for the deletion of the '>' character
                    while(_i>0){ // flush input buffer
                        cli_rxBuf[_i] = '\0';
                        _i--;
                    }
                    cli_rxBuf[0] = '>'; // add '>' char back in
                }
                else{ // clear out incorrect char and move buffer index
                    Serial1.print((char)rxChar);
                    cli_rxBuf[_i-1]='\0';
                    _i-=2;
                }
               break;
                
            /* if any other keys are pressed, store them in the buffer */
            default: // if any other keys are pressed
                cli_rxBuf[_i] = (char)rxChar;
                /*COMMENT OUT FOR JOE*/
                if(!app_logData) Serial1.print(cli_rxBuf[_i]);
        }
    }
    
    /* if a carriage return is detected, search cli_rxBuf[] for a command */
    if (newCli == true)
    {
        // look for a command (see Cli.cpp)
        processCMD(&cli_rxBuf[1],_rxCmdSize); 
        
        // flush the buffer and prepare it for new input
        while(_i>0){ 
            cli_rxBuf[_i] = '\0';
            _i--;
        }
        // print '>' char
        cli_rxBuf[0] = '>'; 
        Serial1.print(cli_rxBuf);
        
        // if logging data for the android app, also include a delimiter
        if(app_logData) Serial1.print(",");
        
        // reset the new command boolean so that this segment of code doesn't repeat
        newCli = false;
    }
    return;
}

// lowest priority code - used for sending pre-gathered sensor data to
// a serial log or file. This code should be run from the main loop of your
// arduino sketch
void SeaSense::CollectData()
{
    // note that light sensor is being read from TIMER1 ISR for better timing interval accuracy
    getTime();
    getADCreadings();
    
    return;
}

// Interrupt is called once every 100mS - checks to see if any type of
// serial or file logging is enabled and acts accordingly.
// File logs will be updated every ISR; serial logs every 10 ISRs
ISR(TIMER1_COMPA_vect) 
{
  getLight(); // read in light from hardware counter exactly every 100ms
    
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
      SDfile.print(GyroZ); SDfile.print("\n\r");
      return;
    }
    else if(app_logData & !logData){
      //Serial1.print(Timestamp); Serial1.print(",");
      Serial.println("Logging data to app");
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
      //Serial1.print("U+1F4A9"); Serial1.print(",");
      count2++;
      if (count2 == 50) {app_logData = false; count2=0; Serial1.print("U+1F4A9"); Serial1.print(",");}
      return;
    } 
    
    // keep a rolling count of the number of interrupts triggered
    if(count<9) 
      count++;
    else count = 0;

    // log data over the bluetooth port every time the count rolls over
    if(count == 0)
    {

      if(logData & !app_logData){
          Serial1.print(Timestamp); Serial1.print("\t");
          Serial1.print(Temp); Serial1.print("\t");
          Serial1.print(Depth); Serial1.print("\t");
          Serial1.print(Cond); Serial1.print("\t");
          Serial1.print(Light); Serial1.print("\t");
          Serial1.println(Head);
          return;
      } 
    }
}

// Timer overflow for edge count interrupt (used with light sensor)
// this ISR will occur if the hardware edge counter doesn't detect any
// new rising edges before TIMER5 overflows
ISR(TIMER5_OVF_vect){ 
  carryOut++;
}

// ADC ISR
// https://bennthomsen.wordpress.com/arduino/peripherals/analogue-input/
// http://www.avrfreaks.net/forum/sampling-multiple-adc-channels
// Users' guide pg 283
ISR(ADC_vect){
    // read in current adc value (users' guide pg 286)
    // the shifts are based on ADCD[9:0] positions as set by the ADLAR bit of ADMUX
    adcBuf[adc_pos] = ADCL >> 6; // must read low data register first
    adcBuf[adc_pos] += ADCH << 2; // followed by high data register

    // if the ADC buffer for the given channel still has room, keep adding to it.
    // when the buffer is full, this code won't run, causing ADC interrupts to hang
    // until re-enabled through calling getADCreadings() (see datacollection.cpp)
    if(adc_pos<(ADC_BUFFER_SIZE-1)) {
        // increment to next buffer position
        adc_pos++;       
        ADCSRA |= (1 << ADSC); // Start A2D Conversions (ONLY IF BUFFER ISN'T FULL)
    } 
}
 