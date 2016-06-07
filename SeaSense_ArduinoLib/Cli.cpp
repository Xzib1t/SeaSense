// Created by Georges Gauthier - glgauthier@wpi.edu
// ported from the original seasense PIC code

#include "Arduino.h"
#include "Cli.h"
#include "SeaSense.h"
#include <string.h>

// input text and number of args from a new command line entry
static int cli_argc;
static char* cli_argv[MAX_CLI_ARGV]; //see globals.h
 
/* Create a type for each CLI command. */
struct CLI_CMD
{
  char *name;
  char *description;
  void (*cli_function)(int argc, char *argv[]);
};
typedef struct CLI_CMD cli_cmd_t;


// generate all available commands and their associated content
#define CLI_CORE_CMD_LIST	\
        CLI_CMD("help", "Show a list of commands", cli_help) \
        CLI_CMD("#", "Comment", cli_comment) \
		CLI_CMD("test", "A test command", cli_test) \
        /* Realtime Clock commands. */ \
        CLI_CMD("rtc_get", "Get the RTC time", cli_rtc_get) \
        CLI_CMD("rtc_set", "Set the RTC time", cli_rtc_set) \
        /* SD card debugging commands. */ \
        CLI_CMD("sd_init", "Initialize the SD card", cli_sd_init) \
        CLI_CMD("sd_ls", "List all files on the SD card", cli_sd_ls) \
        CLI_CMD("sd_cat", "Dump a file", cli_sd_cat) \
        CLI_CMD("sd_append", "Append to a file", cli_sd_append) \
        CLI_CMD("sd_create", "Create a file", cli_sd_create) \
        CLI_CMD("sd_del", "Delete a file", cli_sd_del) \
        /* Data logging commands */ \
        CLI_CMD("log", "Log sensor data to command line.", cli_log_data) \
        CLI_CMD("logapp", "Log sensor data to the andriod app", cli_log_app) \
        CLI_CMD("logfile", "Log sensor data to file.", cli_log_file) \
        /* Misc. commands*/ \
        CLI_CMD("reset", "Reset the SeaSense (BE CAREFUL - KILLS ALL PROCESSES)", cli_wdt_reset) \
        

// Generate the list of function prototypes
#undef CLI_CMD
#define CLI_CMD(cmd, desc, func) void func(int argc, char *argv[]);		
CLI_CORE_CMD_LIST

// misc. function prototypes
void printDirectory(File dir, int numTabs);
char* newFile(int filenum);

// Generate the list of CLI commands. 
//      note that converting "text like this" to a char* is a
//      depreciated method and will throw warnings. Proper syntax
//      would be to use strcpy() or something similar
cli_cmd_t cli_cmds[] = {
#undef CLI_CMD
#define CLI_CMD(cmd, desc, func) {cmd, desc, func},
	CLI_CORE_CMD_LIST
};

/* Calculate the number of CLI commands based upon the sizes. */
int num_cli_cmds = sizeof(cli_cmds)/sizeof(cli_cmds[0]);

// processCMD:
//  consumes a pointer to an entry from the bluetooth serial port and the 
//  length of said command, searches said entry for a one of the given input
//  commands and excecutes the command if possible
void processCMD(char *command, int size)
{
    int j;
    int argc = 0;
    
    // print out recieved command to the serial monitor
    Serial.println("Recieved new entry: ");
    cli_argv[argc] = &command[0];
    command[size+1] = '\0';
    if(command[0] == '\0'){
        Serial.println("Recieved blank entry");
        return;
    }
    for(j=0;j<size;j++){ 
        Serial.print(command[j]); 
        if (command[j]==' ')
        {
            argc++;
            cli_argv[argc] = &command[j+1]; 
            command[j] = '\0';// strcmp will stop looking after this position (i.e. only search 1st arg for command)
        }
    }
    Serial.print('\n'); // new line after printing cmd
   
    // search through commands list for given input
    for(j=0;j<num_cli_cmds;j++){
        if(argc == 1){ // if only one arg is given...
            if(strcmp(cli_cmds[j].name,command) == 0){ // search for a corresponding command
                // call the command, passing in the command line input
                return cli_cmds[j].cli_function(argc,&cli_argv[0]);
            } 
        }
        else { // if multiple args are given...
            if(strcmp(cli_cmds[j].name,command) == 0){ // search for a corresponding command
                // call the command, passing in a string starting with the second input argument
                return cli_cmds[j].cli_function(argc,cli_argv);
            } 
        }
    }
    // if the input doesn't match any valid commands, indicate so
    Serial1.println("Error: Invalid Command\n\r");
    return;
}

//cli_help command - prints out all available commands and their descriptions
void cli_help(int argc, char *argv[])
{
    int count;
    
    Serial1.println("Help:\n");
    
    for(count=0;count<num_cli_cmds;count++)
    {
        Serial1.print("\t");
        Serial1.print(cli_cmds[count].name);
        Serial1.print(": ");
        Serial1.println(cli_cmds[count].description);
    }
    return;
}

// cli_comment command - ignore all command line entry after a '#' char
void cli_comment(int argc, char *argv[]) 
{
    Serial1.println(" ");
    return;
}

// cli_test command - print a string to illustrate the use of the command line client
void cli_test(int argc, char *argv[]) 
{
    Serial1.println("It's alive! This is only a test\n\rTry adding additional arguments\n\r");
    int i;
    for (i=0;i<argc;i++)
    {
        Serial1.print("argv[");
        Serial1.print(i);
        Serial1.print("] = ");
        Serial1.println(argv[i+1]);
    }
    return;
}

// cli_rtc_get - print the current time
void cli_rtc_get(int argc, char *argv[])
{
    if (! rtc.isrunning()) {
        Serial1.println("Error: RTC is not running.\n\rPlease set RTC_AUTOSET to true or use the rtc_set command");
    }
    char temp[25];
    DateTime now = rtc.now();
    sprintf(temp,"%04d/%02d/%02d - %02d:%02d:%02d\n",now.year(),now.month(),now.day(),now.hour(),now.minute(),now.second());
    Serial1.println(temp);
    return;
}

// cli_rtc_set - set a new RTC time
void cli_rtc_set(int argc, char *argv[])
{
    // if autoset is turned on, don't allow for the time to be manipulated
    if (RTC_AUTOSET == 1){
        Serial1.println("Error: RTC Autoset set to true");
        return;
    }
    else{
        // if there aren't enough args don't parse a new time
        if(argc < 2 | argc > 2) {
            Serial1.println("Error: incorrect number of arguments. Input should be yyyy/mm/dd hh:mm:ss");
            return;
        }
        
        // read in the command line input, parse it to yyyy/mm/dd hh:mm:ss
        int year,month,day,hour,minute,second;
        char temp[35]; 
        sscanf(argv[1],"%d/%d/%d",&year,&month,&day);
        sscanf(argv[2],"%2d%c%2d%c%2d",&hour,temp,&minute,temp,&second);
        
        // set the time based on the input and print it to the command line
        rtc.adjust(DateTime(year,month,day,hour,minute,second));
        sprintf(temp,"Set time to %04d/%02d/%02d %02d:%02d:%02d",year, month, day, hour, minute, second);
        Serial1.println(temp);
        return;
    }
    // if none of the above qualify, throw an error
    Serial1.println("Error: input should be yyyy/mm/dd hh:mm:ss");
    return;
}

void cli_sd_init(int argc, char *argv[])
{
    Serial1.print("Initializing SD card ...");
    if (!SD.begin(SD_CS))
        Serial1.println(" failed");
    else
        Serial1.println(" done");
    return;
}
// cli_sd_ls - prints all files and directories saved on the SD card
void cli_sd_ls(int argc, char *argv[])
{
    File currentFile;
    currentFile.seek(0); // fix for sd_ls showing nothing after modifying files
    currentFile = SD.open("/");
    printDirectory(currentFile, 0);
    currentFile.close();
    return;
}

// print out all data contained in a file
void cli_sd_cat(int argc, char *argv[])
{
    if(argc<1 | argc>1)
    {
        Serial1.println("Error: incorrect number of args");
        return;
    }
    
    File currentFile;
    currentFile = SD.open(argv[1]);
    if (currentFile){
        Serial1.print ("Contents of "); Serial1.print(argv[1]); Serial1.println(":");
        // read from the file until there's nothing else in it:
        while (currentFile.available()) {
          Serial1.write(currentFile.read());
        }
    } else {
    // if the file didn't open, print an error:
    Serial1.println("error opening file");
   }
    return;
}
 
// append to a file
// argv[1] = filename, argv[2]-argv[MAX_CLI_ARGV] = text to be written to file
void cli_sd_append(int argc, char *argv[])
{
    if(argc<2)
    {
        Serial1.println("Error: incorrect number of args");
        return;
    }
    
    File currentFile;
    currentFile = SD.open(argv[1],FILE_WRITE);
    if(currentFile){
        Serial1.print("Writing to file "); Serial1.println(argv[1]);
        int i;
        for(i=1;i<argc;i++)
        {
            currentFile.print(argv[i+1]);
            currentFile.print(" ");//argv parsing removes spaces
        }
        currentFile.print("\n\r"); // add a new line after appending text
        // add a special EOF emoji
        currentFile.print((char)0xF0);
        currentFile.print((char)0x9F);
        currentFile.print((char)0x92);
        currentFile.println((char)0xA9);
        currentFile.close();
        Serial1.println("Write Complete");
    } else {
        Serial1.print("Error opening "); Serial1.println(argv[1]);
    }
}

// create a new file on the SD card
void cli_sd_create(int argc, char *argv[]) {
    if(argc<1 | argc>1)
    {
        Serial1.println("Error: incorrect number of args");
        return;
    }
    File currentFile;
    currentFile = SD.open(argv[1],FILE_WRITE);
    if(currentFile) {
        Serial1.print("Created file "); Serial1.println(argv[1]);
    } else
        Serial1.println("Error creating file. Does name conform to 8.3 convention?");
    currentFile.close();
    return;
}

// delete a file from the SD card
void cli_sd_del(int argc, char *argv[]) {
    if(argc<1 | argc>1)
    {
        Serial1.println("Error: incorrect number of args");
        return;
    }
    if(SD.exists(argv[1])){
        SD.remove(argv[1]);
        Serial1.print("Removed file "); Serial1.println(argv[1]);
    } else
        Serial1.println("Error: given file doesn't exist");
    return;
}

// log data to the bluetooth serial port in a human-readable format
void cli_log_data(int argc, char *argv[])
{
    logData = !logData;
    if(logData) // if data logging is enabled, print out a header for each column
        Serial1.println("Time\t\tTemp\tDepth\tCond\tLight\tHead");
    return;
}

// log data to the bluetooth serial port in a machine-recognizable format
void cli_log_app(int argc, char *argv[])
{
    delay(2000);
    app_logData = !app_logData;
    return;
}

// Create a new datafile and begin logging data to it
void cli_log_file(int argc, char *argv[])
{
    sd_logData = !sd_logData;
    if(sd_logData){ // if data logging is toggled to on..
        char filename[13];
        strncat(filename,newFile(0),13);
        SDfile = SD.open(filename,FILE_WRITE);
        SDfile.println("Time,Temp,Depth,Cond,Light,Head,AccelX,AccelY,AccelZ,GyroX,GyroY,GyroZ");
        Serial1.print("Logging data to file "); Serial1.println(filename);
    } else { // if data logging is toggled to off...
        SDfile.print((char)0xF0);
        SDfile.print((char)0x9F);
        SDfile.print((char)0x92);
        SDfile.println((char)0xA9);
        SDfile.close();
        Serial1.println("Stopped logging data to file");
    }
    return;
}

// reset the microcontroller by enabling the watchdog timer and letting it overflow
void cli_wdt_reset(int argc, char *argv[]) {
    wdt_enable(WDTO_500MS);
    Serial1.println("System reset in 500mS");
    while(1); // eat processor cycles until the wdt overflows
}

void printDirectory(File dir, int numTabs) {
    dir.seek(0); // fix for sd_ls showing nothing after modifying files
    while (true) {
        File entry =  dir.openNextFile();
        if (! entry) {
          // no more files
          break;
        }
        for (uint8_t i = 0; i < numTabs; i++) {
          Serial1.print("\t");
        }
        Serial1.print(entry.name());
        if (entry.isDirectory()) {
          Serial1.println("/");
          printDirectory(entry, numTabs + 1);
        } else {
          // files have sizes, directories do not
           Serial1.print("\t\t");
           Serial1.println(entry.size(), DEC);
        }
        entry.close();
  }
}

// newfile - scans SD card for a filename in the format of YYMMDDxx.csv
// returns a pointer to a new 8.3 filename containing YYMMDD and a number (00-99)
char* newFile(int filenum){
        DateTime now = rtc.now();
        char filename[13];
        boolean exists = true;
        while(exists == true){
             sprintf(filename,"%02d%02d%02d%02d.csv",now.year()-2000,now.month(),now.day(),filenum);
             if(SD.exists(filename)){
                 filenum++;
             } else {
                 exists = false;
             }
        }
        return filename;
}
