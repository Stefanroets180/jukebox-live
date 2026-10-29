import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import PlayerPage from "@/app/player/page"

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
  const audio = container.querySelector("audio")
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

    const { container } = render(<PlayerPage />)
    const audio = getAudio(container)

    expect(await screen.findByRole("heading", { name: "Dedicated player" })).toBeTruthy()
    expect(screen.getByText("Blue Hour")).toBeTruthy()
    await waitFor(() => expect(audio.src).toContain("/api/playback/audio?"))
    expect(new URL(audio.src).searchParams.get("partyId")).toBe("party-1")
    expect(audio.hasAttribute("crossorigin")).toBe(false)
  })

  it("requires an admin host session", () => {
    doubles.session.role = "USER"

    render(<PlayerPage />)

    expect(screen.getByText("A host party is required")).toBeTruthy()
  })
})

describe("playback controls", () => {
  it("starts a queued track after the host starts playback", async () => {
    const user = userEvent.setup()
    const selectedTrack = track("track-1", "First Track")
    doubles.queue = [queueItem("queue-1", selectedTrack, "PENDING", 1)]

    const view = render(<PlayerPage />)
    const startButton = await screen.findByRole("button", { name: "Start queued track" })
    await user.click(startButton)
    await waitFor(() => expect(doubles.api.startPlayback).toHaveBeenCalledWith(doubles.session, "party-1"))

    doubles.queue = [queueItem("queue-1", selectedTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(selectedTrack, true, 0)
    view.rerender(<PlayerPage />)
    const audio = getAudio(view.container)
    await waitFor(() => expect(audio.src).toContain("queueItemId=queue-1"))
    prepareAudio(audio)
    await loadAudioMetadata(audio)

    await waitFor(() => expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(1))
  })

  it("pauses and resumes from a user gesture while saving the latest progress on pause", async () => {
    const user = userEvent.setup()
    const selectedTrack = track("track-1", "Current Track")
    doubles.queue = [queueItem("queue-1", selectedTrack, "PLAYING", 1)]
    doubles.nowPlaying = nowPlaying(selectedTrack, false, 12)

    const { container } = render(<PlayerPage />)
    const audio = getAudio(container)
    await waitFor(() => expect(audio.src).toContain("queueItemId=queue-1"))
    prepareAudio(audio)
    await loadAudioMetadata(audio)

    await user.click(screen.getByRole("button", { name: "Resume playback" }))
    await waitFor(() => expect(doubles.api.resumePlayback).toHaveBeenCalledWith(doubles.session, "party-1", expect.any(String)))
    expect(audio.paused).toBe(false)

    audio.currentTime = 37.8
    fireEvent.timeUpdate(audio)
    expect(screen.getByText("0:37")).toBeTruthy()

    await user.click(await screen.findByRole("button", { name: "Pause playback" }))
    await waitFor(() => expect(doubles.api.pausePlayback).toHaveBeenCalled())
    await waitFor(() => expect(doubles.api.updatePlaybackProgress).toHaveBeenCalledWith(
      doubles.session,
      "party-1",
      expect.any(String),
      "queue-1",
      37,
    ))
    expect(audio.paused).toBe(true)
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

    const view = render(<PlayerPage />)
    const audio = getAudio(view.container)
    await waitFor(() => expect(audio.src).toContain("queueItemId=queue-1"))
    prepareAudio(audio)
    await loadAudioMetadata(audio)
    await user.click(screen.getByRole("button", { name: "Skip track" }))
    await waitFor(() => expect(doubles.api.skipPlayback).toHaveBeenCalledWith(doubles.session, "party-1"))
    await waitFor(() => expect(doubles.mutateQueue).toHaveBeenCalled())
    expect(audio.paused).toBe(true)

    doubles.queue = [
      queueItem("queue-1", currentTrack, "SKIPPED", 1),
      queueItem("queue-2", nextTrack, "PLAYING", 2),
    ]
    doubles.nowPlaying = nowPlaying(nextTrack, false, 0)
    view.rerender(<PlayerPage />)
    await waitFor(() => expect(audio.src).toContain("queueItemId=queue-2"))
    prepareAudio(audio)
    await loadAudioMetadata(audio)

    expect(HTMLMediaElement.prototype.play).not.toHaveBeenCalled()
    expect(screen.getByRole("button", { name: "Resume playback" }).hasAttribute("disabled")).toBe(false)
  })

  it("completes an ended queue item only once and loads the following item", async () => {
    const currentTrack = track("track-1", "Finished Track")
    const nextTrack = track("track-2", "Following Track")
    doubles.queue = [
      queueItem("queue-1", currentTrack, "PLAYING", 1),
      queueItem("queue-2", nextTrack, "PENDING", 2),
    ]
    doubles.nowPlaying = nowPlaying(currentTrack, true, 0)

    let resolveCompletion!: () => void
    const completion = new Promise<void>((resolve) => { resolveCompletion = resolve })
    doubles.api.completePlayback.mockReturnValue(completion)

    const view = render(<PlayerPage />)
    const audio = getAudio(view.container)
    await waitFor(() => expect(audio.src).toContain("queueItemId=queue-1"))
    prepareAudio(audio)
    await loadAudioMetadata(audio)
    await waitFor(() => expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(1))

    fireEvent.ended(audio)
    fireEvent.ended(audio)
    expect(doubles.api.completePlayback).toHaveBeenCalledTimes(1)

    await act(async () => {
      resolveCompletion()
      await completion
    })
    await waitFor(() => expect(doubles.mutateQueue).toHaveBeenCalled())

    doubles.queue = [
      queueItem("queue-1", currentTrack, "COMPLETED", 1),
      queueItem("queue-2", nextTrack, "PLAYING", 2),
    ]
    doubles.nowPlaying = nowPlaying(nextTrack, true, 0)
    view.rerender(<PlayerPage />)
    await waitFor(() => expect(audio.src).toContain("queueItemId=queue-2"))
    prepareAudio(audio)
    await loadAudioMetadata(audio)
    await waitFor(() => expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(2))
    expect(doubles.api.completePlayback).toHaveBeenCalledTimes(1)
  })
})
