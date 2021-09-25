# Reproducible builds

Reproducibility is a goal of the Sparrow Wallet project. 
As of v1.5.0 and later, it is possible to recreate the exact binaries in the Github releases (specifically, the contents of the `.tar.gz` and `.zip` files).

Due to minor variances, it is not yet possible to reproduce the installer packages (`.deb`, `.rpm` and `.exe`).
In addition, the OSX binary is code signed and thus can't be directly reproduced yet. 
Work on resolving both of these issues is ongoing.

# Reproducing a release

## Install Java

Because Sparrow bundles a Java runtime in the release binaries, it is essential to have the same version of Java installed when creating the release.
For v1.5.0 and later, the project will be using **AdoptOpenJdk jdk-16.0.1+9 Hotspot**.

Set AdoptOpenJDK version for command line installation:
```shell
ADOPTOPENJDK_MAJOR_VERSION=16
ADOPTOPENJDK_UNDERLINE_VERSION=16.0.1_9
ADOPTOPENJDK_PLUS_VERSION=16.0.1+9
ADOPTOPENJDK_FULL_VERSION=16.0.1+9-3
```

### Binaries from official site

It is available for all supported platforms from the [adoptopenjdk.net](https://adoptopenjdk.net/archive.html?variant=openjdk16&jvmVariant=hotspot).

### Binaries from GitHub

For reference, the downloads are as follows on the [adoptopenjdk github release page](https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/jdk-16.0.1+9):
- [Linux x64](https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1+9/OpenJDK16U-jdk_x64_linux_hotspot_16.0.1_9.tar.gz)
- [MacOS x64](https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1+9/OpenJDK16U-jdk_x64_mac_hotspot_16.0.1_9.tar.gz)
- [Windows x64](https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1+9/OpenJDK16U-jdk_x64_windows_hotspot_16.0.1_9.zip)

#### Download from terminal for *nix systems

Set your operating system [mac|linux|alpine-linux|aix]:
```shell
OPERATING_SYSTEM=linux
```

Set the building machine architecture:
linux=[x64|arm|aarch64|ppc64le|ppc64|s390x]
aix=[ppc64]
mac=[x64]
```shell
ARCHITECTURE=x64
```

Download AdoptOpenJDK to the `/tmp` folder:
```shell
wget -q --show-progress -P /tmp/ https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-${ADOPTOPENJDK_PLUS_VERSION}/OpenJDK16U-jdk_${ARCHITECTURE}_${OPERATING_SYSTEM}_hotspot_${ADOPTOPENJDK_UNDERLINE_VERSION}.tar.gz
wget -q --show-progress -P /tmp/  https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-${ADOPTOPENJDK_PLUS_VERSION}/OpenJDK16U-jdk_${ARCHITECTURE}_${OPERATING_SYSTEM}_hotspot_${ADOPTOPENJDK_UNDERLINE_VERSION}.tar.gz.sha256.txt
```

Check if the hash of the `tar.gz` is correct (*OK* is what you need):
```shell
sha256sum -c /tmp/OpenJDK16U-jdk_${ARCHITECTURE}_linux_hotspot_${ADOPTOPENJDK_UNDERLINE_VERSION}.tar.gz.sha256.txt
```

### Package manager

AdoptOpenJDK RPM and DEB packages are available on [adoptopenjdk.net latest install guide](https://adoptopenjdk.net/installation.html?variant=openjdk16&jvmVariant=hotspot#linux-pkg).

#### APT

Debian and Ubuntu:
```shell
sudo apt update -y
sudo apt-get install -y wget gnupg apt-transport-https
wget -q --show-progress https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public
gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --import public
gpg --no-default-keyring --keyring ./adoptopenjdk-keyring.gpg --export --output adoptopenjdk-archive-keyring.gpg
rm adoptopenjdk-keyring.gpg
sudo mv adoptopenjdk-archive-keyring.gpg /usr/share/keyrings
echo "deb [signed-by=/usr/share/keyrings/adoptopenjdk-archive-keyring.gpg] https://adoptopenjdk.jfrog.io/adoptopenjdk/deb $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/adoptopenjdk.list
sudo apt update -y
sudo apt-get install -y adoptopenjdk-${ADOPTOPENJDK_MAJOR_VERSION}-hotspot=${ADOPTOPENJDK_FULL_VERSION}
```

#### RPM

CentOS, RHEL and Fedora:
```shell
cat <<'EOF' > /etc/yum.repos.d/adoptopenjdk.repo
[AdoptOpenJDK]
name=AdoptOpenJDK
baseurl=http://adoptopenjdk.jfrog.io/adoptopenjdk/rpm/centos/$releasever/$basearch
enabled=1
gpgcheck=1
gpgkey=https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public
EOF
sudo yum install -y adoptopenjdk-${ADOPTOPENJDK_MAJOR_VERSION}-hotspot=${ADOPTOPENJDK_FULL_VERSION}
```

openSUSE and SLES:
```shell
sudo zypper ar -f http://adoptopenjdk.jfrog.io/adoptopenjdk/rpm/opensuse/15.0/$(uname -m) adoptopenjdk
sudo zypper install -y adoptopenjdk-${ADOPTOPENJDK_MAJOR_VERSION}-hotspot=${ADOPTOPENJDK_FULL_VERSION}
```

#### SDK

A alternative option for all platforms is to use the [sdkman.io](https://sdkman.io/) package manager.
See the installation [instructions here](https://sdkman.io/install).

First, install `zip` and `unzip`. Example on Debian/Ubuntu systems:
```shell
sudo apt-get install -y zip unzip
```

Installation on macOS, Linux, WSL, Cygwin, Solaris and FreeBSD (compatible with Bash and Zsh shells):
```shell
curl -sS "https://get.sdkman.io" | ${SHELL##*/}
. "$HOME/.sdkman/bin/sdkman-init.sh"
```

Installation on Windows with [git-scm](https://git-scm.com/download/win).

Install AdoptOpenJDK HostSpot:
```shell
sdk install java 16.0.1.hs-adpt
```

## Building the binaries

To complete the build the package below are required. Installation on Debian/Ubuntu systems:
```shell
sudo apt install -y rpm fakeroot binutils git wget curl gnupg tar diffutils
```

Set a specific release tag as follows:
```shell
GIT_TAG="1.5.0-beta1"
```

Clone the repository:

* without SSH credentials:
```shell
git clone --recursive --branch "${GIT_TAG}" https://github.com/sparrowwallet/sparrow.git
```

* with SSH credentials:
```shell
git clone --recursive --branch "${GIT_TAG}" git@github.com:sparrowwallet/sparrow.git
```

Thereafter, building should be straightforward:
```shell
cd sparrow
./gradlew jpackage
```

The binaries (and installers) will be placed in the `build/jpackage` folder.

### Verifying if the binaries are identical

Note: If you encounter any errors on the following steps, you should not procede until it is resolved.

Import the maintainer PGP public key (Fingerperint: *E946 1833 4C67 4B40*:
```shell
curl -sS https://keybase.io/craigraw/pgp_keys.asc | gpg --import
```

Download the binaries, manifest and signed manifest that are available on the [releases page](https://github.com/sparrowwallet/sparrow/releases) (will download in the `/tmp` folder):
```shell
wget -q --show-progress -P /tmp/ https://github.com/sparrowwallet/sparrow/releases/download/"${GIT_TAG}"/sparrow-"${GIT_TAG}"-manifest.txt
wget -q --show-progress -P /tmp/ https://github.com/sparrowwallet/sparrow/releases/download/"${GIT_TAG}"/sparrow-"${GIT_TAG}"-manifest.txt.asc
wget -q --show-progress -P /tmp/ https://github.com/sparrowwallet/sparrow/releases/download/"${GIT_TAG}"/sparrow-"${GIT_TAG}".tar.gz
```

Download the compressed binaries [tar.gz|zip]:
```shell
COMPRESSION_METHOD="tar.gz"
wget -q --show-progress -P /tmp/ https://github.com/sparrowwallet/sparrow/releases/download/"${GIT_TAG}"/sparrow-"${GIT_TAG}".${COMPRESSION_METHOD}

Verify the manifest authenticity (*Good signaure* is what you need):
```shell
gpg --verify /tmp/sparrow-${GIT_TAG}-manifest.txt.asc /tmp/sparrow-${GIT_TAG}-manifest.txt
```

Check if the hash of the `.tar.gz` is correct (*OK* is what you need):
```shell
sha256sum -c /tmp/sparrow-"${GIT_TAG}"-manifest.txt --ignore-missing
```

Extract the archive with:
* tar:
```shell
sudo tar -xf /tmp/sparrow-"${GIT_TAG}".tar.gz
```
* unzip:
```shell
sudo unzip -q /tmp/sparrow-"${GIT_TAG}".zip
```

Compare recursively the files in the `build/jpackage/Sparrow` folder against either the uncompressed releases. This command should have no output indicating that the two folders and all their contents are identical: 
```shell
diff -r build/jpackage/Sparrow /tmp/Sparrow
```
