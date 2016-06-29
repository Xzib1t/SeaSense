// Created by Georges Gauthier - glgauthier@wpi.edu
// Last updated June 2016

// this source files contains low-priority code for updating global variables with new sensor
// readings, as well as code for resetting the ADC for a new conversion sequence

#include "Arduino.h"
#include "SeaSense.h"

// used for counting pulses from the light sensor
int carryOut = 0;

// function prototypes
void light_sensitivity(int scale, int s0, int s1);
void resetADC();

// getTime - update the current timestamp stored in global var Timestamp
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

// getLight - read a new value from the hardware counter to global var Light
void getLight() {
    Light = (65535 * carryOut + TCNT5)/10;
    TCNT5 = 0;
    carryOut = 0;
}

// cycle through the adc buffer, come up with an average val for each sensor, and
// process said val to its final form
void getADCreadings(){
    if(!adc_ready) return;
    
    cli(); // globally disable interrupts while the ADC registers are being modified
    int i = 0;
    unsigned long long avg = 0;
    
    // cycle through the ADC buffer and get an average for each channel
    for(i = 0; i < ADC_BUFFER_SIZE; i++){
        avg+=adcBuf[i];
        adcBuf[i] = 0;
    }
    avg = avg/ADC_BUFFER_SIZE;
    
    // set global vars for each channel's reading based on the avg
    switch(adc_channel){
        case 10: Temp = (avg*500.0)/1024;
            break;
        case 11: //VOUT = Vs* (0.004 x P-0.04) ± Error
            // => (avg*5/1024)*(1/(Vs*.004))+0.04 = pressure in kPa = (avg*125)/512 + 0.04
            long pressure;
            pressure = ((avg*125)>>9)+0.04; 
            pressure = pressure*10; // convert from kpa to mbar
            Depth = abs(pressure-1013)*1.019; //convert mbar to cm
            break;
        case 12: Cond = avg;
            break;
        case 13: vBat = avg; // scaling handled in display.cpp
            break;
    }
    
    resetADC(); // configure the ADC for a new set of readings
    adc_ready = false;
    sei(); // re-enable interrupts
    ADCSRA |= (1 << ADSC); // restart A2D Conversions
}

void getMag(){
  /* Get a new sensor event */ 
    float heading;
    
    sensors_event_t event; 
    mag.getEvent(&event);
    
    heading = atan2(event.magnetic.y, event.magnetic.x);
    heading += DECLINATION_ANGLE;
    // Correct for when signs are reversed.
      if(heading < 0)
        heading += 2*PI;

      // Check for wrap due to addition of declination.
      if(heading > 2*PI)
        heading -= 2*PI;
    
     // Convert radians to degrees for readability.
     Head = heading * 180/M_PI; 

    return;
}

// getAccel - get a new reading from the accelerometer
void getAccel(){
     /* Get a new sensor event */ 
    sensors_event_t event; 
    accel.getEvent(&event);
    AccelX = event.acceleration.x;
    AccelY = event.acceleration.y;
    AccelZ = event.acceleration.z;
    
}

// getGyro - get a new reading from the gyroscope
void getGyro(){
  /*~~~~~~~~~~~~~~~~~~~~ GY80 ~~~~~~~~~~~~~~~~~~~~~~*/
//  byte xMSB = readRegister(L3G4200D_ADDRESS, 0x29);
//  byte xLSB = readRegister(L3G4200D_ADDRESS, 0x28);
//  GyroX = ((xMSB << 8) | xLSB);
//
//  byte yMSB = readRegister(L3G4200D_ADDRESS, 0x2B);
//  byte yLSB = readRegister(L3G4200D_ADDRESS, 0x2A);
//  GyroY = ((yMSB << 8) | yLSB);
//
//  byte zMSB = readRegister(L3G4200D_ADDRESS, 0x2D);
//  byte zLSB = readRegister(L3G4200D_ADDRESS, 0x2C);
//  GyroZ = ((zMSB << 8) | zLSB);
  
  /*~~~~~~~~~~~~~~~~ Adafruit 9DOF ~~~~~~~~~~~~~~~~*/
    sensors_event_t event;
    gyro.getEvent(&event);
    GyroX = event.gyro.x;
    GyroY = event.gyro.y;
    GyroZ = event.gyro.z;
}

// configure the Gyroscope (only used for GY80 IMU)
int setupL3G4200D(int scale){
  //From  Jim Lindblom of Sparkfun's code

  // Enable x, y, z and turn off power down:
  writeRegister(L3G4200D_ADDRESS, CTRL_REG1, 0b00001111);

  // If you'd like to adjust/use the HPF, you can edit the line below to configure CTRL_REG2:
  writeRegister(L3G4200D_ADDRESS, CTRL_REG2, 0b00000000);

  // Configure CTRL_REG3 to generate data ready interrupt on INT2
  // No interrupts used on INT1, if you'd like to configure INT1
  // or INT2 otherwise, consult the datasheet:
  writeRegister(L3G4200D_ADDRESS, CTRL_REG3, 0b00001000);

  // CTRL_REG4 controls the full-scale range, among other things:

  if(scale == 250){
    writeRegister(L3G4200D_ADDRESS, CTRL_REG4, 0b00000000);
  }else if(scale == 500){
    writeRegister(L3G4200D_ADDRESS, CTRL_REG4, 0b00010000);
  }else{
    writeRegister(L3G4200D_ADDRESS, CTRL_REG4, 0b00110000);
  }

  // CTRL_REG5 controls high-pass filtering of outputs, use it
  // if you'd like:
  writeRegister(L3G4200D_ADDRESS, CTRL_REG5, 0b00000000);
}
// writeRegister - used in gyroscope config (only used for GY80 IMU)
void writeRegister(int deviceAddress, byte address, byte val) {
    Wire.beginTransmission(deviceAddress); // start transmission to device 
    Wire.write(address);       // send register address
    Wire.write(val);         // send value to write
    Wire.endTransmission();     // end transmission
}

// readRegister - used to read from the gyroscope module (only used for GY80 IMU)
int readRegister(int deviceAddress, byte address){

    int v;
    Wire.beginTransmission(deviceAddress);
    Wire.write(address); // register to read
    Wire.endTransmission();

    Wire.requestFrom(deviceAddress, 1); // read a byte

    while(!Wire.available()) {
        // waiting
    }

    v = Wire.read();
    return v;
}

// used for scaling light sensor readings
void light_sensitivity(int scale, int s0, int s1){
    switch(scale){
        case 1:
            digitalWrite(s0,1);
            digitalWrite(s1,0);
            Serial1.println(F("\tLight sensor scaled to 1x"));
            break;
        case 10:
            digitalWrite(s0,0);
            digitalWrite(s1,1);
            Serial1.println(F("\tLight sensor scaled to 10x"));
            break;
        case 100:
            digitalWrite(s0,1);
            digitalWrite(s1,1);
            Serial1.println(F("\tLight sensor scaled to 100x"));
            break;
        default:
            Serial1.println(F("\tError: invalid light sensor scale"));
            Serial1.println(F("\tReconfigure in dataCollection.cpp"));
            digitalWrite(s0,0);
            digitalWrite(s1,0);
    }
}

// resets the ADC for new conversions starting with channel 10 and buffer index 0
void resetADC(){
    // cycle which ADC is being read from
    if(adc_channel<13) {
        adc_channel++;
    }
    else adc_channel = 10;
    
    // allow for a new conversion
    adc_pos = 0;
         
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
        case 13: 
            // MUX[5:0] = 100101 = ADC channel 13
            ADMUX |= ((1 << MUX2)|(1<<MUX0));
            ADCSRB |= (1 << MUX5);
            break;
        default:
            Serial1.println(F("Error: Incorrect ADC "));
    }
}