/*
  Example Bluetooth Serial Passthrough Sketch
 by: Jim Lindblom
 SparkFun Electronics
 date: February 26, 2013
 license: Public domain

 This example sketch converts an RN-42 bluetooth module to
 communicate at 57600 bps (from 115200), and passes any serial
 data between Serial Monitor and bluetooth module.
 */
 
 /*
 Modified by Georges Gauthier on 7/05/2016 to work with the SeaSense suite
 */

void setup()
{
  // turn on the bluetooth module
  pinMode(38,OUTPUT);
  digitalWrite(38,HIGH);
  
  Serial.begin(115200);  // The Bluetooth Mate defaults to 115200bps
  Serial.print("$");  // Print three times individually
  Serial.print("$");
  Serial.print("$");  // Enter command mode
  delay(100);  // Short delay, wait for the Mate to send back CMD
  Serial.println("U,57600,N");  // Temporarily Change the baudrate to 57600, no parity
  // 115200 can be too fast at times for NewSoftSerial to relay the data reliably
  Serial.end();
  Serial.begin(57600);  // Start bluetooth serial at 9600
}

void loop()
{
  if(Serial.available())  // If the bluetooth sent any characters
  {
    // Send any characters the bluetooth prints to the serial monitor
    Serial.print((char)Serial.read());  
  }
}