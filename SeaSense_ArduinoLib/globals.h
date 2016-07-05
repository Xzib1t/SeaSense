// Created by Georges Gauthier - glgauthier@wpi.edu
// Last updated June 2016

#ifndef HEADER_GLOBALS
 #define HEADER_GLOBALS
    #include "RTClib.h"
    extern boolean RTC_AUTOSET;
    extern RTC_DS1307 rtc;

    #include "SD.h"
    #define SD_CS 4
    extern File SDfile;

    /* IMU */
    #undef GY80 /* change #undef to #define to switch from adafruit 9 or 10dof to GY80 IMU */

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
    #define DECLINATION_ANGLE 0.2516f /* http://www.magnetic-declination.com/ */

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

    /* Bluetooth power pin, baud rate*/
    #define BT_PWR 38
    #define BT_BAUDRATE 115200

    /* CLI input buffer size */
    #define MAX_INPUT_SIZE 80 /* Character limit */
    #define MAX_CLI_ARGV 10 /* Number of CLI arguments (space separated) */

    /* ADC buffer & channel controls */
    #define NUM_ADC_CHANNELS 4 /* channels 10:(NUM_ADC_CHANNELS-1) will be read into the ADC buffer by an ISR*/
    #define ADC_BUFFER_SIZE 100 
    extern int adcBuf[ADC_BUFFER_SIZE];
    extern byte adc_channel, adc_pos;
   
    /* OLED Display */
    #define OLED_MOSI 6    //D1 on wish board (Data on adafruit 128x64)
    #define OLED_CLK 5     //D0 on wish board (Clk on adafruit 128x64)
    #define OLED_DC 49     //DC on wish board (SA0/DC on adafruit 128x64)
    #define OLED_CS 7      //CS on wish board (CS on adafruit 128x64)
    #define OLED_RESET 48  //RES on wish board (Rst on adafruit 128x64)
    
    /* LED logging status indicator */
    #define LEDpin 3
    
    /* Low power mode wake pin (external interrupt)*/
    #define LPM_WAKE 2
    /*Controls for turning off the display/bluetooth based on depth*/
    #define LOW_PWR_DEPTH -5
    extern boolean lowPowerLogging;

    /* Booleans that control SD card writes and logging */
    extern boolean noSD;
    extern boolean logData;
    extern boolean sd_logData;
    extern boolean app_logData;
    extern boolean adc_ready;
    
    /* Sensor readings */
    extern char Timestamp[9];
    extern double Temp;
    extern unsigned int Depth;
    extern int Cond;
    extern unsigned long Light;
    extern int carryOut;
    extern int Head;
    extern float AccelX,AccelY,AccelZ;
    extern float GyroX,GyroY,GyroZ;
    extern int vBat;
        
#endif