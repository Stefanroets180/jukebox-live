package com.jukebox.live.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    
    @NotBlank(message = "Party code is required")
    @Pattern(regexp = "^[A-Z0-9]{6}$", message = "Party code must be 6 alphanumeric characters")
    private String partyCode;
    
    private Double guestLatitude;
    private Double guestLongitude;
    private Double locationAccuracy;
}
