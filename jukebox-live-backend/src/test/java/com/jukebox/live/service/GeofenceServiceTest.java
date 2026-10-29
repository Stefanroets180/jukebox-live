package com.jukebox.live.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GeofenceServiceTest {

    private final GeofenceService geofenceService = new GeofenceService();

    @Test
    public void testIsWithinGeofence_WithNullPartyCoordinates_ShouldAllowAllGuests() {
        // Party has no geofence configured - should allow all guests
        assertTrue(geofenceService.isWithinGeofence(40.7128, -74.0060, null, null, 100.0));
        assertTrue(geofenceService.isWithinGeofence(null, null, null, null, 100.0));
    }

    @Test
    public void testIsWithinGeofence_WithNullGuestCoordinates_ShouldDeny() {
        // Guest has no location but party has geofence - should deny
        assertFalse(geofenceService.isWithinGeofence(null, null, 40.7128, -74.0060, 100.0));
    }

    @Test
    public void testIsWithinGeofence_WithinRadius_ShouldAllow() {
        // Guest within 100m of party
        assertTrue(geofenceService.isWithinGeofence(40.7129, -74.0061, 40.7128, -74.0060, 100.0));
    }

    @Test
    public void testIsWithinGeofence_OutsideRadius_ShouldDeny() {
        // Guest outside 100m of party
        assertFalse(geofenceService.isWithinGeofence(40.7228, -74.0160, 40.7128, -74.0060, 100.0));
    }
}
