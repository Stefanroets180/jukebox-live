package com.jukebox.live.service;

import org.springframework.stereotype.Service;

@Service
public class GeofenceService {

    public double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            throw new IllegalArgumentException("Latitude and longitude values cannot be null");
        }
        if (!isValidCoordinate(lat1) || !isValidCoordinate(lon1) || !isValidCoordinate(lat2) || !isValidCoordinate(lon2)) {
            throw new IllegalArgumentException("Invalid latitude or longitude values");
        }
        final int R = 6371000; // Earth radius in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public boolean isWithinGeofence(Double guestLat, Double guestLng, Double partyLat, Double partyLng, Double radiusMeters) {
        if (partyLat == null || partyLng == null) {
            // Party has no geofence configured - allow all guests
            return true;
        }
        if (guestLat == null || guestLng == null) {
            // Guest has no location - deny
            return false;
        }
        double distance = calculateDistance(guestLat, guestLng, partyLat, partyLng);
        return distance <= radiusMeters;
    }

    private boolean isValidCoordinate(Double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
