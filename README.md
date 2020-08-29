# Sparrow Bitcoin Wallet

Sparrow is a modern desktop Bitcoin wallet application supporting most hardware wallets and built on common standards such as PSBT, with an emphasis on transparency and usability.

More information (and release binaries) can be found at https://sparrowwallet.com. Release binaries are also available directly from [Github](https://github.com/sparrowwallet/sparrow/releases).

![Sparrow Wallet](https://sparrowwallet.com/assets/images/control-your-sends.png)

## Building

To clone this project, use `git clone --recursive git@github.com:sparrowwallet/sparrow.git`

In order to build, Sparrow requires Java 14 to be installed. The release packages can be built using

`./gradlew jpackage`

## Running

If you prefer to run Sparrow directly from source, it can be launched with

`./gradlew run`

Java 14 must be installed.

## Configuration

Sparrow stores it's configuration, log file and wallets in a location appropriate to the operating system:

Platform | Location
-------- | --------
OSX      | ~/.sparrow
Linux    | ~/.sparrow
Windows  | %APPDATA%/Sparrow

## Reporting Issues

Please use the [Issues](https://github.com/sparrowwallet/sparrow/issues) tab above to report an issue. If possible, look in the sparrow.log file in the configuration directory for information helpful in debugging. 

## License

Sparrow is licensed under the Apache 2 software licence.