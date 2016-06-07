// Created by Georges Gauthier - glgauthier@wpi.edu

#include "Arduino.h"
#include "SeaSense.h"

// used for counting pulses from the light sensor
int carryOut = 0;

// function prototypes
void light_sensitivity(int scale, int s0, int s1);
void resetADC();

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

// cycle through the adc buffer, come up with an average val for each sensor, and
// process said val to its final form
void getADCreadings(){
    int var = 0;
    int i = 0;
    int avg = 0;
    // cycle through the ADC buffer and get an average for each channel
    for (var = 0; var < NUM_ADC_CHANNELS; var++){
        for(i = 0; i < ADC_BUFFER_SIZE; i++){
            avg+=adcBuf[var][i];
        }
        avg = avg/ADC_BUFFER_SIZE;
        // set global vars for each channel's reading based on the avg
        switch(var){
            case 0: Temp = (avg*500.0)/1024;
                break;
            case 1: Depth = avg;
                break;
            case 2: Cond = avg;
                break;
        }
    }
    resetADC(); // allow for a new set of readings to occur  
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

// resets the ADC for new conversions starting with channel 10 and buffer index 0
void resetADC(){
    // allow for 2D adc buffer to be written to again
    adc_channel = 10; 
    adc_pos = 0;
    // clear old ADC MUX settings
    ADMUX &= ~((1<<MUX4)|(1<<MUX3)|(1<<MUX2)|(1<<MUX1)|(1<<MUX0));  
    ADCSRB &= ~(1<<MUX5);
    // MUX[5:0] = 100010 = ADC channel 10
    ADMUX |= (1 << MUX1);
    ADCSRB |= (1 << MUX5);
    ADCSRA |= (1 << ADSC); // Start A2D Conversions
}