## Reproducible builds

Reproducibility is a goal of the Sparrow Wallet project. 
As of v1.5.0 and later, it is possible to recreate the exact binaries in the Github releases (specifically, the contents of the `.tar.gz` and `.zip` files).

Due to minor variances, it is not yet possible to reproduce the installer packages (`.deb`, `.rpm` and `.exe`).
In addition, the OSX binary is code signed and thus can't be directly reproduced yet. 
Work on resolving both of these issues is ongoing.

### Reproducing a release

#### Install Java

Because Sparrow bundles a Java runtime in the release binaries, it is essential to have the same version of Java installed when creating the release.
For v1.5.0 and later, this is AdoptOpenJdk jdk-16.0.1+9 Hotspot. 
It is available for all supported platforms from the [AdoptOpenJdk site](https://adoptopenjdk.net/archive.html?variant=openjdk16&jvmVariant=hotspot).

For reference, the downloads are as follows:
- [Linux x64](https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jdk_x64_linux_hotspot_16.0.1_9.tar.gz)
- [MacOS x64](https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jdk_x64_mac_hotspot_16.0.1_9.tar.gz)
- [Windows x64](https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jdk_x64_windows_hotspot_16.0.1_9.zip)

It is also possible to install via a package manager on *nix systems. For example, on Debian/Ubuntu systems:
```shell
sudo apt-get install -y wget apt-transport-https gnupg
wget https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public
gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --import public
gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --export --output adoptopenjdk-archive-keyring.gpg
rm adoptopenjdk-keyring.gpg
sudo mv adoptopenjdk-archive-keyring.gpg /usr/share/keyrings
echo "deb [signed-by=/usr/share/keyrings/adoptopenjdk-archive-keyring.gpg] https://adoptopenjdk.jfrog.io/adoptopenjdk/deb $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/adoptopenjdk.list
sudo apt update -y
sudo apt-get install -y adoptopenjdk-16-hotspot=16.0.1+9-3
```

A alternative option for all platforms is to use the [sdkman.io](https://sdkman.io/) package manager ([Git Bash for Windows](https://git-scm.com/download/win) is a good choice on that platform).
See the installation [instructions here](https://sdkman.io/install).
Once installed, run
```shell
sdk install java 16.0.1.hs-adpt
```

#### Other requirements

Other packages may also be necessary to build depending on the platform. On Debian/Ubuntu systems:
```shell
sudo apt install -y rpm fakeroot binutils
```

#### Building the binaries

The project can cloned for a specific release tag as follows:
```shell
GIT_TAG="1.5.0-beta1"
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

