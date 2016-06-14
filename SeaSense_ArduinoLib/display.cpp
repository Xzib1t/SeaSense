#include "Arduino.h"
#include "SeaSense.h"

int dx,dy;

//https://forum.pololu.com/t/1-8-tft-display-to-baby-o-with-lsm303dlh-compass/4125
void drawArrow(int degrees){
     double calcRad = degrees * M_PI / 180;
    int x_t,x_to,y_to;
    int x = 63;
    int y = 42;
    
    // Each quadrant around the circle has + or - values in relation to the center
    // point (x, y).  The sin() function returns +/- values for x_to, but y_to has
  // to be manipulated.
  if (degrees > -1 && degrees < 91)
  {
    x_to = sin(calcRad)*16;
    // The x_t value was inserted for trouble shooting.  May be removed later.
    // Shouldn't matter because (neg * neg) = positive number.
    x_t = -1 * x_to;
    y_to = -1 * sqrt((16*16)-(x_t * x_t));
  }
  if (degrees > 90 && degrees < 181)
  {
    x_to = sin(calcRad)*16;
    y_to = (sqrt((16*16)-(x_to * x_to)));
  }
  if (degrees > 180 && degrees < 271)
  {
    x_to = sin(calcRad)*16;
    y_to = (sqrt((16*16)-(x_to * x_to)));
  }
  if (degrees > 270 && degrees < 361)
  {
    x_to = sin(calcRad)*16;
    x_t = -1 * x_to;
    y_to = -1*(sqrt((16*16)-(x_t * x_t)));
  }
    
  display.drawLine(x+x_to, y+y_to, 63, 42, WHITE);
}