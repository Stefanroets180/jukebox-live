import type {
  AuthSession,
  CreatedParty,
  GeoLocation,
  NowPlaying,
  PartyGuest,
  QueueItem,
  Track,
  ArtworkBacklog,
  ArtworkCandidate,
  ArtworkLookupResult,
  PlayerLeaseResponse,
} from "@/lib/jukebox/types"

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.code = code
  }
}

export function getApiBaseUrl() {
  const configured = process.env.NEXT_PUBLIC_API_URL?.trim()
  if (configured) return configured.replace(/\/+$/, "")

  if (typeof window !== "undefined") {
    return `${window.location.protocol}//${window.location.hostname}:8082`
  }

  return "http://localhost:8082"
}

function sessionHeaders(session?: AuthSession) {
  if (!session) return {}

  const headers: Record<string, string> = {
    Authorization: `Bearer ${session.token}`,
  }

  // Include party ID header for any session with a partyId (both guest and admin)
  if (session.partyId) {
    headers["X-Party-ID"] = session.partyId
  }

  return headers
}

async function request<T>(
  path: string,
  options: {
    method?: "GET" | "POST" | "DELETE"
    body?: unknown
    session?: AuthSession
  } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" }
  Object.assign(headers, sessionHeaders(options.session))
  if (options.body !== undefined) headers["Content-Type"] = "application/json"

  let response: Response
  try {
    response = await fetch(`${getApiBaseUrl()}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      cache: "no-store",
    })
  } catch {
    throw new ApiError(
      "The host server could not be reached. Make sure you are on the party Wi-Fi and try again.",
      0,
      "HOST_UNREACHABLE",
    )
  }

  const raw = await response.text()
  let payload: unknown = null
  if (raw) {
    try {
      payload = JSON.parse(raw)
    } catch {
      payload = raw
    }
  }

  if (!response.ok) {
    const error = payload && typeof payload === "object" ? payload as Record<string, unknown> : null
    const message = typeof error?.message === "string"
      ? error.message
      : typeof error?.error === "string"
        ? error.error
        : typeof payload === "string" && payload
          ? payload
          : `Request failed with status ${response.status}`
    const code = typeof error?.code === "string" ? error.code : undefined
    throw new ApiError(message, response.status, code)
  }

  return payload as T
}

export const jukeboxApi = {
  getSetupStatus() {
    return request<{ available: boolean }>("/api/auth/setup-status")
  },

  createFirstAdmin(input: { username: string; password: string }) {
    return request<{ token: string; userId: string; partyId: string | null; credits: number }>("/api/auth/create-first-admin", {
      method: "POST",
      body: input,
    })
  },

  register(input: { username: string; password: string }) {
    return request<{ token: string; user: { id: string; username: string; credits: number; role: string } }>("/api/auth/register", {
      method: "POST",
      body: input,
    })
  },

  login(input: {
    username: string
    password: string
    partyCode: string
  }) {
    return request<{ token: string; userId: string; partyId: string; credits: number }>(
      "/api/auth/login",
      {
        method: "POST",
        body: {
          username: input.username,
          password: input.password,
          partyCode: input.partyCode,
        },
      },
    )
  },

  adminLogin(input: { username: string; password: string }) {
    return request<{ token: string; userId: string; partyId: string | null; credits: number }>("/api/auth/admin-login", {
      method: "POST",
      body: input,
    })
  },

  searchTracks(query: string, session?: AuthSession) {
    const params = new URLSearchParams({ q: query })
    return request<Track[]>(`/api/library/search?${params.toString()}`, { session })
  },

  getQueue(session: AuthSession) {
    if (!session.partyId) throw new Error("Party ID is required for this operation")
    const params = new URLSearchParams({ partyId: session.partyId })
    return request<QueueItem[]>(`/api/queue?${params.toString()}`, { session })
  },

  getNowPlaying(session: AuthSession) {
    if (!session.partyId) throw new Error("Party ID is required for this operation")
    const params = new URLSearchParams({ partyId: session.partyId })
    return request<NowPlaying | null>(`/api/playback/now-playing?${params.toString()}`, { session })
  },

  getGuests(session: AuthSession) {
    if (!session.partyId) throw new Error("Party ID is required for this operation")
    return request<PartyGuest[]>(`/api/parties/${encodeURIComponent(session.partyId)}/guests`, { session })
  },

  getLibraryStats(session?: AuthSession) {
    return request<{ count: number }>("/api/library/stats", { session })
  },

  addToQueue(session: AuthSession, trackId: string) {
    return request<{ id: string; trackId: string; status: string; position: number; cost: number }>(
      "/api/queue/add",
      { method: "POST", body: { trackId }, session },
    )
  },

  skipQueueItem(session: AuthSession, queueId: string) {
    return request<void>(`/api/queue/${encodeURIComponent(queueId)}/skip`, {
      method: "DELETE",
      session,
    })
  },

  startPlayback(session: AuthSession, partyId: string) {
    const params = new URLSearchParams({ partyId })
    return request<void>(`/api/playback/start?${params.toString()}`, {
      method: "POST",
      session,
    })
  },

  completePlayback(session: AuthSession, partyId: string, queueItemId: string) {
    const params = new URLSearchParams({ partyId, queueItemId })
    return request<void>(`/api/playback/complete?${params.toString()}`, {
      method: "POST",
      session,
    })
  },

  skipPlayback(session: AuthSession, partyId: string) {
    const params = new URLSearchParams({ partyId })
    return request<void>(`/api/playback/skip?${params.toString()}`, {
      method: "POST",
      session,
    })
  },

  claimPlayerLease(session: AuthSession, partyId: string, playerInstanceId: string) {
    const params = new URLSearchParams({ partyId })
    return request<PlayerLeaseResponse>(`/api/playback/player-lease/acquire?${params}`, {
      method: "POST",
      body: { playerInstanceId },
      session,
    })
  },

  heartbeatPlayerLease(session: AuthSession, partyId: string, playerInstanceId: string) {
    const params = new URLSearchParams({ partyId })
    return request<PlayerLeaseResponse>(`/api/playback/player-lease/heartbeat?${params}`, {
      method: "POST",
      body: { playerInstanceId },
      session,
    })
  },

  releasePlayerLease(session: AuthSession, partyId: string, playerInstanceId: string) {
    const params = new URLSearchParams({ partyId })
    return request<void>(`/api/playback/player-lease/release?${params}`, {
      method: "POST",
      body: { playerInstanceId },
      session,
    })
  },

  async getAudioToken(session: AuthSession, queueId: string, partyId: string, playerInstanceId: string) {
    const params = new URLSearchParams({ queueItemId: queueId, partyId, playerInstanceId })
    return request<string>(`/api/playback/audio-token?${params.toString()}`, {
      method: "GET",
      session,
    })
  },

  getAudioUrl(session: AuthSession, queueId: string, audioToken: string, partyId: string, playerInstanceId: string) {
    const params = new URLSearchParams({ queueItemId: queueId, partyId, playerInstanceId, token: audioToken })
    return `${getApiBaseUrl()}/api/playback/audio?${params.toString()}`
  },

  updatePlaybackProgress(
    session: AuthSession,
    partyId: string,
    playerInstanceId: string,
    queueItemId: string,
    progressSeconds: number,
  ) {
    const params = new URLSearchParams({ partyId })
    return request<void>(`/api/playback/progress?${params}`, {
      method: "POST",
      body: { playerInstanceId, queueItemId, progressSeconds },
      session,
    })
  },

  pausePlayback(session: AuthSession, partyId: string, playerInstanceId: string) {
    const params = new URLSearchParams({ partyId })
    return request<void>(`/api/playback/pause?${params}`, {
      method: "POST",
      body: { playerInstanceId },
      session,
    })
  },

  resumePlayback(session: AuthSession, partyId: string, playerInstanceId: string) {
    const params = new URLSearchParams({ partyId })
    return request<void>(`/api/playback/resume?${params}`, {
      method: "POST",
      body: { playerInstanceId },
      session,
    })
  },

  getMissingArtwork(session: AuthSession, offset = 0, limit = 20) {
    const params = new URLSearchParams({ offset: String(offset), limit: String(limit) })
    return request<ArtworkBacklog>(`/api/library/artwork/missing?${params}`, { session })
  },

  lookupArtwork(session: AuthSession, trackId: string, retry = false) {
    const params = new URLSearchParams({ retry: String(retry) })
    return request<ArtworkLookupResult>(`/api/library/artwork/${encodeURIComponent(trackId)}/lookup?${params}`, {
      method: "POST",
      session,
    })
  },

  getArtworkCandidates(session: AuthSession, trackId: string) {
    return request<ArtworkLookupResult>(`/api/library/artwork/${encodeURIComponent(trackId)}/candidates`, { session })
  },

  selectArtwork(session: AuthSession, trackId: string, releaseId: string) {
    return request<ArtworkLookupResult>(`/api/library/artwork/${encodeURIComponent(trackId)}/select`, {
      method: "POST",
      body: { releaseId },
      session,
    })
  },

  grantCredits(session: AuthSession, username: string, amount: number) {
    return request<void>("/api/admin/credits/grant", {
      method: "POST",
      body: { username, amount },
      session,
    })
  },

  scanLibrary(session: AuthSession, path: string) {
    return request<{ scannedCount: number }>("/api/library/scan", {
      method: "POST",
      body: { path },
      session,
    })
  },

  createParty(
    session: AuthSession,
    input: { hostLatitude: number | null; hostLongitude: number | null; musicLibraryPath: string },
  ) {
    return request<CreatedParty>("/api/parties/create", {
      method: "POST",
      body: input,
      session,
    })
  },
}
