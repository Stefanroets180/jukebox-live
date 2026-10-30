"use client"

import { createContext, useContext, useEffect, useRef, useState } from "react"
import useSWR from "swr"
import { useJukebox } from "@/lib/jukebox/context"
import { jukeboxApi, ApiError } from "@/lib/jukebox/api"
import { usePartyData } from "@/lib/jukebox/use-party-data"

type AudioPlayerContextValue = {
  resumePlayback: () => void
  audioError: string
}

export const AudioPlayerContext = createContext<AudioPlayerContextValue | null>(null)

function createPlayerInstanceId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID()
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16)
    const value = character === "x" ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

function getApiErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

export function GlobalAudioPlayer() {
  const { hydrated, session, isAdmin } = useJukebox()
  const { queue, nowPlaying, mutateQueue, mutateNowPlaying } = usePartyData()
  const audioRef = useRef<HTMLAudioElement>(null)
  const [playerInstanceId, setPlayerInstanceId] = useState<string | null>(null)
  const [audioLoading, setAudioLoading] = useState(false)
  const [audioReady, setAudioReady] = useState(false)
  const [audioIsPlaying, setAudioIsPlaying] = useState(false)
  const [audioError, setAudioError] = useState("")
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const loadedQueueItemIdRef = useRef<string | null>(null)
  const shouldAutoplayRef = useRef(false)
  const audioResumeRef = useRef<{ queueItemId: string; seconds: number; shouldPlay: boolean } | null>(null)
  const isCompletingRef = useRef(false)

  const adminSession = isAdmin && session?.partyId ? session : null
  const partyId = adminSession?.partyId ?? null

  const resumePlayback = () => {
    const audio = audioRef.current
    if (!audio) return
    console.log("[GlobalAudioPlayer] Resume playback called")
    void audio.play()
      .then(() => {
        console.log("[GlobalAudioPlayer] Resume playback succeeded")
        setAudioError("")
      })
      .catch((error) => {
        console.error("[GlobalAudioPlayer] Resume playback rejected:", error.name, error.message)
        setAudioError("Could not resume playback. Please try again.")
      })
  }

  useEffect(() => {
    const id = createPlayerInstanceId()
    console.log("[GlobalAudioPlayer] Player instance created:", {
      playerInstanceId: id,
      partyId,
      adminSession: !!adminSession,
      timestamp: new Date().toISOString(),
    })
    setPlayerInstanceId(id)
  }, [])

  // Player lease acquisition
  const leaseQuery = useSWR(
    adminSession && playerInstanceId
      ? ["global-player-lease", partyId, playerInstanceId]
      : null,
    () => jukeboxApi.claimPlayerLease(adminSession!, partyId!, playerInstanceId!),
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      shouldRetryOnError: false,
      onSuccess: (data) => {
        console.log("[GlobalAudioPlayer] Lease acquired:", {
          acquired: data.acquired,
          message: data.message,
          expiresAt: data.expiresAt,
          playerInstanceId,
          partyId,
        })
      },
      onError: (error) => {
        console.error("[GlobalAudioPlayer] Lease acquisition error:", {
          status: error instanceof ApiError ? error.status : 'unknown',
          message: error instanceof ApiError ? error.message : String(error),
          playerInstanceId,
          partyId,
        })
      },
    },
  )

  const heartbeatQuery = useSWR(
    leaseQuery.data?.acquired && adminSession && playerInstanceId
      ? ["global-player-heartbeat", partyId, playerInstanceId]
      : null,
    () => jukeboxApi.heartbeatPlayerLease(adminSession!, partyId!, playerInstanceId!),
    {
      refreshInterval: 10_000,
      revalidateOnFocus: false,
      revalidateOnReconnect: true,
      shouldRetryOnError: true,
      onErrorRetry: (error, key, config, revalidate, { retryCount }) => {
        const status = error instanceof ApiError ? error.status : 'unknown'
        console.error("[GlobalAudioPlayer] Heartbeat error:", {
          status,
          message: error instanceof ApiError ? error.message : String(error),
          code: error instanceof ApiError ? error.code : undefined,
          retryCount: retryCount + 1,
          playerInstanceId,
          partyId,
        })
        // 409 means lease belongs to another instance or is expired
        // This is a permanent error - do NOT retry indefinitely
        // Only retry for transient network errors (5xx, network failures)
        if (status === 409) {
          console.log("[GlobalAudioPlayer] Heartbeat 409 is permanent, stopping retries")
          return
        }
        setTimeout(() => revalidate({ retryCount }), 1000)
      },
      onSuccess: (data) => {
        console.log("[GlobalAudioPlayer] Heartbeat success:", {
          acquired: data.acquired,
          message: data.message,
          expiresAt: data.expiresAt,
          playerInstanceId,
        })
      },
    },
  )

  const leaseError = leaseQuery.error ?? heartbeatQuery.error
  const leaseTaken = leaseError instanceof ApiError && leaseError.status === 409
  const leaseOwned = Boolean(leaseQuery.data?.acquired)
    && !leaseError
    && heartbeatQuery.data?.acquired !== false

  console.log("[GlobalAudioPlayer] Lease state:", {
    leaseOwned,
    leaseTaken,
    leaseError: leaseError?.message,
    leaseAcquired: leaseQuery.data?.acquired,
    heartbeatAcquired: heartbeatQuery.data?.acquired,
    playerInstanceId,
    partyId,
    timestamp: new Date().toISOString(),
  })

  // Only enable for admin sessions with active party and lease
  const activeQueueItem = adminSession && leaseOwned
    ? queue.find((item) => item.status === "PLAYING") ?? null
    : null
  const matchingNowPlaying = activeQueueItem && nowPlaying?.track.id === activeQueueItem.track.id
    ? nowPlaying
    : null
  const currentTrack = matchingNowPlaying?.track ?? activeQueueItem?.track ?? null
  const serverProgress = matchingNowPlaying?.progressSeconds ?? 0
  const serverWantsPlayback = matchingNowPlaying?.isPlaying ?? false

  // Load audio when track changes
  useEffect(() => {
    const audio = audioRef.current
    console.log("[GlobalAudioPlayer] Audio loading effect running", {
      hasAudio: !!audio,
      hasAdminSession: !!adminSession,
      partyId,
      playerInstanceId,
      activeQueueItemId: activeQueueItem?.id,
      activeQueueItemStatus: activeQueueItem?.status,
      leaseOwned,
      loadedQueueItemId: loadedQueueItemIdRef.current,
      audioSrc: audio?.src,
      queue: queue.map((item) => ({ id: item.id, status: item.status, trackId: item.track.id })),
      nowPlaying: nowPlaying?.track.id,
      timestamp: new Date().toISOString(),
    })

    if (!audio || !adminSession || !partyId || !playerInstanceId || !activeQueueItem || !leaseOwned) {
      console.log("[GlobalAudioPlayer] Skipping load - conditions not met")
      if (audio) {
        audio.pause()
        if (!activeQueueItem || !adminSession || !leaseOwned) {
          audio.removeAttribute("src")
          audio.load()
          loadedQueueItemIdRef.current = null
          setAudioReady(false)
          setAudioIsPlaying(false)
          setAudioLoading(false)
          setCurrentTime(0)
          setDuration(0)
        }
      }
      return
    }

    // Reload if the queue item status changed (e.g., PENDING -> PLAYING)
    // Only skip reload if the same item is already loaded AND it's in PLAYING status
    const isSameItemLoaded = loadedQueueItemIdRef.current === activeQueueItem.id && audio.src && activeQueueItem.status === "PLAYING"
    console.log("[GlobalAudioPlayer] isSameItemLoaded:", isSameItemLoaded)

    const resumeState = audioResumeRef.current?.queueItemId === activeQueueItem.id
      ? audioResumeRef.current
      : null
    if (!resumeState) audioResumeRef.current = null
    // Determine autoplay intent: resume state takes priority, then queue status
    // If the queue item is PLAYING, the server wants playback regardless of nowPlaying state
    // This handles the race condition where nowPlaying arrives with isPlaying=false before the track starts
    shouldAutoplayRef.current = resumeState?.shouldPlay ?? activeQueueItem.status === "PLAYING"
    console.log("[GlobalAudioPlayer] shouldAutoplayRef set to:", shouldAutoplayRef.current, {
      resumeState,
      queueItemStatus: activeQueueItem.status,
    })

    if (isSameItemLoaded) {
      console.log("[GlobalAudioPlayer] Skipping reload - same item already loaded and playing")
      return
    }
    setAudioError("")
    setAudioLoading(true)
    setAudioReady(false)

    // Get audio token
    console.log("[GlobalAudioPlayer] Requesting audio token for:", activeQueueItem.id)
    jukeboxApi.getAudioToken(adminSession, activeQueueItem.id, partyId, playerInstanceId)
      .then((token) => {
        const audioUrl = jukeboxApi.getAudioUrl(adminSession, activeQueueItem.id, token, partyId, playerInstanceId)
        console.log("[GlobalAudioPlayer] Audio URL obtained:", audioUrl.substring(0, 50) + "...")

        const handleLoadedMetadata = () => {
          console.log("[GlobalAudioPlayer] loadedmetadata fired for:", activeQueueItem.id)
          const safeDuration = Number.isFinite(audio.duration) ? audio.duration : 0
          const desiredStart = Math.max(0, resumeState?.seconds ?? serverProgress)
          const startAt = safeDuration > 0 ? Math.min(desiredStart, safeDuration) : desiredStart
          audio.currentTime = startAt
          setDuration(safeDuration)
          setCurrentTime(startAt)
          setAudioReady(true)
          setAudioLoading(false)
          setAudioError("")
          loadedQueueItemIdRef.current = activeQueueItem.id
          console.log("[GlobalAudioPlayer] shouldAutoplayRef at play time:", shouldAutoplayRef.current)
          if (shouldAutoplayRef.current) {
            console.log("[GlobalAudioPlayer] Calling audio.play()")
            void audio.play()
              .then(() => {
                console.log("[GlobalAudioPlayer] audio.play() resolved successfully")
              })
              .catch((error) => {
                console.error("[GlobalAudioPlayer] audio.play() rejected:", error.name, error.message)
                setAudioError("Your browser blocked automatic playback.")
              })
          } else {
            console.log("[GlobalAudioPlayer] Skipping autoplay - shouldAutoplayRef is false")
          }
        }

        const handleLoadError = () => {
          console.error("[GlobalAudioPlayer] Audio load error for:", activeQueueItem.id)
          setAudioLoading(false)
          setAudioReady(false)
          setAudioIsPlaying(false)
          setAudioError("The local audio file could not be loaded.")
        }

        audio.addEventListener("loadedmetadata", handleLoadedMetadata, { once: true })
        audio.addEventListener("error", handleLoadError, { once: true })
        audio.src = audioUrl
        audio.load()
        console.log("[GlobalAudioPlayer] Audio src set and load() called")

        return () => {
          audio.removeEventListener("loadedmetadata", handleLoadedMetadata)
          audio.removeEventListener("error", handleLoadError)
        }
      })
      .catch((error) => {
        console.error("[GlobalAudioPlayer] Audio token request failed:", error)
        setAudioLoading(false)
        setAudioError(getApiErrorMessage(error, "Could not authorize audio stream."))
      })
  }, [
    activeQueueItem?.id,
    adminSession,
    partyId,
    playerInstanceId,
    matchingNowPlaying?.track.id,
    serverProgress,
    serverWantsPlayback,
    leaseOwned,
  ])

  // Update progress periodically
  useEffect(() => {
    const audio = audioRef.current
    if (!audio || !audioIsPlaying || !adminSession || !partyId || !playerInstanceId || !activeQueueItem || !leaseOwned) {
      return
    }

    const timer = window.setInterval(() => {
      const progressSeconds = Math.floor(audio.currentTime)
      void jukeboxApi.updatePlaybackProgress(
        adminSession,
        partyId,
        playerInstanceId,
        activeQueueItem.id,
        progressSeconds,
      ).catch((error: unknown) => setAudioError(getApiErrorMessage(error, "Playback progress could not be saved.")))
    }, 5_000)

    return () => window.clearInterval(timer)
  }, [audioIsPlaying, adminSession, partyId, playerInstanceId, activeQueueItem?.id, leaseOwned])

  // Handle track end
  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return

    const handleEnded = async () => {
      console.log("[GlobalAudioPlayer] Track ended for queue item:", activeQueueItem?.id)
      console.log("[GlobalAudioPlayer] Queue state before completion:", {
        queue: queue.map((item) => ({ id: item.id, status: item.status, trackId: item.track.id })),
        activeQueueItem: activeQueueItem?.id,
        nowPlaying: nowPlaying?.track.id,
      })
      setAudioIsPlaying(false)
      if (!activeQueueItem || !adminSession || !partyId || !leaseOwned || isCompletingRef.current) {
        console.log("[GlobalAudioPlayer] Skipping completion - conditions not met")
        return
      }

      isCompletingRef.current = true
      try {
        console.log("[GlobalAudioPlayer] Calling completePlayback for:", activeQueueItem.id)
        await jukeboxApi.completePlayback(adminSession, partyId, activeQueueItem.id)
        console.log("[GlobalAudioPlayer] completePlayback succeeded")
        setAudioError("")
        console.log("[GlobalAudioPlayer] Mutating queue and nowPlaying")
        await Promise.all([mutateQueue(), mutateNowPlaying()]).catch(() => undefined)
        console.log("[GlobalAudioPlayer] Queue state after mutation:", {
          queue: queue.map((item) => ({ id: item.id, status: item.status, trackId: item.track.id })),
          activeQueueItem: activeQueueItem?.id,
          nowPlaying: nowPlaying?.track.id,
        })
      } catch (error) {
        console.error("[GlobalAudioPlayer] Completion error:", error)
        setAudioError(getApiErrorMessage(error, "The completed track could not be advanced."))
      } finally {
        isCompletingRef.current = false
        console.log("[GlobalAudioPlayer] Completion finished, isCompletingRef reset")
      }
    }

    audio.addEventListener("ended", handleEnded)
    return () => audio.removeEventListener("ended", handleEnded)
  }, [activeQueueItem, adminSession, partyId, leaseOwned, mutateQueue, mutateNowPlaying])

  // Hidden audio element
  return (
    <AudioPlayerContext.Provider value={{ resumePlayback, audioError }}>
      <audio
        ref={audioRef}
        preload="none"
        aria-hidden="true"
        className="sr-only"
        onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
        onPlay={() => setAudioIsPlaying(true)}
        onPause={() => setAudioIsPlaying(false)}
      />
    </AudioPlayerContext.Provider>
  )
}

export function useAudioPlayer() {
  const context = useContext(AudioPlayerContext)
  if (!context) {
    // Return default values for tests that don't provide the context
    return {
      resumePlayback: () => {},
      audioError: "",
    }
  }
  return context
}
