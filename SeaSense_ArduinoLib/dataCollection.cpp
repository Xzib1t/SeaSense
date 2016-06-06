// Created by Georges Gauthier - glgauthier@wpi.edu

#include "Arduino.h"
#include "SeaSense.h"

// used for counting pulses from the light sensor
unsigned long pulseTime,lastTime;

// function prototypes
void light_sensitivity(int scale, int s0, int s1);
void getLight();

void getTime(){
    DateTime now = rtc.now();
    sprintf(Timestamp,"%02d:%02d:%02d",now.hour(),now.minute(),now.second());
    return;
}

void light_sensor_init(int freqPin, int light_s0, int light_s1){
    // light sensor config
    pinMode(freqPin, INPUT);
    pinMode(light_s0, OUTPUT);
    pinMode(light_s1,OUTPUT);
    
    // set sensitivity to 1x
    // NOTE this fxn only matters for the outdated TSL230r sensor
    light_sensitivity(1,light_s0,light_s1);
    
    attachInterrupt(digitalPinToInterrupt(freqPin), getLight, RISING);
    return;
}

void getLight() {
    // disabling interrupts around this critical section slows things down too much
    // next step will be to make this a hardware counter and remove the interrupt
    Light = pulseTime - lastTime;
    
    // after getting a new reading, reset for the next run
    lastTime = pulseTime;
    // micros is only usable if duration is under 1-2mS
    pulseTime = micros();
}

void light_sensitivity(int scale, int s0, int s1){
    switch(scale){
        case 1:
            digitalWrite(s0,1);
            digitalWrite(s1,0);
            Serial1.println("Light sensor scaled to 1x");
            break;
        case 10:
            digitalWrite(s0,0);
            digitalWrite(s1,1);
            Serial1.println("Light sensor scaled to 10x");
            break;
        case 100:
            digitalWrite(s0,1);
            digitalWrite(s1,1);
            Serial1.println("Light sensor scaled to 100x");
            break;
        default:
            Serial1.println("Error: invalid light sensor scale");
            Serial1.println("Reconfigure in dataCollection.cpp");
            digitalWrite(s0,0);
            digitalWrite(s1,0);
    }
}

