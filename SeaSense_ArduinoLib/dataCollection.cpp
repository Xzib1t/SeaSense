// Created by Georges Gauthier - glgauthier@wpi.edu

#include "Arduino.h"
#include "SeaSense.h"

void getTime(){
    DateTime now = rtc.now();
    sprintf(Timestamp,"%02d:%02d:%02d",now.hour(),now.minute(),now.second());
    return;
}