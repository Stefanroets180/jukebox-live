"use client"

import { useEffect, useState, type FormEvent } from "react"
import Link from "next/link"
import { AnimatePresence, motion } from "framer-motion"
import { QRCodeSVG } from "qrcode.react"
import {
  Activity,
  AudioLines,
  Check,
  CircleAlert,
  Clipboard,
  Disc3,
  ExternalLink,
  Fullscreen,
  LoaderCircle,
  MapPin,
  Radio,
  SkipForward,
  Users,
  Wifi,
} from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import { QueuePanel } from "@/components/jukebox/queue-panel"
import { SiteHeader } from "@/components/jukebox/site-header"
import { CoverArt } from "@/components/jukebox/cover-art"
import { useJukebox } from "@/lib/jukebox/context"
import { usePartyData } from "@/lib/jukebox/use-party-data"
import { ApiError, jukeboxApi } from "@/lib/jukebox/api"
import { formatDuration } from "@/lib/jukebox/types"
import { toast } from "sonner"

export function HostDisplay() {
  const { session, isAdmin, updateParty, realtimeStatus } = useJukebox()
  const { queue, nowPlaying, guests, isPreview, isSyncing, mutateQueue } = usePartyData()
  const [origin, setOrigin] = useState("https://jukebox.live")
  const [fullscreen, setFullscreen] = useState(false)
  const [partyDialogOpen, setPartyDialogOpen] = useState(false)
  const [musicLibraryPath, setMusicLibraryPath] = useState("")
  const [creatingParty, setCreatingParty] = useState(false)
  const [skipping, setSkipping] = useState(false)
  const [partyError, setPartyError] = useState("")
  const [partyCodeCopied, setPartyCodeCopied] = useState(false)
  const partyCode = session?.partyCode ?? "------"
  const joinUrl = `${origin}/join?party=${encodeURIComponent(partyCode)}`
  const currentQueueItem = queue.find((item) => item.status === "PLAYING") ?? queue[0]
  const isPlaying = nowPlaying?.isPlaying ?? false
  const duration = nowPlaying?.track.durationSeconds ?? null
  const progress = nowPlaying && duration && duration > 0
    ? Math.min(100, Math.max(0, (nowPlaying.progressSeconds / duration) * 100))
    : 0

  useEffect(() => {
    setOrigin(window.location.origin)
    const updateFullscreen = () => setFullscreen(Boolean(document.fullscreenElement))
    document.addEventListener("fullscreenchange", updateFullscreen)
    return () => document.removeEventListener("fullscreenchange", updateFullscreen)
  }, [])

  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen()
      else await document.documentElement.requestFullscreen()
    } catch {
      toast.error("Fullscreen is unavailable", { description: "Your browser may block fullscreen from this window." })
    }
  }

  async function copyPartyCode() {
    try {
      await navigator.clipboard.writeText(partyCode)
      setPartyCodeCopied(true)
      window.setTimeout(() => setPartyCodeCopied(false), 1600)
      toast.success("Party code copied")
    } catch {
      toast.error("Could not copy the party code")
    }
  }

  async function skipCurrentTrack(item = currentQueueItem) {
    if (!session || !session.partyId || !item || item.id.startsWith("preview-")) return
    setSkipping(true)
    try {
      await jukeboxApi.skipPlayback(session, session.partyId)
      await mutateQueue()
      toast.success("Track skipped", { description: "The room queue has been updated." })
    } catch (error) {
      toast.error("Could not skip this track", { description: error instanceof Error ? error.message : "Try again." })
    } finally {
      setSkipping(false)
    }
  }

  function reorderQueue(nextQueue: typeof queue) {
    void mutateQueue(nextQueue, { revalidate: false })
    toast.info("Queue order changed in this view", {
      description: "The supplied API contract has no reorder endpoint, so the host may not keep this order.",
    })
  }

  async function createParty(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session || !isAdmin) return
    setPartyError("")
    setCreatingParty(true)
    try {
      const party = await jukeboxApi.createParty(session, {
        hostLatitude: null,
        hostLongitude: null,
        musicLibraryPath: musicLibraryPath.trim(),
      })
      updateParty({ id: party.id, code: party.code })
      setPartyDialogOpen(false)
      toast.success("Party is live", { description: `Share code ${party.code} with your guests. They must be on the same Wi-Fi network.` })
    } catch (error) {
      setPartyError(error instanceof ApiError ? error.message : error instanceof Error ? error.message : "The party could not be started.")
    } finally {
      setCreatingParty(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto max-w-[1600px] px-4 py-7 sm:px-6 sm:py-9 xl:px-10">
        <section className="mb-7 flex flex-col gap-5 lg:mb-9 lg:flex-row lg:items-end lg:justify-between">
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="secondary" className="gap-2 rounded-full px-3 py-1 text-[11px]">
                <span className={`size-1.5 rounded-full ${realtimeStatus === "connected" && !isPreview ? "bg-emerald-400" : "bg-primary"}`} />
                {realtimeStatus === "connected" && !isPreview ? "ON AIR" : "HOST DISPLAY PREVIEW"}
              </Badge>
              {session && <Badge variant="outline" className="rounded-full px-3 py-1 text-[11px]">Room {partyCode}</Badge>}
              {isSyncing && <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground"><LoaderCircle aria-hidden="true" className="size-3.5 animate-spin" />Syncing live room</span>}
            </div>
            <h1 className="text-3xl font-semibold tracking-[-0.04em] sm:text-4xl">Tonight&apos;s room</h1>
            <p className="text-sm text-muted-foreground sm:text-base">A big-screen view of the soundtrack your guests are building together.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="outline" onClick={toggleFullscreen}>
              <Fullscreen aria-hidden="true" data-icon="inline-start" />{fullscreen ? "Exit full screen" : "Full screen"}
            </Button>
            <Button type="button" onClick={() => setPartyDialogOpen(true)} disabled={!session || !isAdmin} title={!session ? "Host sign-in is required" : !isAdmin ? "An admin account is required to start a party" : undefined}>
              <Radio aria-hidden="true" data-icon="inline-start" />Start a party
            </Button>
            <Button type="button" variant="outline" disabled title="The current API contract does not include an end-party endpoint">End party</Button>
            <Link href="/admin/party-settings" className={buttonVariants({ variant: "outline" })}>Manage guests</Link>
          </div>
        </section>

        {!session && (
          <Alert className="mb-6 border-primary/20 bg-primary/[0.06]">
            <CircleAlert aria-hidden="true" />
            <AlertTitle>Preview room</AlertTitle>
            <AlertDescription>The screen is showing a sample QR. Sign in at /join?mode=admin with an ADMIN account to connect a real party.</AlertDescription>
          </Alert>
        )}
        {session && !isAdmin && (
          <Alert className="mb-6 border-border bg-card/60">
            <CircleAlert aria-hidden="true" />
            <AlertTitle>Host controls need an admin account</AlertTitle>
            <AlertDescription>Your guest session can view the room. Use an account with the ADMIN role to start parties and control the live queue.</AlertDescription>
          </Alert>
        )}

        <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.45fr)_minmax(360px,0.8fr)]">
          <Card className="relative overflow-hidden border-border/80 bg-card/80">
            <div aria-hidden="true" className="pointer-events-none absolute -right-36 -top-32 size-[28rem] rounded-full bg-primary/12 blur-[100px]" />
            <CardHeader className="relative flex flex-row items-center justify-between gap-4 border-b border-border/60 px-5 py-5 sm:px-7">
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2"><AudioLines aria-hidden="true" className="size-4 text-primary" /><span className="text-[10px] font-semibold tracking-[0.2em] text-primary uppercase">Now playing</span></div>
                <CardDescription>Current room selection</CardDescription>
              </div>
              <span className="flex items-center gap-2 rounded-full border border-border bg-background/60 px-3 py-1.5 text-xs text-muted-foreground">
                <span className={`size-1.5 rounded-full ${nowPlaying?.isPlaying ? "bg-emerald-400" : "bg-muted-foreground"}`} />
                {!nowPlaying ? "Idle" : isPlaying ? "Playing" : "Paused"}
              </span>
            </CardHeader>
            <CardContent className="relative grid gap-6 p-5 sm:p-7 md:grid-cols-[minmax(230px,0.75fr)_minmax(0,1fr)] md:items-center md:gap-8">
              <AnimatePresence mode="wait" initial={false}>
                {nowPlaying ? (
                  <motion.div key={nowPlaying.track.id} initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 1.02 }} transition={{ duration: 0.25 }}>
                    <CoverArt
                      src={nowPlaying.track.albumArtPath}
                      title={nowPlaying.track.album ?? nowPlaying.track.title}
                      className="mx-auto w-full max-w-[360px] rounded-[1.4rem] shadow-2xl shadow-black/30"
                      sizes="(max-width: 768px) 80vw, 360px"
                    />
                  </motion.div>
                ) : (
                  <div className="grid aspect-square w-full place-items-center rounded-[1.4rem] border border-dashed border-border bg-background/50 text-center text-sm text-muted-foreground">Waiting for the next track</div>
                )}
              </AnimatePresence>
              <div className="flex min-w-0 flex-col gap-6">
                {nowPlaying ? (
                  <AnimatePresence mode="wait" initial={false}>
                    <motion.div key={nowPlaying.track.id} className="flex flex-col gap-2" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.2 }}>
                      <span className="text-xs font-medium tracking-[0.18em] text-muted-foreground uppercase">{nowPlaying.track.album ?? "Single"}</span>
                      <h2 className="text-3xl leading-tight font-semibold tracking-[-0.04em] sm:text-4xl xl:text-5xl">{nowPlaying.track.title}</h2>
                      <p className="text-base text-muted-foreground sm:text-lg">{nowPlaying.track.artist}</p>
                    </motion.div>
                  </AnimatePresence>
                ) : (
                  <div><h2 className="text-3xl font-semibold tracking-tight">The room is yours.</h2><p className="mt-2 text-sm text-muted-foreground">Add a song to start the soundtrack.</p></div>
                )}
                <div className="flex flex-col gap-3">
                  <Progress value={progress} aria-label={nowPlaying ? `${Math.floor(progress)} percent of ${nowPlaying.track.title} played` : "No track playing"} className="h-1.5" />
                  <div className="flex items-center justify-between text-xs tabular-nums text-muted-foreground">
                    <span>{nowPlaying ? formatDuration(nowPlaying.progressSeconds) : "0:00"}</span>
                    <span>{nowPlaying ? formatDuration(duration) : "—:—"}</span>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
            <Link href="/player" className={buttonVariants({ variant: "outline" })}>
              <AudioLines aria-hidden="true" data-icon="inline-start" />Open dedicated player
            </Link>
                  <Button type="button" variant="outline" disabled={!session || isPreview || !currentQueueItem || skipping} onClick={() => skipCurrentTrack()}>
                    {skipping ? <LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" /> : <SkipForward aria-hidden="true" data-icon="inline-start" />}
                    Skip track
                  </Button>
                  <span className="text-xs text-muted-foreground">{nowPlaying?.requestedBy?.username ? `Picked by ${nowPlaying.requestedBy.username}` : "Shared room audio"}</span>
                </div>

              </div>
            </CardContent>
          </Card>

          <aside className="flex flex-col gap-5">
            <Card className="overflow-hidden border-border/80 bg-card/80">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex flex-col gap-1">
                    <CardTitle className="text-lg">Scan to join</CardTitle>
                    <CardDescription>Guests need the same Wi-Fi as the host.</CardDescription>
                  </div>
                  <span className="flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary"><Wifi aria-hidden="true" className="size-4" /></span>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col items-center gap-4">
                <div className="rounded-2xl bg-white p-3 shadow-[0_8px_40px_-18px_rgba(255,255,255,0.4)]">
                  <QRCodeSVG value={joinUrl} size={176} level="M" includeMargin title={`Join party ${partyCode}`} bgColor="#ffffff" fgColor="#15131b" />
                </div>
                <div className="flex w-full flex-col items-center gap-1">
                  <span className="text-[10px] font-semibold tracking-[0.18em] text-muted-foreground uppercase">Party code</span>
                  <button type="button" onClick={copyPartyCode} className="inline-flex items-center gap-2 rounded-lg px-2 py-1 font-mono text-2xl font-semibold tracking-[0.2em] text-foreground transition-colors hover:bg-secondary" aria-label="Copy party code">
                    {partyCode}
                    {partyCodeCopied ? <Check aria-hidden="true" className="size-4 text-emerald-400" /> : <Clipboard aria-hidden="true" className="size-4 text-muted-foreground" />}
                  </button>
                  <span className="max-w-full truncate text-center text-[11px] text-muted-foreground">{joinUrl}</span>
                </div>
                <div className="grid w-full grid-cols-2 gap-2">
                  <Button type="button" variant="outline" size="sm" onClick={copyPartyCode}><Clipboard aria-hidden="true" data-icon="inline-start" />Copy code</Button>
                  <Link href={joinUrl} target="_blank" rel="noreferrer" className={buttonVariants({ variant: "outline", size: "sm" })}><ExternalLink aria-hidden="true" data-icon="inline-start" />Open join page</Link>
                </div>
                {isPreview && <p className="text-center text-[10px] leading-4 text-muted-foreground">Sample QR preview. It will use your active party code after host sign-in.</p>}
              </CardContent>
            </Card>
            <QueuePanel items={queue} isPreview={isPreview} showHostControls canReorder={isAdmin && !isPreview} onReorder={reorderQueue} skippingId={skipping ? currentQueueItem?.id : null} onSkip={skipCurrentTrack} />
          </aside>
        </div>

        <section aria-label="Party metrics" className="mt-5 grid gap-3 sm:grid-cols-3">
          <MetricCard label="In the room" value={String(guests.length)} hint="active guests" icon={<Users aria-hidden="true" className="size-4" />} />
          <MetricCard label="Songs played" value="—" hint="not exposed by API" icon={<Disc3 aria-hidden="true" className="size-4" />} />
          <MetricCard label="Credits used" value="—" hint="not exposed by API" icon={<Activity aria-hidden="true" className="size-4" />} />
        </section>

        <section className="mt-7 flex flex-col gap-4 rounded-2xl border border-border/70 bg-card/50 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><Wifi aria-hidden="true" className="size-4" /></span>
            <div className="flex flex-col gap-1"><span className="text-sm font-semibold">Local network access</span><span className="text-xs leading-5 text-muted-foreground">Guests must be on the same Wi-Fi network as the host to join the party.</span></div>
          </div>
          <Link href="/admin/party-settings" className={buttonVariants({ variant: "outline", size: "sm" })}>Manage guests <ExternalLink aria-hidden="true" data-icon="inline-end" /></Link>
        </section>
      </main>

      <Dialog open={partyDialogOpen} onOpenChange={setPartyDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Start a new party</DialogTitle>
            <DialogDescription>Set the music folder. Guests must be on the same Wi-Fi network to join.</DialogDescription>
          </DialogHeader>
          <form onSubmit={createParty} className="flex flex-col gap-5">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="library-path">Music library folder</FieldLabel>
                <Input id="library-path" value={musicLibraryPath} onChange={(event) => setMusicLibraryPath(event.target.value)} placeholder="C:\Users\YourName\Music" required />
                <FieldDescription>Enter the folder path on the host machine.</FieldDescription>
              </Field>
            </FieldGroup>
            {partyError && <Alert variant="destructive"><CircleAlert aria-hidden="true" /><AlertTitle>Could not start party</AlertTitle><AlertDescription>{partyError}</AlertDescription></Alert>}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setPartyDialogOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={creatingParty || !musicLibraryPath.trim()}>
                {creatingParty ? <LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" /> : <Radio aria-hidden="true" data-icon="inline-start" />}
                {creatingParty ? "Starting…" : "Create party"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function MetricCard({ label, value, hint, icon }: { label: string; value: string; hint: string; icon: React.ReactNode }) {
  return (
    <Card className="border-border/70 bg-card/60">
      <CardContent className="flex items-center gap-3.5 p-4 sm:p-5">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-secondary text-primary">{icon}</span>
        <div className="flex min-w-0 flex-col gap-0.5"><span className="text-xs text-muted-foreground">{label}</span><span className="text-xl font-semibold tabular-nums">{value}</span></div>
        <span className="ml-auto text-right text-[10px] leading-4 text-muted-foreground">{hint}</span>
      </CardContent>
    </Card>
  )
}
