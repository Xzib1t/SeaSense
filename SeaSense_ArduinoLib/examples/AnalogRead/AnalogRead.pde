
/* Created by Georges Gauthier - glgauthier@wpi.edu
*-> Ported from the original seasense PIC code
*-> Recorded some data files? Try using our online file viewer!
*   https://xzib1t.github.io/SeaSense/
*/

/* NOTE - This library requires the installation of the following adafruit libraries:
* RTClib (https://github.com/adafruit/RTClib)
* Adafruit_Sensor (https://github.com/adafruit/Adafruit_Sensor)
* Adafruit GFX Library (https://github.com/adafruit/Adafruit-GFX-Library)
* Adafruit SSD1306 Library (https://github.com/adafruit/Adafruit_SSD1306)
* ~~~~~~~~~~ GY80 IMU Version: ~~~~~~~~~~~~~~~
* Adafruit HMC5883L Magnetometer Driver (https://github.com/adafruit/Adafruit_HMC5883_Unified)
* Adafruit ADXL345 Accelerometer Driver (https://github.com/adafruit/Adafruit_ADXL345)
* ~~~~~~~ Adafruit 9 DOF IMU Version: ~~~~~~~~
* Adafruit LSM303DLHC Driver (Accel + Mag) (https://github.com/adafruit/Adafruit_LSM303DLHC)
* Adafruit L3GD20 Gyroscope Driver (https://github.com/adafruit/Adafruit_L3GD20_U)
*/

/* Create a new instanse of the SeaSense library "seasense"
*  Pins used by the library include the following:
*   - Pins 0 and 1: Serial1 Tx and Rx to bluetooth module
*   - Pin 2: Hall Effect Sensor/LPM wake interrupt
*   - Pin 3: Data logging indicator LED
*   - Pin 4 used for SD card CS
*   - Pins 5, 6, and 7 used for OLED clk, data, cs
*   - Pin 38: Bluetooth module power
*   - Pin 47: Timer5 hardware edge counter (light sensor)
*   - Pins 48, 49 for OLED rst, dc
*   - Pins 50, 51, 52, used for the SPI interface
*   - ADC10, ADC11, ADC12, ADC13 for the temperature, pressure, conductivity, and battery voltage sensors
*   ~~ IN ORDER TO USE OTHER ADC CHANNELS YOU MUST MODIFY THE LIBRARY CODE ~~
*   this is due to the internal configuration of ADC interrupts
*
*/ 

#include <SeaSense.h>

SeaSense seasense;

void setup(){
    // allow for users to modify the RTC time
    RTC_AUTOSET = false;
    
    // Initialize the sensor suite
    seasense.Initialize();
    
}

void loop(){
    // Scan the bluetooth port for new data packets
    seasense.BluetoothClient();
    
    // Process sensor data for logging 
    seasense.CollectData();
    
    // Get readings from your analog pins
    // Be careful! ReadAnalogPin disables ISRs, so the more frequently you call it, the more other processes will slow down
    Serial.print(F("Pin A5 Value: "));
    Serial.println(seasense.ReadAnalogPin(5));
    Serial.print(F("Pin A15 Value: "));
    Serial.println(seasense.ReadAnalogPin(15));
    
}