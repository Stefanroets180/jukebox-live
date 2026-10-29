"use client"

import { useEffect, useRef, useState } from "react"
import useSWR from "swr"
import { useJukebox } from "@/lib/jukebox/context"
import { jukeboxApi, ApiError } from "@/lib/jukebox/api"
import { usePartyData } from "@/lib/jukebox/use-party-data"

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

  useEffect(() => {
    setPlayerInstanceId(createPlayerInstanceId())
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
      shouldRetryOnError: false,
    },
  )

  const leaseError = leaseQuery.error ?? heartbeatQuery.error
  const leaseTaken = leaseError instanceof ApiError && leaseError.status === 409
  const leaseOwned = Boolean(leaseQuery.data?.acquired)
    && !leaseError
    && heartbeatQuery.data?.acquired !== false

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
    if (!audio || !adminSession || !partyId || !playerInstanceId || !activeQueueItem || !matchingNowPlaying || !leaseOwned) {
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

    if (loadedQueueItemIdRef.current === activeQueueItem.id && audio.src) return

    const resumeState = audioResumeRef.current?.queueItemId === activeQueueItem.id
      ? audioResumeRef.current
      : null
    if (!resumeState) audioResumeRef.current = null
    shouldAutoplayRef.current = resumeState?.shouldPlay ?? serverWantsPlayback
    setAudioError("")
    setAudioLoading(true)
    setAudioReady(false)

    // Get audio token
    jukeboxApi.getAudioToken(adminSession, activeQueueItem.id, partyId, playerInstanceId)
      .then((token) => {
        const audioUrl = jukeboxApi.getAudioUrl(adminSession, activeQueueItem.id, token, partyId, playerInstanceId)
        
        const handleLoadedMetadata = () => {
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
          if (shouldAutoplayRef.current) {
            void audio.play().catch(() => {
              setAudioError("Your browser blocked automatic playback.")
            })
          }
        }

        const handleLoadError = () => {
          setAudioLoading(false)
          setAudioReady(false)
          setAudioIsPlaying(false)
          setAudioError("The local audio file could not be loaded.")
        }

        audio.addEventListener("loadedmetadata", handleLoadedMetadata, { once: true })
        audio.addEventListener("error", handleLoadError, { once: true })
        audio.src = audioUrl
        audio.load()

        return () => {
          audio.removeEventListener("loadedmetadata", handleLoadedMetadata)
          audio.removeEventListener("error", handleLoadError)
        }
      })
      .catch((error) => {
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
      setAudioIsPlaying(false)
      if (!activeQueueItem || !adminSession || !partyId || !leaseOwned || isCompletingRef.current) return

      isCompletingRef.current = true
      try {
        await jukeboxApi.completePlayback(adminSession, partyId, activeQueueItem.id)
        setAudioError("")
        await Promise.all([mutateQueue(), mutateNowPlaying()]).catch(() => undefined)
      } catch (error) {
        setAudioError(getApiErrorMessage(error, "The completed track could not be advanced."))
      } finally {
        isCompletingRef.current = false
      }
    }

    audio.addEventListener("ended", handleEnded)
    return () => audio.removeEventListener("ended", handleEnded)
  }, [activeQueueItem, adminSession, partyId, leaseOwned, mutateQueue, mutateNowPlaying])

  // Hidden audio element
  return (
    <audio
      ref={audioRef}
      preload="none"
      aria-hidden="true"
      className="sr-only"
      onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
      onPlay={() => setAudioIsPlaying(true)}
      onPause={() => setAudioIsPlaying(false)}
    />
  )
}
