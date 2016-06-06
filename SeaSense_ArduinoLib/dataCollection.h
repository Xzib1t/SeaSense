#ifndef dataCollection_h
#define dataCollection_h

#include "Arduino.h"
#include "SeaSense.h"

void getTime();
void light_sensor_init(int freqPin, int light_s0, int light_s1);

#endif