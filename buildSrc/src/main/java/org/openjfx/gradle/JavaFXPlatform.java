/*
 * Copyright (c) 2018, Gluon
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjfx.gradle;

import com.google.gradle.osdetector.OsDetector;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.awt.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum JavaFXPlatform {

    LINUX("linux", "linux-x86_64"),
    LINUX_MONOCLE("linux-monocle", "linux-x86_64-monocle"),
    LINUX_AARCH64("linux-aarch64", "linux-aarch_64"),
    LINUX_AARCH64_MONOCLE("linux-aarch64-monocle", "linux-aarch_64-monocle"),
    WINDOWS("win", "windows-x86_64"),
    WINDOWS_MONOCLE("win-monocle", "windows-x86_64-monocle"),
    OSX("mac", "osx-x86_64"),
    OSX_MONOCLE("mac-monocle", "osx-x86_64-monocle"),
    OSX_AARCH64("mac-aarch64", "osx-aarch_64"),
    OSX_AARCH64_MONOCLE("mac-aarch64-monocle", "osx-aarch_64-monocle");

    private final String classifier;
    private final String osDetectorClassifier;

    JavaFXPlatform( String classifier, String osDetectorClassifier ) {
        this.classifier = classifier;
        this.osDetectorClassifier = osDetectorClassifier;
    }

    public String getClassifier() {
        return classifier;
    }

    public static JavaFXPlatform detect(Project project) {

        String osClassifier = project.getExtensions().getByType(OsDetector.class).getClassifier();

        if("true".equals(System.getProperty("java.awt.headless"))) {
            osClassifier += "-monocle";
        }

        for ( JavaFXPlatform platform: values()) {
            if ( platform.osDetectorClassifier.equals(osClassifier)) {
                return platform;
            }
        }

        String supportedPlatforms = Arrays.stream(values())
                .map(p->p.osDetectorClassifier)
                .collect(Collectors.joining("', '", "'", "'"));

        throw new GradleException(
            String.format(
                    "Unsupported JavaFX platform found: '%s'! " +
                    "This plugin is designed to work on supported platforms only." +
                    "Current supported platforms are %s.", osClassifier, supportedPlatforms )
        );

    }
}
