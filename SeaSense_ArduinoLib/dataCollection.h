/** @file dataCollection.h
*  @breif Header containing prototypes for all functions related to collecting sensor data.
*  @author Georges Gauthier, glgauthier@wpi.edu
*  @date May-July 2016
*/ 

#ifndef dataCollection_h
#define dataCollection_h
    
    /**Put the current RTC timestamp into global var Timestamp. @see Timestamp*/
    void getTime();
    /**Put the current Light Sensor reading from the hardware pulse counter in global var Light. 
    *@see Light
    *@see SeaSense::Initialize()
    *@see ISR(TIMER5_OVF_vect)
    */
    void getLight();
    /** Average an ADC reading, perform any necessary conversions, store the converted value in 
    * the sensor variable corresponding to adc_channel, and reset the ADC registers for a new
    * set of conversions on a different channel. Currently this function contains conversions 
    * for temperature, pressure, conductivity, and battery voltage. @see resetADC()
    */
    void getADCreadings();
    /**Gets the current magnetic heading and converts it to a compass angle between 0 and 360
    * degrees, with the declination angle accounted for. @see DECLINATION_ANGLE
    */
    void getMag();
    /**Gets the current accelerometer readings and stores them in globally accessible variables*/
    void getAccel();
    /**Gets the current gyroscope readings and stored them in globally accessible variables*/
    void getGyro();
    /**Gets the current internal temperature and pressure and stores them in global variables.
    *Note that this function will return on entry if _TP is #undef in globals.h*/
    void getInternals();
    #ifdef TSL230R 
        void light_sensor_init(int light_s0, int light_s1);
    #endif /* TSL230R */
    #ifdef GY80
        int setupL3G4200D(int scale);
        void writeRegister(int deviceAddress, byte address, byte val);
        int readRegister(int deviceAddress, byte address);
    #endif /* GY80 */

#endif