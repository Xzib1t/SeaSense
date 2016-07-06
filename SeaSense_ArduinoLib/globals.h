/** @file globals.h
*  @breif Contains global variable storage and configuration.
*   
* Globals.h contains globally accessible variables that are used 
* in multiple source files. These include, but are not limited to, 
* configuration for various sensors and storage for processed sensor 
* readings. Note that support for the legacy light sensor can be 
* toggled in this file, as well as internal temperature and pressure 
* readings, and the choice of a GY80 or Adafruit 9 or 10 DOF IMU.
* @author Georges Gauthier, glgauthier@wpi.edu
* @date May-July 2016
*/ 

#ifndef HEADER_GLOBALS
 #define HEADER_GLOBALS
    #include "RTClib.h"
    /** Set to true to set RTC time to compile time.
    * Set to false to allow for manual configuration of the RTC
    * using the rtc_set command.
    */
    extern boolean RTC_AUTOSET;
    extern RTC_DS1307 rtc;

    #include "SD.h"
    /** SD card ChipSelect*/
    #define SD_CS 4
    /** Current file being logged to*/
    extern File SDfile;

    /* change #undef to #define to switch from adafruit 9 or 10dof to GY80 IMU */
    #undef GY80 

    /** Internal temp/pressure (optional, change to #undef to disable)*/
    #define _TP 

    #include "Adafruit_Sensor.h"    
    #ifdef GY80
        /* GY80 IMU */
        #include "Adafruit_HMC5883_U.h"
        extern Adafruit_HMC5883_Unified mag;
        #include "Adafruit_ADXL345_U.h"
        extern Adafruit_ADXL345_Unified accel;
        #define L3G4200D_ADDRESS 0x69
        #define CTRL_REG1 0x20
        #define CTRL_REG2 0x21
        #define CTRL_REG3 0x22
        #define CTRL_REG4 0x23
        #define CTRL_REG5 0x24
    #else /* GY80 */
        /* Adafruit 9DOF or 10DOF IMU */
        #include "Adafruit_LSM303_U.h"
        #include "Adafruit_L3GD20_U.h"
        extern Adafruit_LSM303_Accel_Unified accel;
        extern Adafruit_LSM303_Mag_Unified mag;
        extern Adafruit_L3GD20_Unified gyro;
    #endif /* GY80 */
    #ifdef _TP
        /* Internal Temp and Pressure */
        #include "Adafruit_BMP085_U.h"
        extern Adafruit_BMP085_Unified bmp;
        /**Current reading from internal temperature sensor*/
        extern float TempInt;
        /**Current reading from internal pressure sensor*/
        extern float PresInt;
    #endif /* _TP */
 
    /** http://www.magnetic-declination.com/ */
    #define DECLINATION_ANGLE 0.2516f 

    /* Legacy light sensor support (change #undef to #define to configure for old sensor) */
    #undef TSL230R
    #ifdef TSL230R 
        /* Digital pins for S0, S1 */
        #define _S0 39
        #define _S1 40
    #endif /* TSL230R */
    /* OLED Display */
    #include "Adafruit_GFX.h"
    #include "Adafruit_SSD1306.h"
    #if (SSD1306_LCDHEIGHT != 64)
        #error("Height incorrect, please fix Adafruit_SSD1306.h!");
    #endif
    extern Adafruit_SSD1306 display;

    /** Pin connected to the 5V pin of the BlueSmirf. Using this
    * configuration allows for the microcontroller to manually
    * turn the module on/off to save power.
    */
    #define BT_PWR 38
    /** Baud rate of the BlueSmirf */
    #define BT_BAUDRATE 115200

    /** CLI input buffer character limit */
    #define MAX_INPUT_SIZE 80 
    /** CLI input argument limit (space separated) */
    #define MAX_CLI_ARGV 10 

    /* ADC buffer & channel controls */
    /** Number of ADC channels to be read from by the sequential ISR setup. 
    * Note: channels 10:(NUM_ADC_CHANNELS-1) will be read into the ADC buffer by an ISR
    */
    #define NUM_ADC_CHANNELS 4
    /**Number of individual ADC samples to be taken from each channel*/
    #define ADC_BUFFER_SIZE 100 
    /**ADC sample buffer - fills from one ADC channel, is averaged, and then filled by the next ADC channel, etc, etc*/
    extern int adcBuf[ADC_BUFFER_SIZE];
    extern byte adc_channel, adc_pos; // current channel being read from and position in adcBuf
    /**Indicator that adcBuf is full and ready to be read from
    * @see ISR(ADC_vect)
    * @see getADCreadings()
    */
    extern boolean adc_ready;

    /* OLED Display */
    #define OLED_MOSI 6    //D1 on wish board (Data on adafruit 128x64)
    #define OLED_CLK 5     //D0 on wish board (Clk on adafruit 128x64)
    #define OLED_DC 49     //DC on wish board (SA0/DC on adafruit 128x64)
    #define OLED_CS 7      //CS on wish board (CS on adafruit 128x64)
    #define OLED_RESET 48  //RES on wish board (Rst on adafruit 128x64)
    
    /** Indicator LED for data logging */
    #define LEDpin 3
    
    /**Low power mode wake pin. An external interrupt on this pin
    * is configured to wake the atmega2560 from sleep mode. This
    * allows for users to wake the arduino using the hall effect sensor
    */
    #define LPM_WAKE 2
    /**Minimum depth required to enable low power logging mode
    * (i.e. display and bluetooth are turned off).
    */
    #define LOW_PWR_DEPTH -5
    /**Current status of low power logging (true=enabled)*/
    extern boolean lowPowerLogging;

    /* Booleans that control SD card writes and logging */
    /**Current status of SD card slot (true = no SD card found)*/
    extern boolean noSD;
    /**Current status of verbose data logging to the Bluetooth port*/
    extern boolean logData;
    /**Current status of data logging to the SD card*/
    extern boolean sd_logData;
    /**Current status of data logging to the android app*/
    extern boolean app_logData;
    
    /* Sensor readings */
    /**Character array containing the current time, updated once per second*/
    extern char Timestamp[9];
    /**Current temperature in deg C. @see getADCreadings()*/
    extern double Temp;
    /**Current depth in cm. @see getADCreadings()*/
    extern unsigned int Depth;
    /**Current conductivity. @see getADCreadings()*/
    extern int Cond;
    /**Current light sensor reading in lux. @see getLight() @see SeaSense::Initialize() @see ISR(TIMER5_OVF_vect)*/
    extern unsigned long Light;
    extern int carryOut;
    /**Current heading in degrees. @see getMag()*/
    extern int Head;
    /**Current accelerometer reading. @see getAccel()*/
    extern float AccelX,AccelY,AccelZ;
    /**Current gyroscope reading. @see getGyro()*/
    extern float GyroX,GyroY,GyroZ;
    /**Current battery voltage as read through the ADC.
    * @see getADCreadings()
    * @see drawBatInd()
    */
    extern int vBat;
        
#endif