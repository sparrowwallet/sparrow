# Define Ubuntu 22.04 LTS as base image
FROM ubuntu:22.04

# Set Sparrow version and expected PGP signature
ARG SPARROW_VERSION=1.7.0
ARG PGP_SIG=E94618334C674B40

# Update all packages and install requirements
RUN apt-get update \
    && apt-get upgrade -y
RUN DEBIAN_FRONTEND=noninteractive apt-get -y install --no-install-recommends curl \
    gpg \
    gpg-agent \
    wget \
    ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Switch to /tmp for verification and install
WORKDIR /tmp

# Detect and set architecture to properly download binaries
ARG TARGETARCH
RUN case ${TARGETARCH:-amd64} in \
    "arm64") SPARROW_ARCH="aarch64";; \
    "amd64") SPARROW_ARCH="x86_64";; \
    *) echo "Dockerfile does not support this platform"; exit 1 ;; \
    esac \
    # Download Sparrow Server binaries and verification assets
    && wget --quiet https://github.com/sparrowwallet/sparrow/releases/download/${SPARROW_VERSION}/sparrow-server-${SPARROW_VERSION}-${SPARROW_ARCH}.tar.gz \
    && wget --quiet https://github.com/sparrowwallet/sparrow/releases/download/${SPARROW_VERSION}/sparrow-${SPARROW_VERSION}-manifest.txt \
    && wget --quiet https://github.com/sparrowwallet/sparrow/releases/download/${SPARROW_VERSION}/sparrow-${SPARROW_VERSION}-manifest.txt.asc \
    && wget --quiet https://keybase.io/craigraw/pgp_keys.asc \
    # GPG verify, sha256sum verify, and unpack Sparrow Server binaries
    && gpg --import pgp_keys.asc \
    && gpg --status-fd 1 --verify sparrow-${SPARROW_VERSION}-manifest.txt.asc \
    | grep -q "GOODSIG ${PGP_SIG}" \
    || exit 1 \
    && sha256sum --check sparrow-${SPARROW_VERSION}-manifest.txt --ignore-missing || exit 1 \
    && tar xf sparrow-server-${SPARROW_VERSION}-${SPARROW_ARCH}.tar.gz -C /opt

# Add user and setup directories for Sparrow
RUN useradd -ms /bin/bash sparrow
USER sparrow

# Switch to home directory
WORKDIR /home/sparrow

# Run Sparrow in terminal mode
CMD ["/opt/Sparrow/bin/Sparrow", "-t"]
