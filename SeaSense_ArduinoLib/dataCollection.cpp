// Created by Georges Gauthier - glgauthier@wpi.edu

#include "Arduino.h"
#include "SeaSense.h"

// used for counting pulses from the light sensor
int carryOut = 0;

// function prototypes
void light_sensitivity(int scale, int s0, int s1);

void getTime(){
    DateTime now = rtc.now();
    sprintf(Timestamp,"%02d:%02d:%02d",now.hour(),now.minute(),now.second());
    return;
}

// initialize light sensor scaling rate (only used for TSL230r sensor)
void light_sensor_init(int freqPin, int light_s0, int light_s1){
    // light sensor config
    pinMode(light_s0,OUTPUT);
    pinMode(light_s1,OUTPUT);
    
    // set sensitivity to 1x
    // NOTE this fxn only matters for the outdated TSL230r sensor
    light_sensitivity(1,light_s0,light_s1);
    return;
}

// read a new value from the hardware counter to global var Light
void getLight() {
    Light = 65535 * carryOut + TCNT5;
    TCNT5 = 0;
    carryOut = 0;
}

void getTemp(int tempPin){
    // LM35CAH is 10mV / deg C
    Temp = (analogRead(tempPin)*500.0)/1024;//(5.0*analogRead(tempPin)*100.0)/1024;
}
// used for scaling light sensor readings
void light_sensitivity(int scale, int s0, int s1){
    switch(scale){
        case 1:
            digitalWrite(s0,1);
            digitalWrite(s1,0);
            Serial1.println("\tLight sensor scaled to 1x");
            break;
        case 10:
            digitalWrite(s0,0);
            digitalWrite(s1,1);
            Serial1.println("\tLight sensor scaled to 10x");
            break;
        case 100:
            digitalWrite(s0,1);
            digitalWrite(s1,1);
            Serial1.println("\tLight sensor scaled to 100x");
            break;
        default:
            Serial1.println("\tError: invalid light sensor scale");
            Serial1.println("\tReconfigure in dataCollection.cpp");
            digitalWrite(s0,0);
            digitalWrite(s1,0);
    }
}

