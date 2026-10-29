"use client"

import { useDeferredValue, useRef, useState } from "react"
import Link from "next/link"
import useSWR from "swr"
import {
  AudioLines,
  CircleAlert,
  Disc3,
  LoaderCircle,
  Music2,
  Play,
  Search,
  SkipForward,
} from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import { CoverArt } from "@/components/jukebox/cover-art"
import { SiteHeader } from "@/components/jukebox/site-header"
import { ApiError, jukeboxApi } from "@/lib/jukebox/api"
import { useJukebox } from "@/lib/jukebox/context"
import { usePartyData } from "@/lib/jukebox/use-party-data"
import { formatDuration, type Track } from "@/lib/jukebox/types"
import { toast } from "sonner"

function getApiErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

export function DedicatedPlayer() {
  const { hydrated, session, isAdmin } = useJukebox()
  const { queue, nowPlaying, isSyncing, mutateQueue, mutateNowPlaying } = usePartyData()
  const adminSession = isAdmin && session?.partyId ? session : null
  const partyId = adminSession?.partyId ?? null
  const [searchText, setSearchText] = useState("")
  const deferredSearch = useDeferredValue(searchText.trim())
  const [addingTrackId, setAddingTrackId] = useState<string | null>(null)
  const [controlBusy, setControlBusy] = useState(false)
  const isAdvancingRef = useRef(false)

  // Lease and audio playback are now managed by GlobalAudioPlayer in app/providers.tsx
  // This component is UI-only and displays playback state

  const activeQueueItem = adminSession
    ? queue.find((item) => item.status === "PLAYING") ?? null
    : null
  const matchingNowPlaying = activeQueueItem && nowPlaying?.track.id === activeQueueItem.track.id
    ? nowPlaying
    : null
  const currentTrack = matchingNowPlaying?.track ?? activeQueueItem?.track ?? null
  const requestedByUsername = matchingNowPlaying?.requestedBy?.username ?? activeQueueItem?.requestedBy.username ?? null
  const pendingQueue = adminSession
    ? queue.filter((item) => item.status === "PENDING")
    : []

  const librarySearch = useSWR<Track[], ApiError>(
    adminSession && deferredSearch.length >= 2
      ? ["dedicated-player-library", partyId, deferredSearch]
      : null,
    () => jukeboxApi.searchTracks(deferredSearch, adminSession!),
    {
      keepPreviousData: true,
      revalidateOnFocus: false,
    },
  )

  const serverProgress = matchingNowPlaying?.progressSeconds ?? 0
  const serverWantsPlayback = matchingNowPlaying?.isPlaying ?? false
  const searchedTracks = librarySearch.data ?? []
  const displayedProgressSeconds = serverProgress
  const displayedDuration = currentTrack?.durationSeconds ?? 0

  async function skipCurrentTrack() {
    if (!activeQueueItem || !adminSession || !partyId || isAdvancingRef.current) return

    isAdvancingRef.current = true
    setControlBusy(true)
    try {
      await jukeboxApi.skipPlayback(adminSession, partyId)
      await Promise.all([mutateQueue(), mutateNowPlaying()]).catch(() => undefined)
      toast.success("Track skipped")
    } catch (error) {
      toast.error("Could not skip this track", { description: getApiErrorMessage(error, "Try again.") })
    } finally {
      isAdvancingRef.current = false
      setControlBusy(false)
    }
  }

  async function startNextTrack() {
    if (!adminSession || !partyId || pendingQueue.length === 0 || isAdvancingRef.current) return

    isAdvancingRef.current = true
    setControlBusy(true)
    try {
      await jukeboxApi.startPlayback(adminSession, partyId)
      await Promise.all([mutateQueue(), mutateNowPlaying()]).catch(() => undefined)
      toast.success("Playback started")
    } catch (error) {
      toast.error("Could not start playback", { description: getApiErrorMessage(error, "Try again.") })
    } finally {
      isAdvancingRef.current = false
      setControlBusy(false)
    }
  }

  async function addTrackToQueue(track: Track) {
    if (!adminSession) return
    setAddingTrackId(track.id)
    try {
      await jukeboxApi.addToQueue(adminSession, track.id)
      await Promise.all([mutateQueue(), mutateNowPlaying()])
      toast.success("Added to the party queue", { description: `${track.title} by ${track.artist}` })
    } catch (error) {
      toast.error("Could not add this track", { description: getApiErrorMessage(error, "Try again.") })
    } finally {
      setAddingTrackId(null)
    }
  }

  const leaseStateLabel = "Player active (managed by global audio player)"

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto max-w-[1500px] px-4 py-7 sm:px-6 sm:py-9 xl:px-10">
        <header className="mb-7 flex flex-col gap-4 sm:mb-9 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex flex-col gap-3">
            <Badge variant="secondary" className="w-fit gap-2 rounded-full px-3 py-1 text-[11px]">
              <AudioLines aria-hidden="true" className="size-3.5 text-primary" />
              HOST PLAYER
            </Badge>
            <h1 className="text-3xl font-semibold tracking-[-0.04em] sm:text-4xl">Dedicated player</h1>
            <p className="max-w-2xl text-sm text-muted-foreground sm:text-base">
              One host screen streams the room&apos;s local library. Playback status and progress stay in sync with the party.
            </p>
          </div>
          <Link href="/host" className={buttonVariants({ variant: "outline" })}>
            Return to host display
          </Link>
        </header>

        {!hydrated ? (
          <Card className="border-border/80 bg-card/80">
            <CardContent className="flex min-h-40 items-center justify-center gap-3 text-sm text-muted-foreground">
              <LoaderCircle aria-hidden="true" className="size-4 animate-spin" />Loading your host session
            </CardContent>
          </Card>
        ) : !adminSession ? (
          <Alert className="border-primary/20 bg-primary/[0.06]">
            <CircleAlert aria-hidden="true" />
            <AlertTitle>A host party is required</AlertTitle>
            <AlertDescription>
              Sign in with an ADMIN account and start or join a host party before using the dedicated player.
              <Link href="/host" className={buttonVariants({ variant: "link" })}>
                Open host display
              </Link>
            </AlertDescription>
          </Alert>
        ) : (
          <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(340px,0.85fr)]">
            <Card className="overflow-hidden border-border/80 bg-card/80">
              <CardHeader className="border-b border-border/60 px-5 py-5 sm:px-7">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex flex-col gap-1">
                    <CardTitle className="flex items-center gap-2 text-lg">
                      <Music2 aria-hidden="true" className="size-4 text-primary" />Now playing
                    </CardTitle>
                    <CardDescription>Party room audio from the host&apos;s scanned library</CardDescription>
                  </div>
                  <Badge variant="default" className="gap-1.5 rounded-full">
                    {leaseStateLabel}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-6 p-5 sm:p-7">
                {currentTrack ? (
                  <div className="grid gap-6 sm:grid-cols-[minmax(180px,0.62fr)_minmax(0,1fr)] sm:items-center sm:gap-8">
                    <CoverArt
                      src={currentTrack.albumArtPath}
                      title={currentTrack.album ?? currentTrack.title}
                      className="mx-auto w-full max-w-[320px] rounded-[1.4rem] shadow-2xl shadow-black/30"
                      sizes="(max-width: 768px) 80vw, 320px"
                      priority
                    />
                    <div className="flex min-w-0 flex-col gap-5">
                      <div className="flex flex-col gap-2">
                        <span className="text-xs font-medium tracking-[0.18em] text-muted-foreground uppercase">
                          {currentTrack.album ?? "Single"}
                        </span>
                        <h2 className="text-3xl leading-tight font-semibold tracking-[-0.04em] sm:text-4xl">
                          {currentTrack.title}
                        </h2>
                        <p className="text-base text-muted-foreground">{currentTrack.artist}</p>
                        <p className="text-xs text-muted-foreground">
                          {requestedByUsername ? `Requested by ${requestedByUsername}` : "Shared party selection"}
                        </p>
                        {!matchingNowPlaying && (
                          <p className="inline-flex items-center gap-2 text-xs text-muted-foreground" role="status">
                            {isSyncing ? <LoaderCircle aria-hidden="true" className="size-3.5 animate-spin" /> : <CircleAlert aria-hidden="true" className="size-3.5" />}
                            {isSyncing ? "Syncing playback state" : "Playback state is unavailable; controls are waiting for the server."}
                          </p>
                        )}
                      </div>

                      <div className="flex flex-col gap-3">
                        <Progress
                          value={matchingNowPlaying && displayedDuration > 0 ? Math.min(100, (displayedProgressSeconds / displayedDuration) * 100) : 0}
                          aria-label={`Playback progress for ${currentTrack.title}`}
                          className="h-1.5"
                        />
                        <div className="flex items-center justify-between text-xs tabular-nums text-muted-foreground">
                          <span>{formatDuration(matchingNowPlaying ? displayedProgressSeconds : null)}</span>
                          <span>{formatDuration(displayedDuration || currentTrack.durationSeconds)}</span>
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        <Button
                          type="button"
                          variant="outline"
                          size="lg"
                          onClick={skipCurrentTrack}
                          disabled={controlBusy}
                        >
                          <SkipForward aria-hidden="true" data-icon="inline-start" />Skip track
                        </Button>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="flex min-h-[320px] flex-col items-center justify-center gap-4 rounded-2xl border border-dashed border-border bg-background/40 px-6 text-center">
                    <span className="flex size-16 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                      <Disc3 aria-hidden="true" className="size-8" />
                    </span>
                    <div className="max-w-sm">
                      <h2 className="text-xl font-semibold tracking-tight">No track is playing</h2>
                      <p className="mt-2 text-sm text-muted-foreground">
                        Add a real track from the scanned library or start playback from the host display.
                      </p>
                    </div>
                    <div className="flex flex-wrap justify-center gap-2">
                      {pendingQueue.length > 0 && (
                        <Button type="button" onClick={startNextTrack} disabled={controlBusy}>
                          {controlBusy ? <LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" /> : <Play aria-hidden="true" data-icon="inline-start" />}
                          Start queued track
                        </Button>
                      )}
                      <Link href="/admin/library" className={buttonVariants({ variant: "outline" })}>
                        Browse music library
                      </Link>
                    </div>
                  </div>
                )}

              </CardContent>
            </Card>

            <aside className="flex flex-col gap-5">
              <Card className="border-border/80 bg-card/80">
                <CardHeader className="pb-3">
                  <CardTitle className="text-lg">Search scanned library</CardTitle>
                  <CardDescription>Search the host&apos;s current library and add a track to this party.</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                  <label className="relative block">
                    <Search aria-hidden="true" className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
                    <Input
                      value={searchText}
                      onChange={(event) => setSearchText(event.target.value)}
                      placeholder="Search title or artist"
                      className="pl-9"
                      aria-label="Search scanned music library"
                    />
                  </label>
                  {searchText.trim().length < 2 ? (
                    <p className="text-xs text-muted-foreground">Enter at least two characters to search the scanned library.</p>
                  ) : librarySearch.isLoading ? (
                    <div className="flex items-center gap-2 py-4 text-sm text-muted-foreground">
                      <LoaderCircle aria-hidden="true" className="size-4 animate-spin" />Searching the host library
                    </div>
                  ) : librarySearch.error ? (
                    <p role="alert" className="text-sm text-destructive">{librarySearch.error.message}</p>
                  ) : searchedTracks.length === 0 ? (
                    <p className="py-2 text-sm text-muted-foreground">No tracks matched this search.</p>
                  ) : (
                    <ul className="flex max-h-[360px] flex-col gap-2 overflow-y-auto pr-1">
                      {searchedTracks.map((track) => (
                        <li key={track.id} className="flex items-center gap-3 rounded-xl border border-border/70 bg-background/40 p-2.5">
                          <CoverArt
                            src={track.albumArtPath}
                            title={track.album ?? track.title}
                            className="size-12 shrink-0 rounded-lg"
                            sizes="48px"
                          />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium">{track.title}</p>
                            <p className="truncate text-xs text-muted-foreground">{track.artist}</p>
                          </div>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => void addTrackToQueue(track)}
                            disabled={addingTrackId !== null}
                          >
                            {addingTrackId === track.id ? <LoaderCircle aria-hidden="true" className="size-4 animate-spin" /> : "Add"}
                          </Button>
                        </li>
                      ))}
                    </ul>
                  )}
                </CardContent>
              </Card>

              <Card className="border-border/80 bg-card/80">
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <CardTitle className="text-lg">Up next</CardTitle>
                      <CardDescription>Live queue for this party</CardDescription>
                    </div>
                    <Badge variant="secondary" className="rounded-full">{pendingQueue.length}</Badge>
                  </div>
                </CardHeader>
                <CardContent>
                  {pendingQueue.length === 0 ? (
                    <p className="rounded-xl border border-dashed border-border px-4 py-5 text-center text-sm text-muted-foreground">
                      The queue is empty. Search the scanned library to add a track.
                    </p>
                  ) : (
                    <ol className="flex flex-col gap-2">
                      {pendingQueue.slice(0, 8).map((item, index) => (
                        <li key={item.id} className="flex items-center gap-3 rounded-xl border border-border/70 bg-background/40 p-2.5">
                          <span className="w-5 shrink-0 text-center text-xs tabular-nums text-muted-foreground">{index + 1}</span>
                          <CoverArt
                            src={item.track.albumArtPath}
                            title={item.track.album ?? item.track.title}
                            className="size-11 shrink-0 rounded-lg"
                            sizes="44px"
                          />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium">{item.track.title}</p>
                            <p className="truncate text-xs text-muted-foreground">{item.track.artist}</p>
                          </div>
                        </li>
                      ))}
                    </ol>
                  )}
                </CardContent>
              </Card>
            </aside>
          </div>
        )}
      </main>
    </div>
  )
}
