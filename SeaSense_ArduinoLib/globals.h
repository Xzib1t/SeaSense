#ifndef HEADER_GLOBALS
 #define HEADER_GLOBALS
        #include "RTClib.h"
        extern boolean RTC_AUTOSET;
        extern RTC_DS1307 rtc;
        
        /*Data values for writing to the SD card/serial log*/
        extern boolean logData;
        extern boolean sd_logData;
        extern char Timestamp[9];
        extern double Temp;
        extern unsigned int Depth;
        extern int Cond;
        extern int Light;
        extern int Head;
        extern int AccelX,AccelY,AccelZ;
        extern int GyroX,GyroY,GyroZ;
        
        #define MAX_INPUT_SIZE 100
        #define MAX_CLI_ARGV 20
        #define SD_CS 4
#endif