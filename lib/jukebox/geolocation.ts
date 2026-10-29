import type { GeoLocation } from "@/lib/jukebox/types"

export function getBrowserLocation(): Promise<GeoLocation> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      reject(new Error("Location services are not available in this browser."))
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => resolve({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: position.coords.accuracy,
      }),
      (error) => {
        const message = error.code === error.PERMISSION_DENIED
          ? "Allow location access in your browser to verify your location."
          : error.code === error.TIMEOUT
            ? "We could not confirm your location in time. Try again near the host."
            : "Your location could not be verified. Check your browser settings and try again."
        reject(new Error(message))
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 },
    )
  })
}
