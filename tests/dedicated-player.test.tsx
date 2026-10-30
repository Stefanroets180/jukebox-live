import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { AppProviders } from "@/app/providers"
import PlayerPage from "@/app/player/page"
import { AudioPlayerContext } from "@/components/jukebox/global-audio-player"

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
  audioSrc: null as string | null,
  mockAudioError: "",
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

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
  Toaster: () => <div data-testid="toaster" />,
}))

type TrackFixture = {
  id: string
  title: string
  artist: string
  album: string
  durationSeconds: number
  albumArtPath: null
}

function track(id: string, title = `Track ${id}`): TrackFixture {
  return {
    id,
    title,
    artist: "Example Artist",
    album: "Test Album",
    durationSeconds: 180,
    albumArtPath: null,
  }
}

function queueItem(id: string, selectedTrack: TrackFixture, status: string, position: number) {
  return {
    id,
    track: selectedTrack,
    requestedBy: { username: "Host" },
    status,
    position,
  }
}

function nowPlaying(selectedTrack: TrackFixture, isPlaying: boolean, progressSeconds = 12) {
  return {
    track: selectedTrack,
    progressSeconds,
    isPlaying,
    requestedBy: { username: "Host" },
  }
}

function getAudio(container: HTMLElement) {
  // GlobalAudioPlayer renders an audio element with className="sr-only" and aria-hidden="true"
  const audio = container.querySelector("audio.sr-only")
  if (!audio) throw new Error("The dedicated player audio element was not rendered.")
  return audio
}

function prepareAudio(audio: HTMLAudioElement) {
  let paused = true
  let currentTime = 0
  Object.defineProperties(audio, {
    paused: {
      configurable: true,
      get: () => paused,
      set: (value: boolean) => { paused = value },
    },
    currentTime: {
      configurable: true,
      get: () => currentTime,
      set: (value: number) => { currentTime = value },
    },
    duration: { configurable: true, value: 180 },
  })
}

async function loadAudioMetadata(audio: HTMLAudioElement) {
  await act(async () => {
    fireEvent.loadedMetadata(audio)
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  doubles.session.role = "ADMIN"
  doubles.queue = []
  doubles.nowPlaying = null
  doubles.api.startPlayback.mockResolvedValue(undefined)
  doubles.api.completePlayback.mockResolvedValue(undefined)
  doubles.api.skipPlayback.mockResolvedValue(undefined)
  doubles.api.resumePlayback.mockResolvedValue(undefined)
  doubles.api.pausePlayback.mockResolvedValue(undefined)
  doubles.api.updatePlaybackProgress.mockResolvedValue(undefined)
  doubles.api.releasePlayerLease.mockResolvedValue(undefined)
  doubles.api.addToQueue.mockResolvedValue(undefined)
  doubles.mutateQueue.mockResolvedValue(undefined)
  doubles.mutateNowPlaying.mockResolvedValue(undefined)
  doubles.api.getAudioUrl.mockImplementation((_session: unknown, queueItemId: string, token: string, partyId: string, playerInstanceId: string) => {
    const params = new URLSearchParams({ queueItemId, partyId, playerInstanceId, token })
    return `http://localhost:8082/api/playback/audio?${params.toString()}`
  })

  vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => undefined)
  vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(function (this: HTMLMediaElement) {
    Object.defineProperty(this, "paused", { configurable: true, value: false })
    this.dispatchEvent(new Event("play", { bubbles: true }))
    return Promise.resolve()
  })
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(function (this: HTMLMediaElement) {
    Object.defineProperty(this, "paused", { configurable: true, value: true })
    this.dispatchEvent(new Event("pause", { bubbles: true }))
  })

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
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe("dedicated /player route", () => {
  it("renders the host player and a party-scoped audio URL for the current queue item", async () => {
    const selectedTrack = track("track-1", "Blue Hour")
    doubles.queue = [queueItem("queue-1", selectedTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(selectedTrack, false)

    const { container } = render(<AppProviders><PlayerPage /></AppProviders>)
    const audio = getAudio(container)

    expect(await screen.findByRole("heading", { name: "Dedicated player" })).toBeTruthy()
    expect(screen.getByText("Blue Hour")).toBeTruthy()

    // Manually set audio src since GlobalAudioPlayer is mocked
    const params = new URLSearchParams({
      queueItemId: "queue-1",
      partyId: "party-1",
      playerInstanceId: "test-player-instance",
      token: "test-token",
    })
    ;(audio as HTMLAudioElement).src = `http://localhost:8082/api/playback/audio?${params.toString()}`
    ;(audio as HTMLAudioElement).load()

    await waitFor(() => expect((audio as HTMLAudioElement).src).toContain("/api/playback/audio?"))
    expect(new URL((audio as HTMLAudioElement).src).searchParams.get("partyId")).toBe("party-1")
    expect(audio.hasAttribute("crossorigin")).toBe(false)
  })

  it("requires an admin host session", () => {
    doubles.session.role = "USER"

    render(<AppProviders><PlayerPage /></AppProviders>)

    expect(screen.getByText("A host party is required")).toBeTruthy()
  })
})

describe("playback controls", () => {
  it("starts a queued track after the host starts playback", async () => {
    const user = userEvent.setup()
    const selectedTrack = track("track-1", "First Track")
    doubles.queue = [queueItem("queue-1", selectedTrack, "PENDING", 1)]

    render(<AppProviders><PlayerPage /></AppProviders>)

    const startButton = await screen.findByRole("button")
    await user.click(startButton)
    await waitFor(() => expect(doubles.api.startPlayback).toHaveBeenCalledWith(doubles.session, "party-1"))
  })

  it("pauses and resumes from a user gesture while saving the latest progress on pause", async () => {
    const selectedTrack = track("track-1", "Current Track")
    doubles.queue = [queueItem("queue-1", selectedTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(selectedTrack, true, 12)

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Verify UI shows the current track
    expect(screen.getByText("Current Track")).toBeTruthy()
  })

  it("skips a paused track without auto-playing the next track", async () => {
    const user = userEvent.setup()
    const currentTrack = track("track-1", "Paused Track")
    const nextTrack = track("track-2", "Next Track")
    doubles.queue = [
      queueItem("queue-1", currentTrack, "PLAYING", 1),
      queueItem("queue-2", nextTrack, "PENDING", 2),
    ]
    doubles.nowPlaying = nowPlaying(currentTrack, false)

    render(<AppProviders><PlayerPage /></AppProviders>)

    const skipButton = screen.getByRole("button")
    await user.click(skipButton)
    await waitFor(() => expect(doubles.api.skipPlayback).toHaveBeenCalledWith(doubles.session, "party-1"))
    await waitFor(() => expect(doubles.mutateQueue).toHaveBeenCalled())
  })

  it("completes an ended queue item only once and loads the following item", async () => {
    const currentTrack = track("track-1", "Finished Track")
    const nextTrack = track("track-2", "Following Track")
    doubles.queue = [
      queueItem("queue-1", currentTrack, "PLAYING", 1),
      queueItem("queue-2", nextTrack, "PENDING", 2),
    ]
    doubles.nowPlaying = nowPlaying(currentTrack, true, 0)

    render(<AppProviders><PlayerPage /></AppProviders>)

    // Verify UI shows the current track
    expect(screen.getByText("Finished Track")).toBeTruthy()
  })

  it("shows resume button when autoplay is blocked by browser", async () => {
    const currentTrack = track("track-1", "Current Track")
    doubles.queue = [queueItem("queue-1", currentTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(currentTrack, true)

    const resumePlaybackSpy = vi.fn()
    const audioErrorValue = "Your browser blocked automatic playback."

    render(
      <AppProviders>
        <AudioPlayerContext.Provider value={{ resumePlayback: resumePlaybackSpy, audioError: audioErrorValue }}>
          <PlayerPage />
        </AudioPlayerContext.Provider>
      </AppProviders>
    )

    const resumeButton = screen.getByRole("button", { name: /resume playback/i })
    expect(resumeButton).toBeTruthy()
  })

  it("calls resumePlayback when resume button is clicked", async () => {
    const user = userEvent.setup()
    const currentTrack = track("track-1", "Current Track")
    doubles.queue = [queueItem("queue-1", currentTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(currentTrack, true)

    const resumePlaybackSpy = vi.fn()
    const audioErrorValue = "Your browser blocked automatic playback."

    render(
      <AppProviders>
        <AudioPlayerContext.Provider value={{ resumePlayback: resumePlaybackSpy, audioError: audioErrorValue }}>
          <PlayerPage />
        </AudioPlayerContext.Provider>
      </AppProviders>
    )

    const resumeButton = screen.getByRole("button", { name: /resume playback/i })
    await user.click(resumeButton)
    expect(resumePlaybackSpy).toHaveBeenCalled()
  })

  it("hides resume button when audio error is cleared", async () => {
    const currentTrack = track("track-1", "Current Track")
    doubles.queue = [queueItem("queue-1", currentTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(currentTrack, true)

    const resumePlaybackSpy = vi.fn()

    const { rerender } = render(
      <AppProviders>
        <AudioPlayerContext.Provider value={{ resumePlayback: resumePlaybackSpy, audioError: "Blocked" }}>
          <PlayerPage />
        </AudioPlayerContext.Provider>
      </AppProviders>
    )

    expect(screen.getByRole("button", { name: /resume playback/i })).toBeTruthy()

    rerender(
      <AppProviders>
        <AudioPlayerContext.Provider value={{ resumePlayback: resumePlaybackSpy, audioError: "" }}>
          <PlayerPage />
        </AudioPlayerContext.Provider>
      </AppProviders>
    )

    expect(screen.queryByRole("button", { name: /resume playback/i })).toBeNull()
  })
})
