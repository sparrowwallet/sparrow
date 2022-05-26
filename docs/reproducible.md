## Reproducible builds

Reproducibility is a goal of the Sparrow Wallet project. 
As of v1.5.0 and later, it is possible to recreate the exact binaries in the Github releases (specifically, the contents of the `.tar.gz` and `.zip` files).

Due to minor variances, it is not yet possible to reproduce the installer packages (`.deb`, `.rpm` and `.exe`).
In addition, the OSX binary is code signed and thus can't be directly reproduced yet. 
Work on resolving both of these issues is ongoing.

### Reproducing a release

#### Install Java

Because Sparrow bundles a Java runtime in the release binaries, it is essential to have the same version of Java installed when creating the release.
For v1.6.4 and later, this is Eclipse Temurin 17.0.2+8. 
It is available for all supported platforms from [Eclipse Temurin 17.0.2+8](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.2%2B8).

For reference, the downloads are as follows:
- [Linux x64](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.2%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.2_8.tar.gz)
- [MacOS x64](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.2%2B8/OpenJDK17U-jdk_x64_mac_hotspot_17.0.2_8.tar.gz)
- [MacOS aarch64](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.2%2B8/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.2_8.tar.gz)
- [Windows x64](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.2%2B8/OpenJDK17U-jdk_x64_windows_hotspot_17.0.2_8.zip)

It is also possible to install via a package manager on *nix systems. For example, on Debian/Ubuntu systems:
```shell
sudo apt-get install -y wget apt-transport-https gnupg
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update -y
sudo apt-get install -y temurin-17-jdk=17.0.2+8
sudo update-alternatives --config java
```

A alternative option for all platforms is to use the [sdkman.io](https://sdkman.io/) package manager ([Git Bash for Windows](https://git-scm.com/download/win) is a good choice on that platform).
See the installation [instructions here](https://sdkman.io/install).
Once installed, run
```shell
sdk install java 17.0.2-tem
```

#### Other requirements

Other packages may also be necessary to build depending on the platform. On Debian/Ubuntu systems:
```shell
sudo apt install -y rpm fakeroot binutils
```

#### Building the binaries

The project can cloned for a specific release tag as follows:
```shell
GIT_TAG="1.6.5"
git clone --recursive --branch "${GIT_TAG}" git@github.com:sparrowwallet/sparrow.git
```

Thereafter, building should be straightforward:

```shell
cd sparrow
./gradlew jpackage
```

The binaries (and installers) will be placed in the `build/jpackage` folder.

#### Verifying the binaries are identical

Note that you will be verifying the files in the `build/jpackage/Sparrow` folder against either the `.tar.gz` or `.zip` releases.
Download either of these depending on your platform and extract the contents to a folder (in the following example, `/tmp`).
Then compare all of the folders and files recursively:

```shell
diff -r build/jpackage/Sparrow /tmp/Sparrow
```

This command should have no output indicating that the two folders (and all their contents) are identical.

