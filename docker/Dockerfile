# Define Ubuntu 22.04 LTS as base image
FROM ubuntu:22.04

# Set Sparrow version and expected PGP signature
ARG SPARROW_VERSION=1.7.0
ARG PGP_SIG=E94618334C674B40

# Update all packages and install requirements
RUN apt-get update \
    && apt-get upgrade -y \
    && DEBIAN_FRONTEND=noninteractive apt-get -y install --no-install-recommends curl \
    gpg \
    gpg-agent

# Download Sparrow Server binaries and verification assets
ADD https://github.com/sparrowwallet/sparrow/releases/download/${SPARROW_VERSION}/sparrow-server-${SPARROW_VERSION}-x86_64.tar.gz /tmp
ADD https://github.com/sparrowwallet/sparrow/releases/download/${SPARROW_VERSION}/sparrow-${SPARROW_VERSION}-manifest.txt /tmp
ADD https://github.com/sparrowwallet/sparrow/releases/download/${SPARROW_VERSION}/sparrow-${SPARROW_VERSION}-manifest.txt.asc /tmp
ADD https://keybase.io/craigraw/pgp_keys.asc /tmp

# Switch to /tmp for verification and install
WORKDIR /tmp

# GPG verify, sha256sum verify, and unpack Sparrow Server binaries
RUN gpg --import pgp_keys.asc \
    && gpg --status-fd 1 --verify sparrow-${SPARROW_VERSION}-manifest.txt.asc \
    | grep -q "GOODSIG ${PGP_SIG}" \
    || exit 1 \
    && sha256sum --check sparrow-1.7.0-manifest.txt --ignore-missing || exit 1 \
    && tar xvf sparrow-server-${SPARROW_VERSION}-x86_64.tar.gz -C /opt

# Add user and setup directories for Sparrow
RUN useradd -ms /bin/bash sparrow
USER sparrow

# Switch to home directory
WORKDIR /home/sparrow

# Run Sparrow in terminal mode
CMD ["/opt/Sparrow/bin/Sparrow", "-t"]
