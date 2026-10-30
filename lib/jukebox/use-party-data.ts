"use client"

import useSWR from "swr"
import { jukeboxApi } from "@/lib/jukebox/api"
import { useJukebox } from "@/lib/jukebox/context"

export function usePartyData() {
  const { hydrated, session, liveQueue, liveNowPlaying, realtimeStatus } = useJukebox()
  const activeSession = hydrated && session?.partyId ? session : null

  const queueQuery = useSWR(
    activeSession ? ["party-queue", activeSession.partyId] : null,
    () => jukeboxApi.getQueue(activeSession!),
    { refreshInterval: activeSession && realtimeStatus !== "connected" ? 5000 : 0, errorRetryCount: 1, revalidateOnFocus: true },
  )
  const nowPlayingQuery = useSWR(
    activeSession ? ["party-now-playing", activeSession.partyId] : null,
    () => jukeboxApi.getNowPlaying(activeSession!),
    { refreshInterval: activeSession && realtimeStatus !== "connected" ? 5000 : 0, errorRetryCount: 1, revalidateOnFocus: true },
  )
  const guestsQuery = useSWR(
    activeSession?.role === "ADMIN" ? ["party-guests", activeSession.partyId] : null,
    () => jukeboxApi.getGuests(activeSession!),
    { refreshInterval: activeSession?.role === "ADMIN" ? 15000 : 0, errorRetryCount: 1 },
  )

  const queueFallback = activeSession
    ? queueQuery.data ?? []
    : []
  const nowPlayingFallback = !activeSession
    ? null
    : nowPlayingQuery.data !== undefined
      ? nowPlayingQuery.data
      : null
  const queue = realtimeStatus === "connected" && liveQueue !== null ? liveQueue : queueFallback
  const nowPlaying = realtimeStatus === "connected" && liveNowPlaying !== undefined ? liveNowPlaying : nowPlayingFallback

  // Log queue source to track WebSocket vs SWR data
  if (typeof window !== "undefined" && activeSession) {
    console.log("[usePartyData] Queue source:", {
      realtimeStatus,
      usingWebSocket: realtimeStatus === "connected" && liveQueue !== null,
      queueLength: queue.length,
      queueIds: queue.map((item) => ({ id: item.id, status: item.status })),
      liveQueue: liveQueue?.map((item) => ({ id: item.id, status: item.status })),
      fallbackQueue: queueFallback.map((item) => ({ id: item.id, status: item.status })),
    })
  }
  const guests = guestsQuery.data ?? []
  const isPreview = !activeSession || Boolean(queueQuery.error || nowPlayingQuery.error)
  const isGuestsPreview = !activeSession || activeSession.role !== "ADMIN" || Boolean(guestsQuery.error || !guestsQuery.data)

  const isSyncing = Boolean(activeSession && (queueQuery.isLoading || nowPlayingQuery.isLoading))

  return {
    queue,
    nowPlaying,
    guests,
    isPreview,
    isGuestsPreview,
    isSyncing,
    queueError: queueQuery.error as Error | undefined,
    guestsError: guestsQuery.error as Error | undefined,
    mutateQueue: queueQuery.mutate,
    mutateNowPlaying: nowPlayingQuery.mutate,
    mutateGuests: guestsQuery.mutate,
  }
}
