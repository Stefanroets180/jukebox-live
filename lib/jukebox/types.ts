export type UserRole = "USER" | "ADMIN"

export type GeoLocation = {
  latitude: number
  longitude: number
  accuracy: number
}

export type AuthSession = {
  token: string
  partyId: string | null
  partyCode: string
  username: string
  userId?: string
  role: UserRole
  credits: number
}

export type Track = {
  id: string
  title: string
  artist: string
  album?: string | null
  durationSeconds?: number | null
  albumArtPath?: string | null
  genre?: string | null
  musicBrainzReleaseId?: string | null
  artworkLookupStatus?: ArtworkLookupStatus
  artworkSourceUrl?: string | null
}

export type ArtworkLookupStatus = "NOT_LOOKED_UP" | "FOUND" | "NOT_FOUND" | "AMBIGUOUS" | "ERROR"

export type ArtworkCandidate = {
  recordingTitle: string
  releaseId: string
  releaseTitle: string
  artist: string
  releaseDate: string | null
  country: string | null
  score: number
  coverArtUrl: string
  sourceUrl: string
}

export type ArtworkLookupResult = {
  trackId: string
  status: ArtworkLookupStatus
  message: string
  albumArtPath: string | null
  musicBrainzReleaseId: string | null
  artworkSourceUrl: string | null
  candidates: ArtworkCandidate[]
}

export type ArtworkBacklog = {
  tracks: Track[]
  total: number
  offset: number
  limit: number
}

export type PlayerLeaseResponse = {
  acquired: boolean
  expiresAt: string | null
  message: string
}

export type QueueItem = {
  id: string
  track: Track
  requestedBy: { username: string }
  status: "PENDING" | "PLAYING" | "COMPLETED" | "SKIPPED" | string
  position: number
  createdAt?: string
}

export type NowPlaying = {
  track: Track
  progressSeconds: number
  isPlaying: boolean
  requestedBy?: { username: string }
}

export type PartyGuest = {
  username: string
  credits: number
  distanceFromParty: number
  joinedAt: string
}

export type CreatedParty = {
  id: string
  code: string
  partyIp: string
  latitude: number
  longitude: number
  isActive: boolean
}

export type ApiEnvelopeError = {
  code?: string
  message?: string
  error?: string
}

export function getCoverImageSrc(
  albumArtPath: string | null | undefined,
  fallback = "",
) {
  if (!albumArtPath) return fallback
  if (albumArtPath.startsWith("/") && !albumArtPath.startsWith("//")) {
    return albumArtPath
  }

  try {
    const url = new URL(albumArtPath)
    if (
      url.protocol === "https:" &&
      url.hostname === "coverartarchive.org" &&
      /^\/release\/[0-9a-f-]+\/front-500$/i.test(url.pathname)
    ) {
      return url.toString()
    }
  } catch {
    return fallback
  }

  return fallback
}

export function formatDuration(seconds?: number | null) {
  if (seconds == null || !Number.isFinite(seconds)) return "—:—"
  const minutes = Math.floor(seconds / 60)
  const remainder = Math.floor(seconds % 60)
  return `${minutes}:${String(remainder).padStart(2, "0")}`
}
