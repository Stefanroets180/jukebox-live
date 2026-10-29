package com.jukebox.live.service;

public class ArtworkProviderException extends RuntimeException {
    public ArtworkProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArtworkProviderException(String message) {
        super(message);
    }
}
