# Reproducible builds

Reproducibility is a goal of the Sparrow Wallet project.
As of v1.5.0 and later, it is possible to recreate the exact binaries in the Github releases (specifically, the contents of the `.tar.gz` and `.zip` files).

Due to minor variances, it is not yet possible to reproduce the installer packages (`.deb`, `.rpm` and `.exe`).
In addition, the OSX binary is code signed and thus can't be directly reproduced yet.
Work on resolving both of these issues is ongoing.

## Reproducing a release

### Install Java

Because Sparrow bundles a Java runtime in the release binaries, it is essential to have the same version of Java installed when creating the release.
For v1.6.6 and later, this is Eclipse Temurin 18.0.1+10.

#### Java from Adoptium github repo

It is available for all supported platforms from [Eclipse Temurin 18.0.1+10](https://github.com/adoptium/temurin18-binaries/releases/tag/jdk-18.0.1%2B10).

For reference, the downloads are as follows:
- [Linux x64](https://github.com/adoptium/temurin18-binaries/releases/download/jdk-18.0.1%2B10/OpenJDK18U-jdk_x64_linux_hotspot_18.0.1_10.tar.gz)
- [MacOS x64](https://github.com/adoptium/temurin18-binaries/releases/download/jdk-18.0.1%2B10/OpenJDK18U-jdk_x64_mac_hotspot_18.0.1_10.tar.gz)
- [MacOS aarch64](https://github.com/adoptium/temurin18-binaries/releases/download/jdk-18.0.1%2B10/OpenJDK18U-jdk_aarch64_mac_hotspot_18.0.1_10.tar.gz)
- [Windows x64](https://github.com/adoptium/temurin18-binaries/releases/download/jdk-18.0.1%2B10/OpenJDK18U-jdk_x64_windows_hotspot_18.0.1_10.zip)

#### Java from Adoptium deb repo

It is also possible to install via a package manager on *nix systems. For example, on Debian/Ubuntu systems:

- Install dependencies:
```sh
sudo apt-get install -y wget curl apt-transport-https gnupg
```

Download Adoptium public PGP key:
```sh
curl --tlsv1.2 --proto =https --location -o adoptium.asc https://packages.adoptium.net/artifactory/api/gpg/key/public
```

Check if key fingerprint matches: `3B04D753C9050D9A5D343F39843C48A565F8F04B`:
```
gpg --import --import-options show-only adoptium.asc
```
If key doesn't match, do not proceed.

Add Adoptium PGP key to a the keyring shared folder:
```sh
sudo cp adoptium.asc /usr/share/keyrings/
```

Add Adoptium debian repository:
```sh
echo "deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
```

Update cache, install the desired temurin version and configure java to be linked to this same version:
```
sudo apt update -y
sudo apt-get install -y temurin-18-jdk=18.0.1+10
sudo update-alternatives --config java
```

#### Java from SDK

A alternative option for all platforms is to use the [sdkman.io](https://sdkman.io/) package manager ([Git Bash for Windows](https://git-scm.com/download/win) is a good choice on that platform).
See the installation [instructions here](https://sdkman.io/install).
Once installed, run
```shell
sdk install java 18.0.1-tem
```

### Other requirements

Other packages may also be necessary to build depending on the platform. On Debian/Ubuntu systems:
```shell
sudo apt install -y rpm fakeroot binutils
```

### Building the binaries

First, assign a temporary variable in your shell for the specific release you want to build. For the current one specify:

```shell
GIT_TAG="1.9.1"
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
This is due to the [drongo submodule](https://github.com/sparrowwallet/drongo/tree/master) which needs to be checked out to the commit state it had at the time of the release. 
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
