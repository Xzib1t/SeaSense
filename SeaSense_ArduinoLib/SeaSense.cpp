/** @file SeaSense.cpp File containing all main source code for the SeaSense Arduino lib. 
* All externally accessible library functions are contained within
* this file, along with all register configurations and ISRs.
* @author Georges Gauthier, glgauthier@wpi.edu
* @date May-July 2016
*/

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
#include <avr/sleep.h>
#include <string.h>

// macro for converting unsigned depth to signed number
// remember; negatives evaluate to true in an if statement
#define _UNSIGNED(X) ((X) + 32768)

// function prototypes
void printVerboseData();
void printAppData();
void printFileData();
void printOLEDdata();
void lpmWake();

//************************  global or static vars (see globals.h) *******************************
// booleans for the states of various processes
boolean RTC_AUTOSET; // allows for users to set the time using the bluetooth cli if set to false
boolean logData; // log data to cli in a human-readable format
boolean sd_logData; // log data to a file on the SD card
boolean app_logData; // log data to the cli for the andriod app 
boolean noSD; // indicates whether or not an SD card has been located
boolean adc_ready; // indicates if the ADC is ready to perform new conversions
boolean lowPowerLogging; // indicator for turning on/off bt and lcd based on Depth

// data buffer management (Bluetooth command char buffer, ADC sample buffer)
char cli_rxBuf[MAX_INPUT_SIZE]; // input character buffer
byte adc_channel = 10; // first ADC channel to be read from 
int adcBuf[ADC_BUFFER_SIZE]; // global buffer for incoming ADC readings
byte adc_pos = 0; // position in adcBuf
int count; // used in timer1 interrupt

// formatted data storage variables (these are what get printed when logging)
char Timestamp[9]; // current time stored as 'HH:MM:SS/0'
double Temp = 0.0; // current temperature reading
unsigned int Depth = 0; // current depth reading
int Cond = 0; // current conductivity
unsigned long Light = 0; // current light sensor reading
int Head = 0; // current heading
float AccelX = 0,AccelY = 0,AccelZ = 0; // current accelerometer (reading units of m/s^2)
float GyroX = 0,GyroY = 0,GyroZ = 0; // current compass reading (units of microTeslas)
int vBat; // current battery reading (from ADC)

// library objects
Sd2Card card; // sd card 
File SDfile; // current file being logged to (addressable through SDfile.write(text))
RTC_DS1307 rtc; // realtime clock module 

/* IMU */
#ifdef GY80 
    /*GY80 IMU*/
    Adafruit_HMC5883_Unified mag; // magnometer sensor
    Adafruit_ADXL345_Unified accel; // accelerometer sensor
#else
    /*Adafruit 9DOF IMU*/
    Adafruit_LSM303_Accel_Unified accel;
    Adafruit_LSM303_Mag_Unified mag;
    Adafruit_L3GD20_Unified gyro;
#endif
#ifdef _TP
        /* Internal Temp and Pressure */
        Adafruit_BMP085_Unified bmp;
        float TempInt;
        float PresInt;
#endif /* _TP */

//SPI OLED display
Adafruit_SSD1306 display(OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS); 
//*********************************************************************************************

SeaSense::SeaSense(){
    // disable the watchdog timer
    wdt_disable();
    
    // default to setting the RTC based on prog compile time (autoset = true)
    // this var can be changed from within the arduino sketch
    RTC_AUTOSET = true;
    
    // if an Arduino Mega is used with ethernet shield, this segment
    // will disable the ethernet chip and allow for the SD card to be 
    // read from (addresses this bug http://forum.arduino.cc/index.php?topic=28763.0)
//    pinMode(10, OUTPUT); // per SD install instructions (ethernet chip CS)
//    digitalWrite(10, HIGH); // de-assert chip select on ethernet chip (keeps spi lines clear)
    
    pinMode(SD_CS, OUTPUT); // ss pin for SD card on ethernet shield
    pinMode(OLED_CLK,OUTPUT);
    digitalWrite(OLED_CLK,HIGH);
    digitalWrite(SD_CS,HIGH);
    
    pinMode(53, OUTPUT);    // default SS pin on arduino mega (must be set as output)
    
    // LED indicator
    pinMode(LEDpin,OUTPUT);
    
    // ADC inputs
    pinMode(A10,INPUT); // temp
    pinMode(A11,INPUT); // pressure
    pinMode(A12,INPUT); // cond
    pinMode(A13,INPUT); // battery 
    
    // low power mode disable interrupt pin
    pinMode(LPM_WAKE, INPUT);

    // turn on the bluetooth module
    pinMode(BT_PWR,OUTPUT);
    digitalWrite(BT_PWR,HIGH);
    
    init = false; // set to true after SeaSense.Initialize() has been called
    // the states of these booleans dictate whether or not data is written to the SD card or serial port
    logData = false;
    sd_logData = false;
    app_logData = false;
    // States of the SD card and ADC read sequence
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
    lowPowerLogging = false; // keep bluetooth and the LCD on by default
    
    /* Initialize the display */
    digitalWrite(OLED_CLK,LOW);
    display.begin(SSD1306_SWITCHCAPVCC);
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(WHITE);
    display.setCursor(0,0);
    display.print("System init . . .\n");
    display.display();
    digitalWrite(OLED_CLK,HIGH); // disable the OLED disp. so that the SD card can be initialized
    
    
    /* Perform all critical init with interrupts disabled */
    cli(); // disable global interrupts
    
    Serial.begin(BT_BAUDRATE); // turn on serial to bluetooth module
    
    // Timer 5 hardware hardware pulse count (used for light sensor)
    // see http://forum.arduino.cc/index.php?topic=259063.0 for more info
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
    
    ADCSRA |= (1 << ADSC);  // Start A2D Conversions
    
    Serial.println(F("System Initialization..."));
    
    // initialize the SD card
    // first search for the actual card
    digitalWrite(OLED_CLK,HIGH);
    digitalWrite(SD_CS,LOW);
    Serial.print(F("\tSearching for SD card ..."));
    if (!card.init(SPI_HALF_SPEED, SD_CS)) 
        Serial.println(F(" card not found"));
    else {
        // if the card is present, enable it on the SPI interface
        Serial.println(F(" card found"));
        Serial.print(F("\tInitializing SD card ..."));
        if (!(SD.begin(SD_CS)))
            Serial.println(F(" failed"));
        else{
            Serial.println(F(" done"));
            noSD = false;
        }
    }
    digitalWrite(SD_CS,HIGH);
    
    // initialize the light sensor (only matters if using TSL230r sensor)
    #ifdef TSL230R 
        light_sensor_init(_S0,_S1);
    #endif /* TSL230R */
    
    // initialize the RTC
    if (! rtc.begin()) {
        Serial.println(F("\tError: Couldn't find RTC. Try a system reset"));
        //while(1); // wait for RTC to init (could cause an issue if no RTC connected)
    }
    if ((! rtc.isrunning()) & (RTC_AUTOSET == 1)) {
        Serial.println(F("\tRTC is NOT running!"));
        // following line sets the RTC to the date & time this sketch was compiled
        rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
        Serial.println(F("\tRTC Autoset"));
    }
    else if ((! rtc.isrunning()) & (RTC_AUTOSET == 0)) {
        Serial.println(F("\tRTC unconfigured - please use rtc_set to configure the RTC"));
    }
    else
        Serial.println(F("\tRTC successfully initialized"));
    
    
    #ifdef GY80 
        /* ~~~~~~~~~~~~~~~~~~~~~~~~~~ GY80 IMU ~~~~~~~~~~~~~~~~~~~~~~~~~~~ */
        mag = Adafruit_HMC5883_Unified(12345); // GY80
        accel = Adafruit_ADXL345_Unified(23456); // GY80
        accel.setRange(ADXL345_RANGE_4_G); // GY80
        setupL3G4200D(2000); // GY80 
    #else /* GY80 */
        /* ~~~~~~~~~~~~~~~~~~~~ Adafruit 9/10 DOF IMU ~~~~~~~~~~~~~~~~~~~~ */
        /* Initialize the magnetometer*/
        mag = Adafruit_LSM303_Mag_Unified(30302); // Adafruit 9DOF
        if(!mag.begin())
        {
            Serial.println(F("\tNo magnetometer detected ... Check your wiring!"));
            return;
        } else Serial.println(F("\tMagnetometer successfully initialized"));

        /* Initialize the accelerometer */
        accel = Adafruit_LSM303_Accel_Unified(30301); // Adafruit 9DOF
        if(!accel.begin())
        {
            Serial.println(F("\tNo accelerometer detected ... Check your wiring!"));
            return;
        } else {
            Serial.println(F("\tAccelerometer successfully initialized"));
            
        }

        /* Initialize the gyroscope */
        gyro  = Adafruit_L3GD20_Unified(20); // Adafruit 9DOF
        if(!gyro.begin()){
            Serial.println(F("\tNo gyroscope detected ... Check your wiring!"));
            return;
        } else {
            Serial.println(F("\tGyroscope successfully initialized"));
    }
    #endif /* GY80 */
    #ifdef _TP
        /* Internal Temp and Pressure */
        bmp = Adafruit_BMP085_Unified(10085);
        if(!bmp.begin()){
            /* There was a problem detecting the BMP085 ... check your connections */
            Serial.println(F("\tOoops, no BMP085 detected ... Check your wiring or I2C ADDR!"));
            return;
        } else {
            Serial.println(F("\tInternal temp/pressure sensor successfully initialized"));
        }
    #endif /* _TP */
    
    // indicate that initialization is done on the OLED display
    digitalWrite(SD_CS,HIGH);
    digitalWrite(OLED_CLK,LOW);
    display.print("done!\n");
    display.display();
    digitalWrite(OLED_CLK,HIGH);
    
    newCli = true; // will write '>' for bt CLI on boot if true
    init = true; // indicate that initilization is complete (not actually used, but may be useful at some point)
    
    /* prompt users to enter a new command */
    Serial.print(F("\tType in "));
    Serial.print((char)0x22); 
    Serial.print(F("help")); 
    Serial.print((char)0x22); 
    Serial.println(F(" for a list of commands"));
    
    *_prevCmd = '\0'; // initialize previous command to NULL
}

/* BluetoothClient - reads in new characters from the bluetooth 
* serial port and parses them asan input string upon detecting
* '\r'. I've tested this to work with PuTTY and arduino's serial monitor
*/
void SeaSense::BluetoothClient(){
    if(Serial.available()) // if a new byte has been transmitted 
    {
        _i++; // leave room for '>' in the CLI character buffer
        if (_i == (MAX_INPUT_SIZE-1)) _i = 0; // wrap around if overflow
        byte rxChar = Serial.read(); // read in new char
        
        /* perform different operations based on the character recieved */
        switch(rxChar){
            /* if a carriage return is detected */
            case 0x0D: 
                _rxCmdSize = _i; // save the size of the input buffer (passed to processCMD)
                newCli = true; // globally indicate that a new command has been entered
                Serial.print(F("\n\r")); // jump to a new line in the bluetooth command line interface
              break;
                
            /* if the backspace key is pressed, remove the char and realign the index */
            case 0x7F: 
                if(_i<=1){ // don't allow for the deletion of the '>' character
                    while(_i>0){ // flush input buffer (pad with \0s)
                        cli_rxBuf[_i] = '\0';
                        _i--;
                    }
                    cli_rxBuf[0] = '>'; // add '>' char back in
                }
                else{ // clear out char being deleted and move the buffer index accordingly
                    Serial.print((char)rxChar);
                    cli_rxBuf[_i-1]='\0';
                    _i-=2;
                }
               break;
            /* if the up arrow key is pressed, replace the command buffer with the prev cmd*/
            case 27:
                rxChar = Serial.read ();
                if(rxChar == 91){
                    rxChar = Serial.read();
                    if(rxChar == 65){  
                       // flush input buffer (pad with \0s)
                       while(_i > 0){ 
                            cli_rxBuf[_i] = '\0';
                            _i--;
                       }
                       Serial.write(0x7F); // delete ">" char from console
                       Serial.print(_prevCmd); // print the previous command...
                       sprintf(cli_rxBuf,"%s",_prevCmd); //... fill the input buffer with the previoud command
                       _i = strlen(cli_rxBuf)-1; //set the buffer index to the right position
                    }
                }
                break;
            
            /* if any other keys are pressed, store them in the command buffer */
            default: // if any other keys are pressed
                cli_rxBuf[_i] = (char)rxChar;
                if(!app_logData) Serial.print(cli_rxBuf[_i]);
        }
    }
    
    if ((strcmp(cli_rxBuf,">%CONNECT")==0) 
        || (strcmp(cli_rxBuf,">%DISCONNECT")==0)
        || (strcmp(cli_rxBuf,">%DT")==0)
       ){
        _rxCmdSize = _i; // save the size of the input buffer (passed to processCMD)
        newCli = true; // globally indicate that a new command has been entered
        Serial.print(F("\n\r")); // jump to a new line in the bluetooth command line interface
    }
    
    /* if a carriage return is detected, search cli_rxBuf[] for a matching command */
    if (newCli == true)
    {
        free(_prevCmd); // get rid of old previous command
        _prevCmd = (char*)calloc(strlen(cli_rxBuf),sizeof(char)); // clear out previous command buffer
        sprintf(_prevCmd,cli_rxBuf); // put new command in prevCmd buffer
    
        // look for a matching cli command and excecute it if possible (see Cli.cpp)
        processCMD(&cli_rxBuf[1],_rxCmdSize); 
        
        
        // after running the intended command, flush the buffer and prepare it for new input
        while(_i>0){ 
            cli_rxBuf[_i] = '\0';
            _i--;
        }
        // print '>' char on newline (prompt a new input from the user)
        cli_rxBuf[0] = '>'; 
        Serial.print(cli_rxBuf);
        
        // reset the new command boolean so that this segment of code doesn't repeat
        newCli = false;
    }
    return; // exit
}

// lowest priority code - used for sending pre-gathered sensor data to
// a serial log or file and clearing the ADC for more interrupts.
// This code should be run from the main loop of your arduino sketch
void SeaSense::CollectData()
{
    // Note that the following functions can be found in dataCollection.cpp
    // note that light sensor is being read from TIMER1 ISR for better timing interval accuracy
    getTime(); // update the value stored in Timestamp
    getADCreadings(); // update all ADC-based readings (Temp, Pressure, Conductivity, Battery)
    getMag(); // get a new magnometer reading
    getAccel(); // get a new accelerometer reading
    getGyro(); // get a new gyroscope reading
    getInternals(); // get internal temp/pressure
    this->getHallEffect(); // check if LPM should be turned on
    return;
}

// ReadAnalogPin - replacement for analogRead so that analog pins can be read indipendently from the ADC ISR
// this function will disable interrupts, store the current ADC register settings, read from the given pin, 
// and then revert the ADC register settings and re-enable the ADC isr
// semi based on analogRead source code: http://garretlab.web.fc2.com/en/arduino/inside/arduino/wiring_analog.c/analogRead.html
int SeaSense::ReadAnalogPin(int pin){
    int value = 0; // storage for pin reading

    // start by disabling all ISRs (remember we normally rely on an ADC isr) 
    cli();
    
    // store the ADC register states so that they may be reset later on
    int admuxVal = ADMUX;
    int adcsraVal = ADCSRA;
    int adcsrbVal = ADCSRB;
    
    ADCSRA &= ~((1 << ADIE));
    ADMUX &= ~((1<<ADLAR)|(1<<MUX4)|(1<<MUX3)|(1<<MUX2)|(1<<MUX1)|(1<<MUX0));  
    ADCSRB &= ~(1<<MUX5);
    
    // set up the registers to read a value on the given pin
    if(pin>=8){ 
        ADMUX |= ((1 << REFS0)|((pin-8)& 0x07));
        ADCSRB |= (1 << MUX5);
    } else {
        ADMUX |= ((1 << REFS0)|(pin & 0x07));
    }
    
    // start the conversion
    ADCSRA |= (1<< ADSC); 
    
    // ADSC is cleared when the conversion finishes
    while (bit_is_set(ADCSRA, ADSC));   
    
    // read in the conversion results (ADLAR=0, different from the ADC isr)
    uint8_t low, high;
    low = ADCL; // must read low data register first
    high = ADCH;

   
    // return registers to original state so that the ADC ISR may continue where it left off
    ADMUX = admuxVal;
    ADCSRA = adcsraVal;
    ADCSRB = adcsrbVal;
    
    value = (high << 8) | low;
    // re-enable interrupts
    sei(); 
    
    // return the value read from the given pin
    return value;
}

/** Timer interrupt called once every 100mS 
*- checks to see if any type of serial or file logging is enabled and acts accordingly.
* @see SeaSense::Initialize()
*/
ISR(TIMER1_COMPA_vect) 
{
    if(init == false) return; // if initialization hasn't finished, immediately exit the ISR
    
    getLight(); // read in light from hardware counter exactly every 100ms
    
    if(app_logData & !logData){ // log data to the andriod app
        printAppData();
    } 

    // keep a rolling count of the number of interrupts triggered (every 10 counts = 1 second)
    if(count<9) {
        count++;
    // all code contained within the corresponding else statement will run once per second
    } else { 
        count = 0; // don't forget to reset the counter
        
        // to speed up sampling by a factor of 10, move any of these conditionals outside of this if:else statement
        if(sd_logData & SDfile) { // log data to SD card (highest priority logging)
            printFileData();
        } else if(logData & !app_logData) { // log verbose output to the command line if enabled 
            printVerboseData();
        } 
        
        // turn off the LCD and bluetooth if below a certain depth
        // see globals.h to set LOW_POWER_DEPTH
        // guess what - negative numbers are converted to true within conditional statements! 
        if((_UNSIGNED(Depth) < _UNSIGNED(LOW_PWR_DEPTH)) && (!lowPowerLogging)) {
            lowPowerLogging = true;

            // turn off any bluetooth logging 
            logData = false;
            app_logData = false;

            // turn off bluetooth
            digitalWrite(BT_PWR,LOW); 

            // clear the LDC screen (turn all pixels off)
            digitalWrite(SD_CS,HIGH);
            digitalWrite(OLED_CLK,LOW);
            display.clearDisplay();
            display.display();
            digitalWrite(OLED_CLK,HIGH);
            digitalWrite(SD_CS,LOW);
            
        // turn on the LCD and bluetooth if above a certain depth 
        } else if ((_UNSIGNED(Depth) > _UNSIGNED(LOW_PWR_DEPTH)) && lowPowerLogging){
            lowPowerLogging = false;
            digitalWrite(BT_PWR,HIGH);
        }
        
        // update the OLED display with new sensor readings 
        if(!lowPowerLogging) printOLEDdata(); 
    }
    
    // update the logging indicator LED
    if((!app_logData) & (!logData) & (!sd_logData))  
        digitalWrite(LEDpin,LOW); // turn off data logging indicator
    else 
        digitalWrite(LEDpin,HIGH); // turn on data logging indicator

}

/** Timer overflow for edge count interrupt (used with light sensor).
* This ISR will occur if the hardware edge counter doesn't detect any
* new rising edges before TIMER5 overflows
*/
ISR(TIMER5_OVF_vect){ 
  carryOut++;
}

/** ADC hardware interrupt routine
* See the following for some useful information: 
* - https://bennthomsen.wordpress.com/arduino/peripherals/analogue-input/
* .
* - http://www.avrfreaks.net/forum/sampling-multiple-adc-channels
* .
* - Users' guide pg 283
* .
* The ADC is configured to run until the adc buffer has been filled based on ADC_BUFFER_SIZE. 
* The ADC data is processed, and the ADC ISR is then re-enabled externally for a different ADC channel as specified in dataCollection.cpp.
* Note that to add additional ADC channels to read from, you need to modify the globals for
* the number of ADCs, as well as the getADCreadings() and resetADC() functions in dataCollection.cpp
*/
ISR(ADC_vect){
    // read in current adc value (users' guide pg 286 - MUST READ ADCL FIRST)
    // the shifts are based on ADCD[9:0] positions as set by the ADLAR bit of ADMUX (ADLAR=1)
    // ADCL = (ADC1|ADC0|x5|x4|x3|x2|x1|x0)
    // ADCH = (ADC9|ADC8|ADC7|ADC6|ADC5|ADC4|ADC3|ADC2)
    adcBuf[adc_pos] = 0x0000; // make sure all 16 bits of the adc buffer are clear
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
 
// get the current state of the hall effect sensor and toggle low power mode
// NOTE: I used a slightly different layout than the original schematic:
// Pin 1 connected to VCC
// Pin 2 connected to ground
// Pin 3 Connected to LED cathode -> 390Ohm resistor -> VCC
// LPM_WAKE (digital pin 2) connected to pin 3
// see http://playground.arduino.cc/Learning/ArduinoSleepCode
// this is a member function so that I can call SeaSense:Initialize() from within it
void SeaSense::getHallEffect(){
    boolean state = digitalRead(LPM_WAKE);
    if(state == 1){ // output pulled high (LED turned off)
        if(sd_logData){ 
            Serial.print(F("Turning off data logging . . ."));
            sd_logData = !sd_logData;
            SDfile.print(F("U+1F4A9")); 
            SDfile.print(F(","));
            SDfile.close();
            Serial.println(F("Stopped logging data to file"));
            sd_logData = false;
        }
        app_logData = false;
        logData = false;
        Serial.println(F("Low power mode enabled - goodbye!"));
        delay(100);
        digitalWrite(SD_CS,HIGH);
        digitalWrite(OLED_CLK,LOW);
        display.clearDisplay();
        display.setTextSize(1);
        display.setTextColor(WHITE);
        display.setCursor(25,28);
        display.print(F("low power mode"));
        display.display();
        digitalWrite(OLED_CLK,HIGH);
        digitalWrite(SD_CS,LOW);
        
        digitalWrite(BT_PWR,LOW); // turn off the bluetooth module
       
        delay(100);
        set_sleep_mode(SLEEP_MODE_PWR_DOWN); // max power savings
        sleep_enable(); // set sleep bit in mcu
        attachInterrupt(0, lpmWake, LOW); // int0 = pin 2 = LPM_WAKE
        sleep_mode(); // put micro to sleep
        
        sleep_disable(); // resumes here on wake, runs lpmwake
        detachInterrupt(0);
        this->Initialize();
    }
    
}

/** Called automatically when waking back up from low power mode.
* NOTE that this *function* is called multiple times on wake, so there isn't
* any internal way to run a process a single time unless you use globals
*/
void lpmWake(){
        // wake from low power mode
        digitalWrite(BT_PWR,HIGH); // turn on the bluetooth module
        Serial.println(F("Waking up from low power mode ..."));
}
    
/** print user-readable sensor data to the bluetooth port */
void printVerboseData(){
    Serial.print(Timestamp); Serial.print(F("\t"));
    Serial.print(Temp); Serial.print(F("\t"));
    Serial.print(Depth); Serial.print(F("\t"));
    Serial.print(Cond); Serial.print(F("\t"));
    Serial.print(Light); Serial.print(F("\t"));
    #ifdef _TP
        Serial.print(Head); Serial.print(F("\t"));
        Serial.print(TempInt); Serial.print(F("\t"));
        Serial.println(PresInt);
    #else
        Serial.println(Head); 
    #endif
}

/** Print sensor readings to the android app */
void printAppData(){
    //Serial.println(F("Logging data to app"));
    Serial.print(Timestamp); Serial.print(F(","));
    Serial.print(Temp); Serial.print(F(","));
    Serial.print(Depth); Serial.print(F(","));
    Serial.print(Cond); Serial.print(F(","));
    Serial.print(Light); Serial.print(F(","));
    Serial.print(Head); Serial.print(F(","));
    Serial.print(AccelX); Serial.print(F(","));
    Serial.print(AccelY); Serial.print(F(","));
    Serial.print(AccelZ); Serial.print(F(","));
    Serial.print(GyroX); Serial.print(F(","));
    Serial.print(GyroY); Serial.print(F(","));
    Serial.print(GyroZ); Serial.print(F(",\n"));
}

/** Print sensor readings to a file on the SD card */
void printFileData(){
    digitalWrite(OLED_CLK,HIGH); // make sure the OLED display isn't asserted
    digitalWrite(SD_CS,LOW);
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
    digitalWrite(SD_CS,HIGH);
    digitalWrite(OLED_CLK,LOW); // make sure the OLED display isn't asserted
    
}

/** Update content shown on the OLED display */
void printOLEDdata(){
    digitalWrite(SD_CS,HIGH);
    digitalWrite(OLED_CLK,LOW);
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
    display.print(F("lx"));
    display.setCursor(24,48);
    display.print(F("C"));
    drawArrow(Head);
    if (noSD) { // if no SD card is available, indicate so
        display.drawBitmap(0, 0, dispNoCard, 128, 64, WHITE);
        display.setCursor(2,1); display.print(F("NC"));
    } else if(app_logData || logData || sd_logData) {
        display.drawBitmap(0, 0, dispCardWrite, 128, 64, WHITE);
        display.setCursor(16,2);
        if(app_logData)
            display.print(F("A")); // logging to (A)pp
        if(logData)
            display.print(F("U")); // logging to (U)ser
        if(sd_logData)
            display.print(F("F")); // logging to (F)ile
    } else {
        display.drawBitmap(0, 0, dispCard, 128, 64, WHITE);
    }
    drawBatInd();
    display.display();
    digitalWrite(OLED_CLK,HIGH);
    digitalWrite(SD_CS,LOW);
}
