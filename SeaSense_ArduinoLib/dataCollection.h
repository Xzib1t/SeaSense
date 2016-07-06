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
    void getADCreadings();
    void getMag();
    void getAccel();
    void getGyro();
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