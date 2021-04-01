# Sparrow Bitcoin Wallet

Sparrow is a modern desktop Bitcoin wallet application supporting most hardware wallets and built on common standards such as PSBT, with an emphasis on transparency and usability.

More information (and release binaries) can be found at https://sparrowwallet.com. Release binaries are also available directly from [Github](https://github.com/sparrowwallet/sparrow/releases).

![Sparrow Wallet](https://sparrowwallet.com/assets/images/control-your-sends.png)

## Building

To clone this project, use 

`git clone --recursive git@github.com:sparrowwallet/sparrow.git`

In order to build, Sparrow requires Java 14 to be installed. The release packages can be built using

`./gradlew jpackage`

When updating to the latest HEAD

`git pull --recurse-submodules`

All jar files created are reproducible builds.

## Running

If you prefer to run Sparrow directly from source, it can be launched with

`./sparrow`

Java 14 or higher must be installed.

## Configuration

Sparrow has a number of command line options, for example to change its home folder or use testnet:

```
./sparrow -h

Usage: sparrow [options]
  Options:
    --dir, -d
      Path to Sparrow home folder
    --help, -h
      Show usage
    --level, -l
      Set log level
      Possible Values: [ERROR, WARN, INFO, DEBUG, TRACE]      
    --network, -n
      Network to use
      Possible Values: [mainnet, testnet, regtest, signet]
```

As a fallback, the network (mainnet, testnet, regtest or signet) can also be set using an environment variable `SPARROW_NETWORK`. For example:

`export SPARROW_NETWORK=testnet`

A final fallback which can be useful when running the Sparrow binary is to create a file called ``network-testnet`` in the Sparrow home folder (see below) to configure the testnet network.

Note that if you are connecting to an Electrum server when using testnet, that server will need to be running on testnet configuration as well.

When not explicitly configured using the command line argument above, Sparrow stores its mainnet config file, log file and wallets in a home folder location appropriate to the operating system:

Platform | Location
-------- | --------
OSX      | ~/.sparrow
Linux    | ~/.sparrow
Windows  | %APPDATA%/Sparrow

Testnet, regtest and signet configurations (along with their wallets) are stored in subfolders to allow easy switching between networks.

## Reporting Issues

Please use the [Issues](https://github.com/sparrowwallet/sparrow/issues) tab above to report an issue. If possible, look in the sparrow.log file in the configuration directory for information helpful in debugging. 

## License

Sparrow is licensed under the Apache 2 software licence.

## Credit

![Yourkit](https://www.yourkit.com/images/yklogo.png)

Sparrow Wallet uses the [Yourkit Java Profiler](https://www.yourkit.com/java/profiler/) to profile and improve performance. 
YourKit supports open source projects with useful tools for monitoring and profiling Java and .NET applications.
