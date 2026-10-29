package com.jukebox.live.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ScanLibraryRequest {
    @NotBlank(message = "Library path is required")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\\\/:.\\s]+$", message = "Invalid library path format")
    private String path;
}
