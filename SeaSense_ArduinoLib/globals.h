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
        
        #include "Adafruit_ADXL345_U.h"
        extern Adafruit_ADXL345_Unified accel;
        
        #include "Adafruit_GFX.h"
        #include "Adafruit_SSD1306.h"
        #if (SSD1306_LCDHEIGHT != 64)
        #error("Height incorrect, please fix Adafruit_SSD1306.h!");
        #endif
        extern Adafruit_SSD1306 display;

        #define MAX_INPUT_SIZE 80
        #define MAX_CLI_ARGV 10
        #define SD_CS 4
        #define NUM_ADC_CHANNELS 3
        #define ADC_BUFFER_SIZE 10
        /* http://www.magnetic-declination.com/ */
        #define DECLINATION_ANGLE 0.2516f 
        #define CTRL_REG1 0x20
        #define CTRL_REG2 0x21
        #define CTRL_REG3 0x22
        #define CTRL_REG4 0x23
        #define CTRL_REG5 0x24
        #define L3G4200D_ADDRESS 0x69 
        /* OLED Display */
        #define OLED_MOSI  6    //D1
        #define OLED_CLK   5    //D0
        #define OLED_DC    11   //DC
        #define OLED_CS    7    //CS
        #define OLED_RESET 13   //RES

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