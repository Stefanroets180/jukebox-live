"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import useSWR from "swr"
import { motion } from "framer-motion"
import { ArrowRight, CircleAlert, Clock3, Disc3, Headphones, LoaderCircle, Search, Sparkles, Users } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { NowPlayingBar } from "@/components/jukebox/now-playing-bar"
import { QueuePanel } from "@/components/jukebox/queue-panel"
import { SiteHeader } from "@/components/jukebox/site-header"
import { TrackCard } from "@/components/jukebox/track-card"
import { usePartyData } from "@/lib/jukebox/use-party-data"
import { useJukebox } from "@/lib/jukebox/context"
import { ApiError, jukeboxApi } from "@/lib/jukebox/api"
import type { Track } from "@/lib/jukebox/types"
import { toast } from "sonner"

function useDebouncedValue(value: string, delay = 280) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debounced
}

function describeQueueError(error: unknown) {
  if (error instanceof Error && !(error instanceof ApiError)) {
    return { title: "Location access required", description: error.message }
  }
  const details = error instanceof ApiError ? `${error.code ?? ""} ${error.message}`.toLowerCase() : ""
  if (details.includes("wifi") || details.includes("network") || details.includes("ip")) {
    return { title: "Not on party Wi-Fi", description: "Connect to the same Wi-Fi as the host to add a song." }
  }
  if (details.includes("distance") || details.includes("gps") || details.includes("location")) {
    return { title: "Too far from the party", description: "Move closer to the host and make sure location access is enabled." }
  }
  if (details.includes("credit")) {
    return { title: "Not enough credits", description: "Ask your host to add credits, then try again." }
  }
  if (details.includes("ended") || details.includes("inactive")) {
    return { title: "Party ended", description: "This room is no longer accepting requests." }
  }
  return { title: "Song not added", description: error instanceof Error ? error.message : "Try again in a moment." }
}

export function GuestDashboard() {
  const { session, updateCredits, hydrated, isAdmin } = useJukebox()
  const { queue, nowPlaying, isPreview, isSyncing, mutateQueue } = usePartyData()
  const [searchText, setSearchText] = useState("")
  const [busyTrackId, setBusyTrackId] = useState<string | null>(null)
  const [queueError, setQueueError] = useState<{ title: string; description: string } | null>(null)
  const debouncedQuery = useDebouncedValue(searchText)
  const normalizedQuery = debouncedQuery.trim()
  const searchQuery = useSWR(
    normalizedQuery && session ? ["library-search", session.partyId, normalizedQuery] : null,
    () => jukeboxApi.searchTracks(normalizedQuery, session!),
    { keepPreviousData: true, errorRetryCount: 1 },
  )

  const tracks = normalizedQuery
    ? session
      ? searchQuery.error ? [] : searchQuery.data ?? []
      : []
    : session ? [] : []
  const usingSearchPreview = Boolean(normalizedQuery && (!session || searchQuery.error))
  const queuedTrackIds = useMemo(() => new Set(queue.map((item) => item.track.id)), [queue])

  async function queueTrack(track: Track) {
    if (!session) {
      toast.info("Join a party first", { description: "Sign in from the host screen to spend a queue credit." })
      return
    }
    if (session.credits < 1) {
      setQueueError({ title: "Not enough credits", description: "Ask your host to add credits, then try again." })
      return
    }

    setBusyTrackId(track.id)
    try {
      const result = await jukeboxApi.addToQueue(session, track.id)
      const optimisticItem = {
        id: result.id,
        track,
        requestedBy: { username: session.username },
        status: result.status,
        position: result.position,
        createdAt: new Date().toISOString(),
      }
      await mutateQueue((current) => [...(current ?? queue), optimisticItem], { revalidate: false })
      updateCredits(Math.max(0, session.credits - result.cost))
      toast.success("Added to the queue", { description: `${track.title} is on its way.` })
    } catch (error) {
      setQueueError(describeQueueError(error))
    } finally {
      setBusyTrackId(null)
    }
  }

  function reorderQueue(nextQueue: typeof queue) {
    void mutateQueue(nextQueue, { revalidate: false })
    toast.info("Queue order changed in this view", {
      description: "The Spring API contract has no reorder route yet, so other screens may not receive this change.",
    })
  }

  return (
    <div className="min-h-screen bg-background pb-28">
      <SiteHeader />
      <main className="mx-auto max-w-[1440px] px-4 py-8 sm:px-6 sm:py-10 xl:px-10">
        <section className="mb-8 flex flex-col gap-5 sm:mb-10 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="secondary" className="gap-1.5 rounded-full px-3 py-1 text-[11px]">
                <span className={`size-1.5 rounded-full ${session && !isPreview ? "bg-emerald-400" : "bg-primary"}`} />
                {session && !isPreview ? "LIVE PARTY" : "ROOM PREVIEW"}
              </Badge>
              {session && <Badge variant="outline" className="rounded-full px-3 py-1 text-[11px]">{session.partyCode}</Badge>}
              {isSyncing && <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground"><LoaderCircle aria-hidden="true" className="size-3.5 animate-spin" />Syncing room</span>}
            </div>
            <h1 className="text-3xl font-semibold tracking-[-0.04em] sm:text-4xl">{session ? `Hey, ${session.username}.` : "Make the next song yours."}</h1>
            <p className="max-w-2xl text-sm leading-6 text-muted-foreground sm:text-base">
              {session ? "Search the host's library and spend one credit to add a track." : "Join a party to search its library and add a song."}
            </p>
          </div>
          <div className="flex items-center gap-2 rounded-2xl border border-border/70 bg-card/70 px-4 py-3 sm:min-w-[180px]">
            <span className="flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary"><Disc3 aria-hidden="true" className="size-4" /></span>
            <div className="flex flex-col gap-0.5">
              <span className="text-[10px] font-medium tracking-[0.16em] text-muted-foreground uppercase">Your balance</span>
              <span className="text-sm font-semibold">{hydrated && session ? `${session.credits} credits` : "Join to see credits"}</span>
            </div>
          </div>
        </section>

        <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
          <div className="flex min-w-0 flex-col gap-7">
            <Card className="relative overflow-hidden border-border/70 bg-card/75">
              <div aria-hidden="true" className="pointer-events-none absolute -right-24 -top-28 size-72 rounded-full bg-primary/10 blur-[90px]" />
              <CardHeader className="relative flex flex-col gap-4 pb-4 sm:flex-row sm:items-end sm:justify-between">
                <div className="flex flex-col gap-1">
                  <CardTitle className="text-xl tracking-tight sm:text-2xl">Find a track for the room</CardTitle>
                  <CardDescription>Search by song, artist, album, or genre.</CardDescription>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground"><Users aria-hidden="true" className="size-3.5" />Everyone gets a turn</div>
              </CardHeader>
              <CardContent className="relative flex flex-col gap-5">
                <div className="relative">
                  <Search aria-hidden="true" className="absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={searchText}
                    onChange={(event) => setSearchText(event.target.value)}
                    placeholder="Try a song, artist, album…"
                    aria-label="Search the music library"
                    className="h-12 rounded-xl border-border/80 bg-background/80 pl-11 pr-4 text-sm"
                  />
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <Sparkles aria-hidden="true" className="size-3.5 text-primary" />
                    {normalizedQuery ? `Results for “${normalizedQuery}”` : session ? "Search the host catalog" : "Join a party to search"}
                  </div>
                  {usingSearchPreview && <Badge variant="outline" className="text-[10px]">Search unavailable</Badge>}
                </div>
                {searchQuery.isLoading && normalizedQuery && !searchQuery.data && (
                  <div className="flex items-center gap-2 text-xs text-muted-foreground"><LoaderCircle aria-hidden="true" className="size-3.5 animate-spin" />Searching the host library…</div>
                )}
                {session && session.credits < 1 && (
                  <Alert variant="destructive" className="border-destructive/25 bg-destructive/5">
                    <CircleAlert aria-hidden="true" />
                    <AlertTitle>You&apos;re out of credits</AlertTitle>
                    <AlertDescription>Ask the host to top up your balance before adding another track.</AlertDescription>
                  </Alert>
                )}
                {tracks.length > 0 ? (
                  <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3">
                    {tracks.slice(0, 6).map((track, index) => (
                      <motion.div key={track.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.035, duration: 0.2 }}>
                        <TrackCard
                          track={track}
                          disabled={!session || session.credits < 1}
                          needsCredits={Boolean(session && session.credits < 1)}
                          busy={busyTrackId === track.id}
                          queued={queuedTrackIds.has(track.id)}
                          onQueue={queueTrack}
                        />
                      </motion.div>
                    ))}
                  </div>
                ) : (
                  <Empty className="rounded-2xl border border-dashed border-border bg-background/30 px-6 py-8">
                    <EmptyHeader>
                      <EmptyMedia variant="icon"><Search aria-hidden="true" /></EmptyMedia>
                      <EmptyTitle>{normalizedQuery ? "No matching songs" : "Search the host library"}</EmptyTitle>
                      <EmptyDescription>{normalizedQuery ? "Try a different search or browse the host's library." : "Enter a song, artist, album, or genre to find the room's music."}</EmptyDescription>
                    </EmptyHeader>
                  </Empty>
                )}
                {!session && (
                  <div className="flex flex-col gap-3 rounded-2xl border border-primary/20 bg-primary/[0.06] p-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex flex-col gap-1">
                      <span className="text-sm font-semibold">Ready to add your pick?</span>
                      <span className="text-xs text-muted-foreground">Join the host&apos;s Wi-Fi and enter the party code.</span>
                    </div>
                    <Link href="/join" className={`${buttonVariants({ size: "sm" })} shrink-0`}>
                      Join a party <ArrowRight aria-hidden="true" data-icon="inline-end" />
                    </Link>
                  </div>
                )}
              </CardContent>
            </Card>

            <div className="flex flex-col gap-4">
              <div className="flex items-end justify-between gap-4">
                <div className="flex flex-col gap-1">
                  <h2 className="text-lg font-semibold tracking-tight">The room&apos;s sound</h2>
                  <p className="text-xs text-muted-foreground">A little inspiration while you decide.</p>
                </div>
                <span className="hidden items-center gap-1.5 text-xs text-muted-foreground sm:inline-flex"><Clock3 aria-hidden="true" className="size-3.5" />Updated live</span>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Card className="overflow-hidden border-border/70 bg-card/65">
                  <CardContent className="flex items-center gap-4 p-4">
                    <span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary"><Headphones aria-hidden="true" className="size-5" /></span>
                    <div className="flex min-w-0 flex-col gap-1"><span className="truncate text-sm font-semibold">One queue, everyone&apos;s picks</span><span className="text-xs text-muted-foreground">Songs move in the order they&apos;re added.</span></div>
                  </CardContent>
                </Card>
                <Card className="overflow-hidden border-border/70 bg-card/65">
                  <CardContent className="flex items-center gap-4 p-4">
                    <span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-secondary text-muted-foreground"><Users aria-hidden="true" className="size-5" /></span>
                    <div className="flex min-w-0 flex-col gap-1"><span className="truncate text-sm font-semibold">Make space for every voice</span><span className="text-xs text-muted-foreground">Each request costs one party credit.</span></div>
                  </CardContent>
                </Card>
              </div>
            </div>
          </div>

          <aside className="flex min-w-0 flex-col gap-5 xl:sticky xl:top-28">
            <QueuePanel items={queue} isPreview={isPreview} canReorder={isAdmin && !isPreview} onReorder={reorderQueue} />
            <Card className="border-border/70 bg-card/65">
              <CardContent className="flex items-center gap-3 p-4">
                <span className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary"><Disc3 aria-hidden="true" className="size-4" /></span>
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <span className="text-xs font-semibold">{nowPlaying ? nowPlaying.track.title : "Nothing playing"}</span>
                  <span className="truncate text-[11px] text-muted-foreground">{nowPlaying ? `Now playing · ${nowPlaying.track.artist}` : "The host can start playback when ready."}</span>
                </div>
                <span className="relative flex size-2 shrink-0"><span className="absolute inline-flex size-full animate-ping rounded-full bg-primary opacity-40" /><span className="relative inline-flex size-2 rounded-full bg-primary" /></span>
              </CardContent>
            </Card>
          </aside>
        </div>
      </main>

      <NowPlayingBar nowPlaying={nowPlaying} isPreview={isPreview} />

      <Dialog open={Boolean(queueError)} onOpenChange={(open) => { if (!open) setQueueError(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{queueError?.title ?? "Song not added"}</DialogTitle>
            <DialogDescription>{queueError?.description}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" onClick={() => setQueueError(null)}>Got it</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
