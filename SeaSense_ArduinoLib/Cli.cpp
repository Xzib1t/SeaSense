/** @file Cli.cpp
*  @breif Contains all functions for handling command line input
*  @author Georges Gauthier, glgauthier@wpi.edu
*  @author Eugene Chabot (original CLI code for PIC24FJ64GB004)
*  @author John DiCecco (original CLI code for PIC24FJ64GB004)
*  @date May-July 2016
*/ 

#include "Arduino.h"
#include "Cli.h"
#include "SeaSense.h"
#include <string.h>

// input text and number of args from a new command line entry
static int cli_argc;
static char* cli_argv[MAX_CLI_ARGV]; 

// number of CSV files in the directory
int _numCSV = 0;

/* Create a type for each CLI command. */
/** @struct CLI_CMD
*   @breif Format for CLI commands added to CLI_CORE_CMD_LIST
*   @param name Function name, as entered in the command line interface
*   @param description Function description (shown when calling the help command)
*   @param cli_function Local function in Cli.cpp corresponding to the given command name and description
*/
struct CLI_CMD
{
  char *name;
  char *description;
  void (*cli_function)(int argc, char *argv[]);
};
typedef struct CLI_CMD cli_cmd_t;


/**Generate a list of CLI_CMD structs for all commands visible from the help menu.
* Note that passing "text" for each command name and description is a depreciated conversion
* from a string constant to a char* and will result in compiler warnings. 
* However, this method does save a lot of space and is more readable. 
* In the future this may need to be fixed; proper syntax would be to use strcpy() 
* or something similar.
*/
#define CLI_CORE_CMD_LIST	\
        CLI_CMD("help", "Show a list of commands", cli_help) \
		CLI_CMD("test", "A test command", cli_test) \
        /* Realtime Clock commands. */ \
        CLI_CMD("rtc_get", "Get the RTC time", cli_rtc_get) \
        CLI_CMD("rtc_set", "Set the RTC time", cli_rtc_set) \
        /* SD card debugging commands. */ \
        CLI_CMD("sd_init", "Initialize the SD card", cli_sd_init) \
        CLI_CMD("sd_ls", "List all files on the SD card", cli_sd_ls) \
        CLI_CMD("sd_cat", "Dump a file", cli_sd_cat) \
        CLI_CMD("sd_dd", "Dump all .csv files", cli_sd_dd) \
        CLI_CMD("sd_append", "Append a comment to a file", cli_sd_append) \
        CLI_CMD("sd_create", "Create a file", cli_sd_create) \
        CLI_CMD("sd_del", "Delete a file or folder of files", cli_sd_del) \
        /* Data logging commands */ \
        CLI_CMD("log", "Log sensor data to command line.", cli_log_data) \
        CLI_CMD("logapp", "Log sensor data to the andriod app", cli_log_app) \
        CLI_CMD("logfile", "Log sensor data to file.", cli_log_file) \
        /* Misc. commands*/ \
        CLI_CMD("reset", "Reset the SeaSense (BE CAREFUL - KILLS ALL PROCESSES)", cli_wdt_reset) \
        

/** Generate function prototypes for all CLI_CMD structs in CLI_CORE_CMD_LIST
*/
#undef CLI_CMD
#define CLI_CMD(cmd, desc, func) void func(int argc, char *argv[]);		
CLI_CORE_CMD_LIST

/**Array containing all CLI_CMD structs*/
cli_cmd_t cli_cmds[] = {
#undef CLI_CMD
#define CLI_CMD(cmd, desc, func) {cmd, desc, func},
	CLI_CORE_CMD_LIST
};

/* Calculate the number of CLI commands based upon the sizes. */
int num_cli_cmds = sizeof(cli_cmds)/sizeof(cli_cmds[0]);


/************************  function prototypes *************************/
/** Recursively print all files/folders/filesizes starting from the given directory. 
* @param dir Top directory of the filesystem being iterated through
* @param numTabs Depth to start searching the given dir at 
*/
void printDirectory(File dir, int numTabs);
/** Scans SD card for a filename in the format of YYMMDDxx.csv contained 
* within the given directory. This function will recursively search the directory
* until it hits a filename in sequence (xx = 00->99) that doesn't exist, 
* and will return a full path to said filename.
* @param filenum Sequential recording number
* @param directory File directory (8.3 name formatted as YYYYMMDD/)
* @return Pointer to a new 8.3 filename containing YYMMDD and a number (00-99) 
* @see cli_log_file(int argc, char *argv[])
*/
char* newFile(int filenum,char* directory);
/** Recursively search through each dir on the SD card and 
* dump the contents of all .CSV files contained within.
* @param dir Top directory to begin searching within.
* @param numTabs Depth within the top directory to begin searching at
*/
void dumpCSV(File dir, int numTabs); 
/** Check to see if a file is .csv format 
* @param filename 8.3 Filename
* @return true or false
*/
bool isCSV(char* filename);
/** Delete all files within a directory. 
* Created because SD.delete() doesn't work on a full dir.
* @param dir Directory containing files
*/
void rmSubFiles(File dir);
/** Recursively print the number of CSV files contained on the sd card to the bluetooth serial port.
* Used for the android app.
* @param dir Top directory to begin searching within.
* @param numTabs Depth within the top directory to begin searching at
* @see processCMD(char *command, int size)
*/
void numCSVfiles(File dir, int numTabs);
/** Recursively print the file size and name of each CSV file contained on the sd card
* Used for the android app.
* @param dir Top directory to begin searching within.
* @param numTabs Depth within the top directory to begin searching at
* @param dirName name of the directory being searched (used in recursion to create a fullpath string for each filename)
* @see processCMD(char *command, int size)
*/
void dumpCSVinfo(File dir, int numTabs, char* dirName);
// Wait, I totally had something for this
void dangerzone(); 
/************************************************************************/


// processCMD:
//  consumes a pointer to an entry from the bluetooth serial port and the 
//  length of said command, searches said entry for a one of the given input
//  commands and excecutes the command if possible
void processCMD(char *command, int size)
{
    int j;
    int argc = 0;
    
    // print out recieved command to the serial monitor
    // Serial.println(F("Recieved new entry: "));
    cli_argv[argc] = &command[0]; // argv[0] == base addr for given command
    command[size+1] = '\0'; // terminate input string with a null (so that string.h commands end at eos)
    // if no command was recieved, indicate so
    if(command[0] == '\0'){ 
       // Serial.println(F("Recieved blank entry"));
        return;
    }
    // otherwise, iterate through the input string and look for a command
    for(j=0;j<size;j++){ 
        //Serial.print(command[j]); // print each char to the serial monitor
        if (command[j]==' ') // treat spaces as argument separators
        {
            argc++; // increase num args for each spacce
            cli_argv[argc] = &command[j+1]; // fill cli_argv[argc] with commands
            // replace the space with a null 
            // strcmp will stop looking after this position (i.e. only search 1st arg for command)
            command[j] = '\0';
        }
    }
   
    //***** commands not added to CLI_CORE_CMD_LIST (hidden from help menu) *******
    // easter came early
    if ((strcmp("danger",strlwr(command)) == 0)
                || (strcmp("dangerzone",strlwr(command)) == 0)
                || (strcmp("something",strlwr(cli_argv[1])) == 0)
                || (strcmp("zone",strlwr(cli_argv[1])) == 0)
        ){
            dangerzone();
            return;
        }
    // dump number of files and size of each (for android app)
    else if (strcmp("fileinfo",strlwr(cli_argv[0])) == 0){
            _numCSV = 0; // reset count of CSV files stored on the card
            File currentFile;
            currentFile.seek(0);
            currentFile = SD.open("/");
            numCSVfiles(currentFile,0);
            Serial.print(_numCSV); Serial.print(F(","));
            currentFile.seek(0); // go to the top directory of the SD card
            currentFile = SD.open("/"); // open the root directory of the SD card
            dumpCSVinfo(currentFile, 0,"/");
            currentFile.close(); // close file upon exit
            // get the number of .CSV files stored on the card
            return;
        }
    else  if ((strcmp("%CONNECT",command)==0) 
        || (strcmp("%DISCONNECT",command)==0)
        || (strcmp("%DT",command)==0)
       ){
        return;
    }
    //***** end of non CLI_CORE_CMD_LIST cmds *******
    
    // search through commands list for a command matching the input
    for(j=0;j<num_cli_cmds;j++){
        if(argc == 1){ // if only one arg is given...
            if(strcmp(cli_cmds[j].name,command) == 0){ // search for a corresponding command
                // call the command, passing in the command line input
                return cli_cmds[j].cli_function(argc,&cli_argv[0]);
            } 
        }
        
        else { // if multiple args are given...
            // commands contianed in CLI_CORE_CMD_LIST
            if(strcmp(cli_cmds[j].name,command) == 0){ // search for a corresponding command
                // call the command, passing in a string starting with the second input argument
                return cli_cmds[j].cli_function(argc,cli_argv);
            } 
        }
    }
    // if the input doesn't match any valid commands, indicate so
    Serial.println(F("Error: Invalid Command\n\r"));
    return;
}

/**Print out all available commands within CLI_CORE_CMD_LIST and their descriptions
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_help(int argc, char *argv[])
{
    int count;
    Serial.println(F("Help:\n"));
    // iterate through the commands list and print out each command name and description
    for(count=0;count<num_cli_cmds;count++)
    {
        Serial.print(F("\t"));
        Serial.print(cli_cmds[count].name);
        Serial.print(F(": "));
        Serial.println(cli_cmds[count].description);
    }
    return;
}

/** Print a test string to illustrate the use of the command line client.
* Also prints each argv and its corresponding argc number to demonstrate input parsing 
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_test(int argc, char *argv[]) 
{
    //print an indicator that the command was recieved
    Serial.println(F("It's alive! This is only a test\n\rTry adding additional arguments\n\r"));
    // individually log each additional argument vector to show how commands are parsed
    int i;
    for (i=0;i<argc;i++)
    {
        Serial.print(F("argv["));
        Serial.print(i+1);
        Serial.print(F("] = "));
        Serial.println(argv[i+1]);
    }
    return;
}

/** Print the current time
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_rtc_get(int argc, char *argv[])
{
    // if the RTC isn't running, throw an error
    if (! rtc.isrunning()) { 
        Serial.println(F("Error: RTC is not running.\n\rPlease set RTC_AUTOSET to true or use the rtc_set command"));
    }
    // create a temporary buffer for storing the current time in
    char temp[25];
    // read the current time from the RTC and print it out
    DateTime now = rtc.now();
    sprintf(temp,"%04d/%02d/%02d - %02d:%02d:%02d\n",now.year(),now.month(),now.day(),now.hour(),now.minute(),now.second());
    Serial.println(temp);
    return;
}

/** Set the RTC to a given time and date
* Input from command line should follow "rtc_set yyyy/mm/dd hh:mm:ss"
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
* @see RTC_AUTOSET
*/
void cli_rtc_set(int argc, char *argv[])
{
    // if autoset is turned on, don't allow for the time to be manipulated
    if (RTC_AUTOSET == 1){
        Serial.println(F("Error: RTC Autoset set to true"));
        return;
    }
    else{
        // if there aren't enough args don't parse a new time
        if(argc < 2 || argc > 2) {
            Serial.println(F("Error: incorrect number of arguments. Input should be yyyy/mm/dd hh:mm:ss"));
            return;
        }
        
        //otherwise, read in the command line input, parse it to yyyy/mm/dd hh:mm:ss
        int year,month,day,hour,minute,second;
        char temp[35]; 
        sscanf(argv[1],"%d/%d/%d",&year,&month,&day);
        sscanf(argv[2],"%2d%c%2d%c%2d",&hour,temp,&minute,temp,&second);
        
        // set the time based on the input and print it to the command line
        rtc.adjust(DateTime(year,month,day,hour,minute,second));
        sprintf(temp,"Set time to %04d/%02d/%02d %02d:%02d:%02d",year, month, day, hour, minute, second);
        Serial.println(temp);
        return; // exit upon setting the time
    }
    
    // if none of the above qualify, throw an error
    Serial.println(F("Error: input should be yyyy/mm/dd hh:mm:ss"));
    return;
}

/** @breif Attempt to re-initialize the SD card.
* Note that the SD library doesn't have the ability to terminate an already-initialized SD card,
* so attempting an sd_init after already initializing the card will throw an error. 
* I can't find a workaround to this :(
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_sd_init(int argc, char *argv[])
{   Sd2Card card; 
 
    if(sd_logData){ // don't mess up the memory card by resetting while writing
        Serial.print(F("Error: currently writing to SD card!"));
        return;
    }
 
    digitalWrite(OLED_CLK,HIGH); //make sure the OLED CS isn't asserted
    pinMode(10, OUTPUT); // per SD install instructions (ethernet chip CS)
    digitalWrite(10, HIGH); // de-assert chip select on ethernet chip (keeps spi lines clear)
    pinMode(53, OUTPUT);    // default SS pin on arduino mega (must be set as output)
    
    // toggle the SD card CS
    Serial.println(F("\tToggling SD card ChipSelect"));
    digitalWrite(SD_CS,HIGH);
    SPI.end();
    delay(50);
    
    // initialize the SD card
    // first search for the actual card
    Serial.print(F("\tSearching for SD card ..."));
    if (!card.init(SPI_HALF_SPEED, SD_CS)) 
        Serial.println(F(" card not found"));  
    else {
        // if the card is present, enable it on the SPI interface
        Serial.println(F(" card found"));
        Serial.print(F("\tInitializing SD card ..."));
        if (!SD.begin(SD_CS)){
            Serial.println(F(" failed."));
            Serial.println(F("\tHas the card already been initialized?\n\r\tThis operation can only be completed once"));
        }
        else
            Serial.println(F(" done"));
    }
    return;
}

/** Print all files and directories saved on the SD card
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
* @see printDirectory(File dir, int numTabs)
*/
void cli_sd_ls(int argc, char *argv[])
{
    if(sd_logData){
        Serial.println(F("Error: turn off data logging first!"));
        return;
    }
    
    File currentFile; // create a new 'File' instance
    currentFile.seek(0); // go to the top directory of the SD card
    currentFile = SD.open("/"); // open the root directory of the SD card
    printDirectory(currentFile, 0); // pass the open directory to the printDirectory() function
    currentFile.close(); // close file upon exit
    return;
}

/** Print out all data contained in a file.
* Proper syntax is sd_cat FILENAME.EXT or sd_cat FOLDERNAME/FILENAME.EXT
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_sd_cat(int argc, char *argv[])
{
    // if an incorrect number of arguments are given, throw an error and exit
    if(argc<1 || argc>1)
    {
        Serial.println(F("Error: incorrect number of args"));
        return;
    }
    
    if(sd_logData){
        Serial.println(F("Error: turn off data logging first!"));
        return;
    }
    
    // otherwise, create a new file instance 
    File currentFile;
    // open to the given file argument
    currentFile = SD.open(argv[1]);
    if (currentFile){ // if the file is successfully opened, iterate through its contents and print
        if(currentFile.isDirectory()){
            Serial.println(F("Error: given dir instead of file"));
            return;
        }
        Serial.print (F("Contents of ")); Serial.print(argv[1]); Serial.println(F(":"));
        // read from the file until there's nothing else in it:
        while (currentFile.available()) {
          Serial.write(currentFile.read());
        }
        Serial.print(F("\n\r"));
    } else {
        // if the file doesn't open, print an error:
        Serial.println(F("error opening file"));
    }
    return;
}

/** Dump all .csv files on the SD card
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
* @see dumpCSV(File dir, int numTabs)
*/
void cli_sd_dd(int argc, char *argv[])
{
    // don't read from the card if it's being written to by the ISR
    if(sd_logData){
        Serial.println(F("Error: can't dump files while logging to SD card"));
        return;
    }
    // otherwise, create a new file instance, and point it to the top of the directory
    File currentFile;
    currentFile.seek(0);
    currentFile = SD.open("/");
    // once the directory has been opened, print out all CSV files located within
    dumpCSV(currentFile,0);
    // close the file instance upon finishing
    currentFile.close();
    Serial.print(F("\n\r"));
    return;
}

/** Append a comment to a file
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing text to be written to file
*/
void cli_sd_append(int argc, char *argv[])
{
    // if an incorrect number of arguments are given, throw an error
    if(argc<2)
    {
        Serial.println(F("Error: incorrect number of args"));
        return;
    }
    
    if(sd_logData){
        Serial.println(F("Error: turn off data logging first!"));
        return;
    }
    
    // otherwise, create a new file instance and open the given file argument
    File currentFile;
    currentFile = SD.open(argv[1],FILE_WRITE);

    if(currentFile){ // if the file successfully opens, write the given argv to it
        if(currentFile.isDirectory()){
            Serial.println(F("Error: given dir instead of file"));
            return;
        }
        // print the name of the file being written to
        Serial.print(F("Writing to file ")); Serial.println(argv[1]);
        
        // iterate through each argv and add each to the file, separated by a space
        int i;
        currentFile.print('#'); // add a .csv comment indicator before the actual text being appended
        for(i=1;i<argc;i++)
        {
            currentFile.print(argv[i+1]);
            currentFile.print(F(" ")); //argv parsing removes spaces, so reinsert them!
        }
        currentFile.print(F("\n\r")); // add a new line after appending text
        currentFile.close();
        Serial.println(F("Write Complete"));
    } else { // if the file doesn't open, throw an error
        Serial.print(F("Error opening ")); Serial.println(argv[1]);
        Serial.println(F("Don't forget to specify file directories"));
    }
}

/** Create a new file on the SD card
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_sd_create(int argc, char *argv[]) {
    if(sd_logData){
        Serial.println(F("Error: turn off data logging first!"));
        return;
    }
    // if an incorrect number of arguments are given, throw an error and return
    if(argc<1 || argc>1)
    {
        Serial.println(F("Error: incorrect number of args"));
        return;
    }
    // otherwise, create a new file instance 
    File currentFile;
    // open the given filename for writing (will create the file if it doesn't already exist)
    currentFile = SD.open(argv[1],FILE_WRITE);
    if(currentFile) { // if the file is created, indicate so 
        Serial.print(F("Created file ")); Serial.println(argv[1]);
    } else // otherwise, throw an error
        Serial.println(F("Error creating file. Does name conform to 8.3 convention?"));
    currentFile.close(); // either way, close the file and exit
    return;
}

/** Delete a file or directory of files from the SD card
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
* @see rmSubFiles(File dir)
*/
void cli_sd_del(int argc, char *argv[]) {  
    if(sd_logData){
        Serial.println(F("Error: turn off data logging first!"));
        return;
    }
    // if an incorrect number of args are given, throw an error
    if(argc<1 || argc>1)
    {
        Serial.println(F("Error: incorrect number of args"));
        return;
    }
    // otherwise, check to see that the file or directory exists
    if(SD.exists(argv[1])){
        // begin by checking to see if the given file is a directory
        File currentFile; // need to do this to use .isDirectory()
        currentFile = SD.open(argv[1]);
        // if the file is a directory, remove all files from it, then remove the dir itself
        // the SD library only allows for the deletion of empty directories, so I've created
        // the rmSubFiles() function to iterate through the dir and delete all files within
        if(currentFile.isDirectory()){
            rmSubFiles(currentFile); // delete all files in the directory
            currentFile.close(); // close the opened dir
            // create "DIRNAME/" from input "DIRNAME"
            char* dir = (char*)calloc(10,sizeof(char));
            strcpy(dir,argv[1]);// strcat(dir,"/");
            // indicate that the directory is being removed, and attempt to delete it
            Serial.print(F("Removing dir: ")); Serial.println(dir);
            // if the directory can't be deleted, throw an error
            if(!SD.rmdir(dir)) Serial.println(F("Error: couldn't remove dir"));
            free(dir); // free the memory containing the directory name
        }
        // if the file input isn't a directory...
        else{
            currentFile.close(); // close the open file
            SD.remove(argv[1]); // delete it
            Serial.print(F("Removed file ")); Serial.println(argv[1]); // and indicate so
        }
    // if the given filename doesn't exist ...
    } else
        Serial.println(F("Error: given file doesn't exist"));
    return;
}

/** Log data to the bluetooth serial port in a human-readable format
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_log_data(int argc, char *argv[])
{
    // if already logging data via the bluetooth serial port, indicate so and return
    if(app_logData){
        Serial.println(F("Error: already logging serial data!"));
        return;
    }
    // otherwise, allow for the ISR to log data to the bluetooth port
    logData = !logData;
    
    if(logData) // if data logging has been enabled, print out a header for each column
        Serial.print(F("Time\t\tTemp\tDepth\tCond\tLight\tHead"));
        #ifdef _TP
            Serial.print(F("\tIntTemp\tIntPressure\n\r"));
        #else
            Serial.print(F("\n\r"));
        #endif

    return;
}

/** Log data to the bluetooth serial port in a machine-recognizable format
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_log_app(int argc, char *argv[])
{
    // if already logging data via the bluetooth serial port, indicate so and return
    if(logData){
        Serial.println(F("Error: already logging serial data!"));
        return;
    }
    delay(10); // give the app a little wiggle room
    
    // if data logging is being turned OFF, send a EOF indicator to the android app
    if(app_logData){
        Serial.print(F("U+1F4A9")); Serial.print(F(","));
    }
    
    // allow/prevent the ISR from logging data to the bluetooth port
    app_logData = !app_logData;
    return;
}

/** @breif Create a new datafile on the SD card and begin logging data to it.
* Files recorded on the SD card will be saved in folders corresponding to the date of the recording.
* Filenames will correspond to YYMMDD##.CSV, where ## is the current number of files recorded
* on the corresponding date.
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
* @see newFile(int filenum, char* directory)
*/
void cli_log_file(int argc, char *argv[])
{   
    // sending logfile will toggle whether or not data is being logged to the SD card
    sd_logData = !sd_logData;
    
    // if data logging has been enabled...
    if(sd_logData){ 
        DateTime now = rtc.now(); // get the current date and time
        // prepare empty arrays for the filename, directory name, and full file path
        char* fullpath = (char*)calloc(24,sizeof(char));
        char* directory = (char*)calloc(10,sizeof(char));
        char* filename = (char*)calloc(13,sizeof(char));
        
        // print the current date (YYYYMMDD) to the directory name
        sprintf(directory,"%04d%02d%02d/",now.year(),now.month(),now.day());
        // if the directory already exists, add it to the filepath
        if (SD.exists(directory)){ 
            fullpath = strcat(fullpath,directory);
        }
        // if the directory doesn't exist, create it and then add it to the filepath
        else{
            SD.mkdir(directory);
            Serial.print(F("Created directory ")); Serial.println(directory);
            fullpath = strcat(fullpath,directory);
        }
        // after the dir has been created, create a filename for the file using the newFile() function
        filename = newFile(0,directory);
        // once a new filename has been created, append the filename to the filepath
        fullpath = strcat(fullpath,filename);
        
        // make sure the OLED is disabled before modifying the contents of the SD card
        // remember that they share the SPI bus
        digitalWrite(OLED_CLK,HIGH);
        // create a new CSV file corresponding to the given filepath
        // this file is globally accessible, so any function/file can write to it
        SDfile = SD.open(fullpath,FILE_WRITE);
        // add a header to the CSV file
        SDfile.println(F("Time,Temp,Depth,Cond,Light,Head,AccelX,AccelY,AccelZ,GyroX,GyroY,GyroZ"));
        // send the name of the filepath to the bluetooth serial port
        Serial.print(F("Logging data to ")); Serial.println(fullpath);
        // free the memory occupied by the filename, directory, and filepath
        free(directory);
        free(filename);
        free(fullpath);
    
    // if data logging is toggled to off...
    } else { 
        // add an EOF to the file so that the android file can read from it
        SDfile.print(F("U+1F4A9")); 
        SDfile.print(F(","));
        // close the file, and indicate that data logging has been disabled
        SDfile.close();
        Serial.println(F("Stopped logging data to file"));
    }
    return;
}

/** @breif Reset the microcontroller by enabling the watchdog timer and letting it overflow.
* Note that for the Seeeduino Mega version I've commented out the watchdog timer config
* and replaced it with a jump to the first address in program memory. Due to the bootloader on
* the 2560 of the seeeduino board, the micro will end up in a permanent WDT overflow loop
* if you enable the watchdog timer. This is NOT an issue on the blueduino board.
* @param argc Number of space-separated arguments entered via the command line interface
* @param argv Character array containing argument vectors
*/
void cli_wdt_reset(int argc, char *argv[]) {
    // don't mess up the memory card by resetting while writing
    if(sd_logData){ 
        Serial.print(F("Turning off data logging . . ."));
        sd_logData = !sd_logData;
        SDfile.print(F("U+1F4A9")); 
        SDfile.print(F(","));
        SDfile.close();
        Serial.println(F("Stopped logging data to file"));
    }
    // enable the watchdog timer with a 500mS overflow
    // wdt_enable(WDTO_500MS); // may cause issue depending on bootloader (wdt reset but no timer reset - will get stuck in infinite wdt reset loop)
    Serial.println(F("System reset ..."));
    delay(15);
    asm volatile ("  jmp 0"); 
     
    // eat processor cycles until the wdt overflows
    // while(1)
}

// printDirectory - prints out all files and directories on the SD card to the serial monitor
// function taken from https://www.arduino.cc/en/Tutorial/listfiles
void printDirectory(File dir, int numTabs) {
    dir.seek(0); // fix for sd_ls showing nothing after modifying files
    while (true) {
        File entry =  dir.openNextFile();
        if (! entry) {
          // no more files
          break;
        }
        for (uint8_t i = 0; i < numTabs; i++) {
          Serial.print(F("\t"));
        }
        Serial.print(entry.name());
        if (entry.isDirectory()) {
          Serial.println(F("/"));
          printDirectory(entry, numTabs + 1);
        } else {
          // files have sizes, directories do not
           Serial.print(F("\t\t"));
           Serial.println(entry.size(), DEC);
        }
        entry.close();
  }
}

// dumpCSV - scans the entire SD card and returns the contents of all CSV files stored within
void dumpCSV(File dir, int numTabs)
{
    // allocate some memory for the location of each file being printed
    char *fullfile = (char*)calloc(24,sizeof(char));
    
    dir.seek(0); // start at the top directory of the SD card
    
    while (true) { // while there are still more files/dirs to iterate through...
        File entry =  dir.openNextFile(); // open the next available file.dir
        if (! entry) { // if the dir is empty (no more files), break
          break;
        }
        if (entry.isDirectory()) { // if the entry is a directory, iterate through its contents
          dumpCSV(entry, numTabs + 1);
        } else { // if the entry is a file...
            if(strchr(entry.name(),'~') == 0){ // don't count INDEXE~1 as a filename
                if(isCSV(entry.name())) // only return .csv files
                {
                    // copy the directory name and filename to char* fullfile[24]
                    strcpy(fullfile,dir.name()); 
                    strcat(fullfile,"/");
                    strcat(fullfile,entry.name());
                    // print the name of the file being dumped
                    Serial.print(F("File ")); 
                    Serial.print(fullfile);
                    Serial.print(F(":\n\r"));
                    // open the file using its filepath
                    File currentFile;
                    currentFile = SD.open(fullfile);
                    if (currentFile){ // if the file opens, dump its contents
                        // read from the file until there's nothing else in it:
                        while (currentFile.available()) {
                          Serial.write(currentFile.read()); // print the file contents to the serial port
                        }
                        // add a newline/return after each file dump
                        Serial.print(F("\n\r"));
                    } // end of if(currentFile)
                } // end of if(isCSV(entry.name()))
            } // end of if(strchr(entry.name(),'~') == 0)
        } // end of <if entry is file>
        entry.close(); // close each entry before moving on to the next
  } // when there are no more memory contents left to read from...
  free(fullfile); // free the memory occupied by the filepath
  return; // exit
}

// print the filesize of each .csv file
void dumpCSVinfo(File dir, int numTabs,char* dirName)
{
    dir.seek(0); // start at the top directory of the SD card

    while (true) { // while there are still more files/dirs to iterate through...
        File entry =  dir.openNextFile(); // open the next available file.dir
        if (! entry) { // if the dir is empty (no more files), break
          break;
        }
        if (entry.isDirectory()) { // if the entry is a directory, iterate through its contents
          dumpCSVinfo(entry, numTabs + 1,entry.name());
        } else { // if the entry is a file...
            if(strchr(entry.name(),'~') == 0){ // don't count INDEXE~1 as a filename
                if(isCSV(entry.name())) // only return .csv files
                {
                    Serial.print(dirName);
                    Serial.print(F("/"));
                    Serial.print(entry.name());
                    Serial.print(F(","));
                    Serial.print(entry.size(), DEC);
                    Serial.print(F(","));
                } // end of if(isCSV(entry.name()))
            } // end of if(strchr(entry.name(),'~') == 0)
        } // end of <if entry is file>
        entry.close(); // close each entry before moving on to the next
  } // when there are no more memory contents left to read from...
  return; // exit
}

void numCSVfiles(File dir, int numTabs) {
    dir.seek(0);
    while (true) { // while there are still more files/dirs to iterate through...
    File entry =  dir.openNextFile(); // open the next available file.dir
    if (! entry) { // if the dir is empty (no more files), break
      break;
    }
    if (entry.isDirectory()) { // if the entry is a directory, iterate through its contents
      numCSVfiles(entry, numTabs + 1);
    } else { // if the entry is a file...
        if(strchr(entry.name(),'~') == 0){ // don't count INDEXE~1 as a filename
            if(isCSV(entry.name())) // only return .csv files
            {
               _numCSV++;
            } // end of if(isCSV(entry.name()))
        } // end of if(strchr(entry.name(),'~') == 0)
    } // end of <if entry is file>
    entry.close(); // close each entry before moving on to the next
  } // when there are no more memory contents left to read from...
}


// newfile - scans SD card for a filename in the format of YYMMDDxx.csv contained within the given directory
// returns a pointer to a new 8.3 filename containing YYMMDD and a number (00-99)
char* newFile(int filenum, char* directory){
        DateTime now = rtc.now(); // begin by grabbing the current date and time
        boolean exists = true; // assume that the filename already exists
        while(exists == true){ // keep incrementing the file number until a new filename has been found
             // allocate memory for the filename and filepath
             char* fullpath = (char*)calloc(24,sizeof(char));
             char* filename = (char*)calloc(13,sizeof(char));
             // create a new filename to check (YYMMDD##.csv)
             sprintf(filename,"%02d%02d%02d%02d.csv",now.year()-2000,now.month(),now.day(),filenum);
             // append the given directory and new filename to the filepath
             fullpath = strcat(fullpath,directory);
             fullpath = strcat(fullpath,filename);
             // if a file exists at the filepath location, increment filenum and try again
             if(SD.exists(fullpath)){
                 filenum++;
                 free(fullpath);
                 free(filename);
             }
             // if no file exists at the filepath location, return the filename
             else{
                 free(fullpath);
                 return filename; // stop when a non-existing filename has been created
             }
        }
}

// isCSV - returns true if a given filename ends with .CSV or .csv, false otherwise
bool isCSV(char* filename) {
  // convert the input string to lowercase and check to see if it contains ".csv"
  if (strstr(strlwr(filename), ".csv")) {
    return true;
  } else {
    return false;
  }
}

// rmSubFiles - deletes all files within the given directory
// based on the code used in printDirectory and dumpCSV
void rmSubFiles(File dir)
{   // allocate memory for the filepath of each file being deleted
    char *fullfile = (char*)calloc(24,sizeof(char)); 
    // start from the beginning of the given directory
    dir.seek(0);
    // while the directory still contains files...
    while (true) {
        // open the next available file
        File entry =  dir.openNextFile();
        if (! entry) { // no more files
          break;
        }
        // copy the directory name and filename to the filepath
        strcpy(fullfile,dir.name());
        strcat(fullfile,"/");
        strcat(fullfile,entry.name());
        // remove the file located at the filepath destination
        Serial.print(F("Removing file ")); 
        Serial.println(fullfile);
        // close the entry for the next iteration
        entry.close();
        // throw an error if trying to delete a directory within a directory
        if(!SD.remove(fullfile)) Serial.println(F("Error: must delete sub-directories first"));
    }
    // free the filepath's memory when finished, and exit
    free(fullfile); 
    return;
}

// I see you've found the easter egg
/**Oh wait, I had something for this*/
void dangerzone()
{
    int quote = random();
    quote %= 14; // num cases + 1
    constrain(quote,0,14); // just to be sure
    switch(quote){
        case 0: 
            Serial.println(F("For I am a sinner in the hands of an angry God. Bloody Mary, full of vodka, blessed are you among cocktails."));
            break;
        case 1:
            Serial.println(F("I told you to buy lemon curd, Woodhouse. Now what am I going to spread on my toast? Your tears?"));
            break;
        case 2:
            Serial.println(F("I'm scared if I stop all at once, the cumulative hangover will literally kill me"));
            break;
        case 3:
            Serial.println(F("Danger zone!"));
            break;
        case 4:
            Serial.println(F("I swear to god I had something for this"));
            break;
        case 5:
            Serial.println(F("That's how you get ants"));
            break;
        case 6:
            Serial.println(F("Boop"));
            break;
        case 7: 
            Serial.println(F("I'm Pacman Jones!"));
            break;
        case 8:
            Serial.println(F("Big whoop. I’m spooning a Barrett .50-cal. I could kill a building"));
            break;
        case 9:
            Serial.println(F("I've been working on miniaturization for years!\n\rKrieger, those were shrinky dinks.\n\rIt's the same principle!"));
            break;
        case 10: 
            Serial.println(F("That wasn't a brain chip. That was a just a sticker of the backpack of a little Lego spaceman"));
            break;
        case 11:
            Serial.println(F("My head feels like a bunch of monkeys fighting over a bucket of marbles"));
            break;
        case 12:
            Serial.println(F("Look, auditory hallucinations aren't going to make you any less delicious"));
            break;
        case 13:
            Serial.println(F("Why don't you go back to Jamaica?\n\rA) Because I got deported."));
            break;
        default:
            Serial.println(F("Something, something, danger zone! I know. I’m not even trying anymore"));
    }
    
}