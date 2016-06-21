/* Created by Georges Gauthier - glgauthier@wpi.edu
*-> Ported from the original seasense PIC code
*-> Recorded some data files? Try using our online file viewer!
*   https://xzib1t.github.io/SeaSense/
*/

/* NOTE - This library requires the installation of the following adafruit libraries:
* RTClib (https://github.com/adafruit/RTClib)
* Adafruit_Sensor (https://github.com/adafruit/Adafruit_Sensor)
* Adafruit HMC5883L Magnetometer Driver (https://github.com/adafruit/Adafruit_HMC5883_Unified)
* Adafruit ADXL345 Accelerometer Driver (https://github.com/adafruit/Adafruit_ADXL345)
* Adafruit GFX Library (https://github.com/adafruit/Adafruit-GFX-Library)
* Adafruit SSD1306 Library (https://github.com/adafruit/Adafruit_SSD1306)
*/

/* Create a new instanse of the SeaSense library "seasense"
*  Pins used by the library include the following:
*   - Pin 4 used for SD card CS
*   - Pins 5, 6, and 7 used for OLED
*   - Pins 10, 11, 12, and 13 used for the SPI interface
*   - Pins 18 and 19: Serial1 Tx and Rx to bluetooth module
*   - Pin 22: Data logging indicator LED
*   - Pin 23: Hall Effect Sensor
*   - Pin 47: Timer5 hardware edge counter (light sensor)
*   - ADC10, ADC11, ADC12 for the temperature, pressure, and conductivity sensors
*   ~~ IN ORDER TO USE OTHER ADC CHANNELS YOU MUST MODIFY THE LIBRARY CODE ~~
*   this is due to the internal configuration of ADC interrupts
*
*  Input arguments for "SeaSense NAME(ARG1,ARG2)" :
*   - ARG1 and ARG2: S0 and S1 pins for use with older TSL230R sensor
*/ 

#include <SeaSense.h>

SeaSense seasense(48,49);

void setup(){
    // allow for users to modify the RTC time
    RTC_AUTOSET = false;
    
    // Initialize USB serial coms (FOR DEBUG ONLY)
    Serial.begin(9600);
    
    // Initialize the sensor suite
    seasense.Initialize();
    
}

void loop(){
    // Scan the bluetooth port for new data packets
    seasense.BluetoothClient();
    
    // Process sensor data for logging 
    seasense.CollectData();
    
    // All other processes are handled internally through interrupts!
}