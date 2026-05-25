{
  description = "Sparrow Wallet Nix Flake (Development Shell only, for now)";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

    flake-parts = {
      url = "github:hercules-ci/flake-parts";
      inputs.nixpkgs-lib.follows = "nixpkgs";
    };

    devshell = {
      url = "github:numtide/devshell";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = inputs @ { flake-parts, devshell, ... }:
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = [ "x86_64-linux" "x86_64-darwin" "aarch64-linux" "aarch64-darwin" ];

      perSystem = { config, self', inputs', pkgs, system, lib, ... }: let
        inherit (pkgs) stdenv;
      in {
        # define default devshell
        devShells.default = pkgs.mkShell {
          packages = with pkgs ; [
                jdk23                # This JDK will be in PATH
                (gradle.override {   # Gradle 8.x (Nix package) runs using an internally-linked JDK
                    java = jdk23;    # Run Gradle with this JDK
                })
            ];
        };
      };
    };
}
