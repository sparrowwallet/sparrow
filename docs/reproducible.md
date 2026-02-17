# Reproducible builds

Reproducibility is a goal of the Sparrow Wallet project.
As of v1.5.0 and later, it is possible to recreate the exact binaries in the Github releases (specifically, the contents of the `.tar.gz` and `.zip` files).

Due to minor variances, it is not yet possible to reproduce the installer packages (`.deb`, `.rpm` and `.exe`).
In addition, the OSX binary is code signed and thus can't be directly reproduced yet.
Work on resolving both of these issues is ongoing.

## Reproducing a release

### Install Java

Because Sparrow bundles a Java runtime in the release binaries, it is essential to have the same version of Java installed when creating the release.
For v1.6.6 to v1.9.1, this was Eclipse Temurin 18.0.1+10. For v2.0.0 to v2.3.1, this was Eclipse Temurin 22.0.2+9. For v2.4.0 and later, Eclipse Temurin 25.0.2+10 is used.

Note: Do not install Java using a system package manager (e.g. apt, dnf, rpm). 
Linux packages replace the JDK's bundled `cacerts` file with a symlink to the system CA certificates, which differ from those in the release tarballs and will produce a non-reproducible build.

#### Java from Adoptium github repo

It is available for all supported platforms from [Eclipse Temurin 25.0.2+10](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.2%2B10).

For reference, the downloads are as follows:
- [Linux x64](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_x64_linux_hotspot_25.0.2_10.tar.gz)
- [Linux aarch64](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.2_10.tar.gz)
- [MacOS x64](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_x64_mac_hotspot_25.0.2_10.tar.gz)
- [MacOS aarch64](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.2_10.tar.gz)
- [Windows x64](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_x64_windows_hotspot_25.0.2_10.zip)

On Linux, extract the tarball and set `JAVA_HOME` to use it for the build:
```shell
tar -xzf OpenJDK25U-jdk_x64_linux_hotspot_25.0.2_10.tar.gz
export JAVA_HOME=$PWD/jdk-25.0.2+10
export PATH=$JAVA_HOME/bin:$PATH
```

#### Java from SDKMAN

An alternative option for all platforms is to use the [sdkman.io](https://sdkman.io/) package manager ([Git Bash for Windows](https://git-scm.com/download/win) is a good choice on that platform).
See the installation [instructions here](https://sdkman.io/install).
Once installed, run
```shell
sdk install java 25.0.2-tem
```

### Other requirements

Other packages may also be necessary to build depending on the platform. On Debian/Ubuntu systems:
```shell
sudo apt install -y rpm fakeroot binutils
```

### Building the binaries

First, assign a temporary variable in your shell for the specific release you want to build. For the current one specify:

```shell
GIT_TAG="2.4.1"
```

The project can then be initially cloned as follows:

```shell
git clone --recursive --branch "${GIT_TAG}" https://github.com/sparrowwallet/sparrow.git
```

If you already have the sparrow repo cloned, fetch all new updates and checkout the release. For this, change into your local sparrow folder and execute:

```shell
cd {yourPathToSparrow}/sparrow
git pull --recurse-submodules
git checkout "${GIT_TAG}"
```

Note - there is an additional step if you updated rather than initially cloned your repo at `GIT_TAG`. 
This is due to the Git submodules which need to be checked out to the commit state they had at the time of the release. 
Only then your build will be comparable to the provided one in the release section of Github. 
To checkout the submodule to the correct commit for `GIT_TAG`, additionally run:

```shell
git submodule update --checkout
```

Thereafter, building should be straightforward. If not already done, change into the sparrow folder and run:

```shell
cd {yourPathToSparrow}/sparrow  # if you aren't already in the sparrow folder
./gradlew jpackage
```

The binaries (and installers) will be placed in the `build/jpackage` folder.

### Verifying the binaries are identical

Verify the built binaries against the released binaries on https://github.com/sparrowwallet/sparrow/releases.

Note that you will be verifying the files in the `build/jpackage/Sparrow` folder against either the `.tar.gz` or `.zip` releases.
Download either of these depending on your platform and extract the contents to a folder (in the following example, `/tmp`).
Then compare all of the folders and files recursively:

```shell
diff -r build/jpackage/Sparrow /tmp/Sparrow
```

This command should have no output indicating that the two folders (and all their contents) are identical.

If there is output, please open an issue with detailed instructions to reproduce, including build system platform.
