package com.sparrowwallet.sparrow.net.http.client;

import java.util.Objects;

public class HttpUsage {
    public static final HttpUsage DEFAULT = new HttpUsage("Default");

    private final String name;

    public HttpUsage(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        HttpUsage httpUsage = (HttpUsage) o;
        return Objects.equals(name, httpUsage.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
