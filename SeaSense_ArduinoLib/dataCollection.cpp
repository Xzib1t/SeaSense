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
void light_sensor_init(int light_s0, int light_s1){
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
    int i = 0;
    int avg = 0;
    
    // cycle through the ADC buffer and get an average for each channel
    for(i = 0; i < ADC_BUFFER_SIZE; i++){
        avg+=adcBuf[i];
    }
    avg = avg/ADC_BUFFER_SIZE;
    
    // set global vars for each channel's reading based on the avg
    switch(adc_channel){
        case 10: Temp = (avg*500.0)/1024;
            break;
        case 11: Depth = avg;
            break;
        case 12: Cond = avg;
            break;
    }
    
    cli(); // globally disable interrupts while the ADC registers are being modified
    resetADC(); // configure the ADC for a new set of readings
    sei(); // re-enable interrupts
    ADCSRA |= (1 << ADSC); // restart A2D Conversions
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
    // cycle which ADC is being read from
    if(adc_channel<12) {
        adc_channel++;
    }
    else adc_channel = 10;
    
    // allow for a new conversion
    adc_pos = 0;

    //ADMUX |= ((1 << REFS0)|(1 << ADLAR)); // make sure data is same format 
              
    // clear old ADC MUX settings
    ADMUX &= ~((1<<MUX4)|(1<<MUX3)|(1<<MUX2)|(1<<MUX1)|(1<<MUX0));  
    ADCSRB &= ~(1<<MUX5);

    // set MUX[5:0] to correspond to the next register
    switch(adc_channel){
        case 10:
            // MUX[5:0] = 100010 = ADC channel 10
            ADMUX |= (1 << MUX1);
            ADCSRB |= (1 << MUX5);
            break;
        case 11:
            // MUX[5:0] = 100011 = ADC channel 11
            ADMUX |= ((1 << MUX1)|(1 << MUX0));
            ADCSRB |= (1 << MUX5);
            break;
        case 12: 
            // MUX[5:0] = 100100 = ADC channel 12
            ADMUX |= (1 << MUX2);
            ADCSRB |= (1 << MUX5);
            break;
        default:
            Serial1.println("Error: Incorrect ADC ");
    }
}