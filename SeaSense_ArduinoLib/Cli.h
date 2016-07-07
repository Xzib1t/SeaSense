/** @file Cli.h
*  @breif The cli.h file provides hooks for parsing command line inputs
*  @author Georges Gauthier, glgauthier@wpi.edu
*  @date May-July 2016
*/ 

#ifndef Cli_h
#define Cli_h

#include <string.h>

/**Parses an incoming command string into argv and argc, searches CLI_CMD_LIST for a 
* corresponding command, and excecutes said command.
* @param command Pointer to a character array
* @param size Number of characters in the array being pointed to
* @see SeaSense::BluetoothClient()
* @see CLI_CORE_CMD_LIST
*/
void processCMD(char *command, int size);

#endif