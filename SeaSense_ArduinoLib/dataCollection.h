// Created by Georges Gauthier - glgauthier@wpi.edu
// Last updated June 2016

#ifndef dataCollection_h
#define dataCollection_h

void getTime();
void light_sensor_init(int light_s0, int light_s1);
void getLight();
void getADCreadings();
void getMag();
void getAccel();
void getGyro();
int setupL3G4200D(int scale);
void writeRegister(int deviceAddress, byte address, byte val);
int readRegister(int deviceAddress, byte address);

#endif