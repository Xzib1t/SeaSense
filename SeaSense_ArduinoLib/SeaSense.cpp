// Created by Georges Gauthier - glgauthier@wpi.edu
// ported from the original seasense PIC code

#include "Arduino.h"
#include "globals.h"
#include "SeaSense.h"
#include "SPI.h"
#include "Cli.h"
#include "dataCollection.h"
#include "display.h"
#include "avr/wdt.h" 
#include <avr/io.h>
#include <avr/interrupt.h>
#include <string.h>


// global vars (defined in globals.h)
boolean RTC_AUTOSET;
boolean logData; 
boolean sd_logData;
boolean app_logData;
boolean noSD;
boolean adc_ready;
RTC_DS1307 rtc;
char Timestamp[9];
double Temp = 0.0;
unsigned int Depth = 0;
int Cond = 0;
unsigned long Light = 0;
int Head = 0;
int AccelX = 0,AccelY = 0,AccelZ = 0; // units of m/s^2
int GyroX = 0,GyroY = 0,GyroZ = 0; // units of microTeslas
File SDfile;
Adafruit_HMC5883_Unified mag;
Adafruit_ADXL345_Unified accel;
Adafruit_SSD1306 display(OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS);


char cli_rxBuf [MAX_INPUT_SIZE];
int count; // used in timer1 interrupt
int vBat;

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
    
    // default to setting the RTC based on prog compile time (autoset = true)
    // this var can be changed from within the arduino sketch
    RTC_AUTOSET = true;
    
    // if an Arduino Mega is used with ethernet shield, this segment
    // will disable the ethernet chip and allow for the SD card to be 
    // read from (addresses this bug http://forum.arduino.cc/index.php?topic=28763.0)
    pinMode(10, OUTPUT); // per SD install instructions (ethernet chip CS)
    digitalWrite(10, HIGH); // de-assert chip select on ethernet chip (keeps spi lines clear)
    pinMode(53, OUTPUT);    // default SS pin on arduino mega (must be set as output)
    pinMode(SD_CS, OUTPUT); // ss pin for SD card on ethernet shield
    
    pinMode(A10,INPUT);
    pinMode(A11,INPUT);
    pinMode(A12,INPUT);
    pinMode(A13,INPUT);
    
    // don't enable data logging by default
    // the states of these booleans dictate whether or not data is written to the
    // SD card or serial port
    init = false;
    logData = false;
    sd_logData = false;
    app_logData = false;
    noSD = true;
    adc_ready = false;
}

/* Initialize - used to configure all I/O and ISRs
* - initializes serial comms on the bluetooth port (serial1)
* - configures Timer5 for hardware edge counting (light sensor)
* - configures a 10Hz Timer1 interrupt (for writing data to serial/SD)
* - configures an ADC interrupt routine
* - initializes the SD card and RTC
*/ 
void SeaSense::Initialize(){
    /* Initialize the display */
    digitalWrite(7,LOW);
    display.begin(SSD1306_SWITCHCAPVCC);
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(WHITE);
    display.setCursor(0,0);
    display.print("System init . . .\n");
    display.display();
    digitalWrite(7,HIGH);
    
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
    // MUX[5:0] = first channel to read = 100010 = ADC channel 10
    ADMUX |= ((1 << REFS0)|(1 << ADLAR)|(1 << MUX1));
    ADCSRB |= (1 << MUX5);
    
    // ADC status register - DATASHEET PG 285
    // enable ADC (ADEN), enable ADC interrupt (ADIE) 
    // clear ADPSx prescale and set to 125kHz samp rate (0.000104 sec per 10 samps)
    ADCSRA |= ((1 << ADEN)|(1 << ADIE)|(1 << ADPS2)|(1 << ADPS1)|(1 << ADPS0));
    
    sei(); // enable global interrupts:
    
    Serial1.println(F("System Initialization..."));
    
    // initialize the SD card
    // first search for the actual card
    digitalWrite(7,HIGH);
    Serial1.print(F("\tSearching for SD card ..."));
    if (!card.init(SPI_HALF_SPEED, SD_CS)) 
        Serial1.println(F(" card not found"));
    else {
        // if the card is present, enable it on the SPI interface
        Serial1.println(F(" card found"));
        Serial1.print(F("\tInitializing SD card ..."));
        if (!(SD.begin(SD_CS)))
            Serial1.println(F(" failed"));
        else{
            Serial1.println(F(" done"));
            noSD = false;
        }
    }
    
    ADCSRA |= (1 << ADSC);  // Start A2D Conversions
    
    // initialize the light sensor (only matters if using TSL230r sensor)
    light_sensor_init(_s0,_s1);
    
    // initialize the RTC
    if (! rtc.begin()) {
        Serial1.println(F("\tError: Couldn't find RTC. Try a system reset"));
        //while(1); // wait for RTC to init (could cause an issue if no RTC connected)
    }
    if ((! rtc.isrunning()) & (RTC_AUTOSET == 1)) {
        Serial1.println(F("\tRTC is NOT running!"));
        // following line sets the RTC to the date & time this sketch was compiled
        rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
        Serial1.println(F("\tRTC Autoset"));
    }
    else if ((! rtc.isrunning()) & (RTC_AUTOSET == 0)) {
        Serial1.println(F("\tRTC unconfigured - please use rtc_set to configure the RTC"));
    }
    else
        Serial1.println(F("\tRTC successfully initialized"));
    
    /* Initialize the magnetometer*/
    mag = Adafruit_HMC5883_Unified(12345); // see globals.h
    if(!mag.begin())
    {
        Serial1.println(F("\tNo HMC5883 detected ... Check your wiring!"));
        //while(1);
    } else Serial1.println(F("\tHMC5883 successfully initialized"));
    
    /* Initialize the accelerometer */
    accel = Adafruit_ADXL345_Unified(23456); // see globals.h
    if(!accel.begin())
    {
        Serial1.println(F("\tNo ADXL345 detected ... Check your wiring!"));
        //while(1);
    } else {
        Serial1.println(F("\tADXL345 successfully initialized with 4G range"));
        accel.setRange(ADXL345_RANGE_4_G);
    }
    
    /* Initialize the gyroscope */
    Serial1.print(F("\tInitializing gyroscope ..."));
    setupL3G4200D(2000); // Configure L3G4200  - 250, 500 or 2000 deg/sec
    //delay(1500); //wait for the sensor to be ready 
    Serial1.println(F(" done"));
    
    digitalWrite(4,HIGH);
    digitalWrite(7,LOW);
    display.print("done!\n");
    display.display();
    digitalWrite(4,LOW);
    digitalWrite(7,HIGH);
    
    newCli = true; // will write '>' for bt CLI if true
    init = true; // indicate that initilization is complete (not actually used)
    
    
    /* prompt users to enter a new command */
    Serial1.print(F("\tType in "));
    Serial1.print((char)0x22); 
    Serial1.print(F("help")); 
    Serial1.print((char)0x22); 
    Serial1.println(F(" for a list of commands"));
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
                Serial1.print(F("\n\r")); // jump to a new line
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
        //if(app_logData) Serial1.print(F(","));
        
        // reset the new command boolean so that this segment of code doesn't repeat
        newCli = false;
    }
    return;
}

// lowest priority code - used for sending pre-gathered sensor data to
// a serial log or file and clearing the ADC for more interrupts.
// This code should be run from the main loop of your arduino sketch
void SeaSense::CollectData()
{
    // note that light sensor is being read from TIMER1 ISR for better timing interval accuracy
    getTime();
    getADCreadings();
    getMag();
    getAccel();
    getGyro();
    
    return;
}

// Interrupt is called once every 100mS - checks to see if any type of
// serial or file logging is enabled and acts accordingly.
// File logs will be updated every ISR; serial logs every 10 ISRs
ISR(TIMER1_COMPA_vect) 
{
  if(init == false) return;
    
  getLight(); // read in light from hardware counter exactly every 100ms
    
    // log data to SD card
    if(sd_logData & SDfile){
      digitalWrite(OLED_CS,HIGH);
      digitalWrite(22,HIGH); // indicate config complete with LED on pin 13
      SDfile.print(Timestamp); SDfile.print(F(","));
      SDfile.print(Temp); SDfile.print(F(","));
      SDfile.print(Depth); SDfile.print(F(","));
      SDfile.print(Cond); SDfile.print(F(","));
      SDfile.print(Light); SDfile.print(F(","));
      SDfile.print(Head); SDfile.print(F(","));
      SDfile.print(AccelX); SDfile.print(F(","));
      SDfile.print(AccelY); SDfile.print(F(","));
      SDfile.print(AccelZ); SDfile.print(F(","));
      SDfile.print(GyroX); SDfile.print(F(","));
      SDfile.print(GyroY); SDfile.print(F(","));
      SDfile.print(GyroZ); SDfile.print(F("\n\r"));
      //return;
    }
    else if(app_logData & !logData){
        digitalWrite(22,HIGH); // indicate config complete with LED on pin 13
      count2++;
      //if (count2 == 1) Serial1.print("Temperature"); Serial1.print("\n\r");
        
      Serial.println(F("Logging data to app"));
      Serial1.print(Timestamp); Serial1.print(F(","));
      Serial1.print(Temp); Serial1.print(F(","));
      Serial1.print(Depth); Serial1.print(F(","));
      Serial1.print(Cond); Serial1.print(F(","));
      Serial1.print(Light); Serial1.print(F(","));
      Serial1.print(Head); Serial1.print(F(","));
      Serial1.print(AccelX); Serial1.print(F(","));
      Serial1.print(AccelY); Serial1.print(F(","));
      Serial1.print(AccelZ); Serial1.print(F(","));
      Serial1.print(GyroX); Serial1.print(F(","));
      Serial1.print(GyroY); Serial1.print(F(","));
      Serial1.print(GyroZ); Serial1.print(F(",\n"));
      //Serial1.print(F("U+1F4A9")); Serial1.print(F(","));
      
      //if (count2 == 50) {app_logData = false; count2=0; Serial1.print(F("U+1F4A9")); Serial1.print(F(","));}
      //return;
    } 
    
    if (!app_logData) count2 = 0;
    // keep a rolling count of the number of interrupts triggered
    if(count<9) 
      count++;
    else count = 0;

    // log data over the bluetooth port every time the count rolls over
    if(count == 0)
    {

      if(logData & !app_logData){
          digitalWrite(22,HIGH); // indicate config complete with LED on pin 13
          Serial1.print(Timestamp); Serial1.print(F("\t"));
          Serial1.print(Temp); Serial1.print(F("\t"));
          Serial1.print(Depth); Serial1.print(F("\t"));
          Serial1.print(Cond); Serial1.print(F("\t"));
          Serial1.print(Light); Serial1.print(F("\t"));
          Serial1.println(Head);
          //return;
      }
        digitalWrite(SD_CS,HIGH);
        digitalWrite(OLED_CS,LOW);
        display.clearDisplay();
        display.setTextSize(1);
        display.setTextColor(WHITE);
        display.setCursor(40,2);
        display.print(Timestamp);
        display.setCursor(8,20);
        display.print(Temp);
        display.setCursor(95,20);
        display.print(Light);
        display.setCursor(115,48);
        display.print("lx");
        display.setCursor(24,48);
        display.print("C");
        drawArrow(Head);
        if (noSD) { // if no SD card is available, indicate so
            display.drawBitmap(0, 0, dispNoCard, 128, 64, WHITE);
            display.setCursor(2,1); display.print("NC");
        } else if(app_logData | logData | sd_logData) {
            display.drawBitmap(0, 0, dispCardWrite, 128, 64, WHITE);
            display.setCursor(16,2);
            if(app_logData)
                display.print("A"); // logging to (A)pp
            if(logData)
                display.print("U"); // logging to (U)ser
            if(sd_logData)
                display.print("F"); // logging to (F)ile
        } else {
            display.drawBitmap(0, 0, dispCard, 128, 64, WHITE);
        }
        drawBatInd();
        display.display();
        digitalWrite(OLED_CS,HIGH);
        digitalWrite(SD_CS,LOW);
    }
    
    if((!app_logData) & (!logData) & (!sd_logData))
    {
        digitalWrite(22,LOW); // indicate config complete with LED on pin 13
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
    } else {
        adc_ready = true;
    }
}
 