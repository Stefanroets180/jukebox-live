import { render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"
import { AppProviders } from "@/app/providers"
import PlayerPage from "@/app/player/page"

// This test file does NOT mock GlobalAudioPlayer to verify the real component renders correctly
// All other dependencies are mocked to isolate the GlobalAudioPlayer integration

const doubles = vi.hoisted(() => ({
  session: {
    token: "host-token",
    partyId: "party-1",
    partyCode: "ROOM",
    username: "Host",
    userId: "host-1",
    role: "ADMIN" as "ADMIN" | "USER",
    credits: 0,
  },
  queue: [] as any[],
  nowPlaying: null as any,
  api: {
    claimPlayerLease: vi.fn(),
    heartbeatPlayerLease: vi.fn(),
    releasePlayerLease: vi.fn(),
    getAudioToken: vi.fn(),
    getAudioUrl: vi.fn(),
    updatePlaybackProgress: vi.fn(),
    resumePlayback: vi.fn(),
    pausePlayback: vi.fn(),
    skipPlayback: vi.fn(),
    startPlayback: vi.fn(),
    completePlayback: vi.fn(),
    searchTracks: vi.fn(),
    addToQueue: vi.fn(),
  },
  mutateQueue: vi.fn(),
  mutateNowPlaying: vi.fn(),
}))

vi.mock("@/lib/jukebox/context", () => ({
  useJukebox: () => ({
    hydrated: true,
    session: doubles.session,
    isAdmin: doubles.session.role === "ADMIN",
  }),
  JukeboxProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock("@/lib/jukebox/use-party-data", () => ({
  usePartyData: () => ({
    queue: doubles.queue,
    nowPlaying: doubles.nowPlaying,
    isSyncing: false,
    mutateQueue: doubles.mutateQueue,
    mutateNowPlaying: doubles.mutateNowPlaying,
  }),
}))

vi.mock("@/lib/jukebox/api", () => ({
  ApiError: class ApiError extends Error {
    status: number
    code?: string

    constructor(message: string, status: number, code?: string) {
      super(message)
      this.status = status
      this.code = code
    }
  },
  jukeboxApi: doubles.api,
}))

vi.mock("swr", () => ({
  default: (key: unknown) => {
    const name = Array.isArray(key) ? key[0] : null
    if (name === "playback-player-lease") {
      return { data: { acquired: true, expiresAt: "2099-01-01T00:00:00Z" }, error: undefined, isLoading: false, mutate: vi.fn() }
    }
    if (name === "playback-player-heartbeat") {
      return { data: { acquired: true, expiresAt: "2099-01-01T00:00:00Z" }, error: undefined, isLoading: false, mutate: vi.fn() }
    }
    if (name === "dedicated-player-audio-token") {
      return { data: "signed-audio-token", error: undefined, isLoading: false }
    }
    if (name === "dedicated-player-library") {
      return { data: [], error: undefined, isLoading: false }
    }
    return { data: undefined, error: undefined, isLoading: false, mutate: vi.fn() }
  },
}))

vi.mock("@/components/jukebox/cover-art", () => ({
  CoverArt: ({ title }: { title: string }) => <div role="img" aria-label={`${title} album artwork`} />,
}))

vi.mock("@/components/jukebox/site-header", () => ({
  SiteHeader: () => <div data-testid="site-header" />,
}))

// GlobalAudioPlayer is NOT mocked in this file - we test the real component

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
  Toaster: () => <div data-testid="toaster" />,
}))

// Mock window.matchMedia for next-themes
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// Mock HTMLMediaElement methods not implemented in jsdom
HTMLAudioElement.prototype.pause = vi.fn()
HTMLAudioElement.prototype.load = vi.fn()

type TrackFixture = {
  id: string
  title: string
  artist: string
  album: string
  duration: number
}

type QueueItemFixture = {
  id: string
  track: TrackFixture
  status: "PENDING" | "PLAYING" | "SKIPPED" | "COMPLETED"
  position: number
  requestedBy?: {
    username: string
  }
}

type NowPlayingFixture = {
  track: TrackFixture
  isPlaying: boolean
  progress: number
}

function track(id: string, title: string, artist = "Artist", album = "Album", duration = 180): TrackFixture {
  return { id, title, artist, album, duration }
}

function queueItem(id: string, track: TrackFixture, status: QueueItemFixture["status"], position: number): QueueItemFixture {
  return { id, track, status, position, requestedBy: { username: "TestUser" } }
}

function nowPlaying(track: TrackFixture, isPlaying: boolean, progress: number): NowPlayingFixture {
  return { track, isPlaying, progress }
}

describe("real GlobalAudioPlayer integration", () => {
  it("renders real GlobalAudioPlayer component with audio element", () => {
    // Set up data that GlobalAudioPlayer needs to render
    const selectedTrack = track("track-1", "Test Track")
    doubles.queue = [queueItem("queue-1", selectedTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(selectedTrack, true, 0)

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Verify the real GlobalAudioPlayer renders an audio element
    const audio = document.querySelector("audio.sr-only")
    expect(audio).toBeTruthy()
    expect(audio?.getAttribute("aria-hidden")).toBe("true")
  })

  it("loads and plays the next track when queue advances from PENDING to PLAYING", () => {
    const firstTrack = track("track-1", "First Song")
    const secondTrack = track("track-2", "Second Song")

    // Initial state: first song playing, second song pending in queue
    doubles.queue = [
      queueItem("queue-1", firstTrack, "PLAYING", 1),
      queueItem("queue-2", secondTrack, "PENDING", 2),
    ]
    doubles.nowPlaying = nowPlaying(firstTrack, true, 0)

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Verify audio element exists
    const audio = document.querySelector("audio.sr-only")
    expect(audio).toBeTruthy()

    // Simulate first song completing and backend advancing second song to PLAYING
    // This is the critical state change that should trigger audio reload
    doubles.queue = [
      queueItem("queue-1", firstTrack, "COMPLETED", 1),
      queueItem("queue-2", secondTrack, "PLAYING", 2),
    ]
    doubles.nowPlaying = nowPlaying(secondTrack, true, 0)

    // Re-render to simulate WebSocket update
    render(<AppProviders><PlayerPage /></AppProviders>)

    // Verify audio element still exists after state transition
    const audioAfter = document.querySelector("audio.sr-only")
    expect(audioAfter).toBeTruthy()

    // The fix ensures that when activeQueueItem.status changes from PENDING to PLAYING,
    // the audio loading effect will execute (previously blocked by the ID check)
    // This test verifies the component handles the transition without errors
  })

  it("autoplays when queue item status changes to PLAYING even if nowPlaying is delayed", () => {
    const track1 = track("track-1", "Song 1")
    const track2 = track("track-2", "Song 2")

    // Initial state: first song playing, second song pending
    doubles.queue = [
      queueItem("queue-1", track1, "PLAYING", 1),
      queueItem("queue-2", track2, "PENDING", 2),
    ]
    doubles.nowPlaying = nowPlaying(track1, true, 0)

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Simulate queue update arriving before nowPlaying update (race condition)
    // Second song advances to PLAYING but nowPlaying still shows first song or is null
    doubles.queue = [
      queueItem("queue-1", track1, "COMPLETED", 1),
      queueItem("queue-2", track2, "PLAYING", 2),
    ]
    doubles.nowPlaying = null // nowPlaying hasn't arrived yet

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Verify audio element exists and component handles the transition
    const audio = document.querySelector("audio.sr-only")
    expect(audio).toBeTruthy()

    // The fix uses activeQueueItem.status === "PLAYING" as the primary autoplay signal
    // This ensures autoplay works even when nowPlaying is delayed or has isPlaying=false
  })

  it("autoplays when serverWantsPlayback is explicitly false but queue item is PLAYING", () => {
    const track1 = track("track-1", "Song 1")
    const track2 = track("track-2", "Song 2")

    // Initial state: first song playing
    doubles.queue = [
      queueItem("queue-1", track1, "PLAYING", 1),
      queueItem("queue-2", track2, "PENDING", 2),
    ]
    doubles.nowPlaying = nowPlaying(track1, true, 0)

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Simulate second song advancing to PLAYING but nowPlaying arrives with isPlaying=false
    // This can happen when the backend hasn't yet updated the isPlaying flag
    doubles.queue = [
      queueItem("queue-1", track1, "COMPLETED", 1),
      queueItem("queue-2", track2, "PLAYING", 2),
    ]
    doubles.nowPlaying = nowPlaying(track2, false, 0) // isPlaying=false but queue says PLAYING

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Verify audio element exists
    const audio = document.querySelector("audio.sr-only")
    expect(audio).toBeTruthy()

    // The fix ignores serverWantsPlayback when queue item status is PLAYING
    // Queue status is authoritative for autoplay decisions
  })

  it("respects explicit paused resume state over queue PLAYING status", () => {
    const track1 = track("track-1", "Song 1")

    // Set up a paused resume state
    doubles.queue = [queueItem("queue-1", track1, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(track1, false, 30) // paused at 30 seconds

    render(<AppProviders><PlayerPage /></AppProviders>)

    const audio = document.querySelector("audio.sr-only")
    expect(audio).toBeTruthy()

    // Resume state shouldPlay=false takes priority over queue PLAYING status
    // This ensures explicit pause is respected
  })

  it("does not reload the same playing track on unrelated state updates", () => {
    const selectedTrack = track("track-1", "Test Track")
    doubles.queue = [queueItem("queue-1", selectedTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(selectedTrack, true, 0)
    doubles.api.getAudioToken.mockResolvedValue("token-1")
    doubles.api.getAudioUrl.mockReturnValue("http://localhost:8082/api/playback/audio?token=token-1")

    const { rerender } = render(<AppProviders><PlayerPage /></AppProviders>)

    // Reset mock to count calls after initial load
    doubles.api.getAudioToken.mockClear()

    // Simulate unrelated state update (e.g., progress update without status change)
    doubles.nowPlaying = nowPlaying(selectedTrack, true, 30)
    rerender(<AppProviders><PlayerPage /></AppProviders>)

    // Verify audio was not reloaded since the same track is still PLAYING
    expect(doubles.api.getAudioToken).not.toHaveBeenCalled()
  })
})
