// Created by Georges Gauthier - glgauthier@wpi.edu
// Last updated June 2016

#ifndef HEADER_GLOBALS
 #define HEADER_GLOBALS
        #include "RTClib.h"
        extern boolean RTC_AUTOSET;
        extern RTC_DS1307 rtc;

        #include "SD.h"
        extern File SDfile;

        #include "Adafruit_Sensor.h"
        #include "Adafruit_HMC5883_U.h"
        extern Adafruit_HMC5883_Unified mag;

        #define MAX_INPUT_SIZE 80
        #define MAX_CLI_ARGV 10
        #define SD_CS 4
        #define NUM_ADC_CHANNELS 3
        #define ADC_BUFFER_SIZE 10
        /* http://www.magnetic-declination.com/ */
        #define DECLINATION_ANGLE 0.2516f 

        /*Data values for writing to the SD card/serial log*/
        extern boolean logData;
        extern boolean sd_logData;
        extern boolean app_logData;
        extern char Timestamp[9];
        extern double Temp;
        extern unsigned int Depth;
        extern int Cond;
        extern unsigned long Light;
        extern int carryOut;
        extern int Head;
        extern int AccelX,AccelY,AccelZ;
        extern int GyroX,GyroY,GyroZ;
        extern int adcBuf[ADC_BUFFER_SIZE];
        extern byte adc_channel, adc_pos;
        
#endif