"use client"

import { useState } from "react"
import Link from "next/link"
import useSWR, { useSWRConfig } from "swr"
import { ArrowLeft, ArrowRight, ExternalLink, LoaderCircle, RefreshCw, SearchCheck } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { CoverArt } from "@/components/jukebox/cover-art"
import { ApiError, jukeboxApi } from "@/lib/jukebox/api"
import { useJukebox } from "@/lib/jukebox/context"
import { usePartyData } from "@/lib/jukebox/use-party-data"
import type { ArtworkLookupResult, Track } from "@/lib/jukebox/types"
import { toast } from "sonner"

const PAGE_SIZE = 12
const ARTWORK_SEARCH_CACHE_KEYS = new Set([
  "admin-library-search",
  "dedicated-player-library",
  "library-search",
])

function getArtworkStatusLabel(track: Track) {
  switch (track.artworkLookupStatus) {
    case "NOT_FOUND":
      return "No match"
    case "AMBIGUOUS":
      return "Review match"
    case "ERROR":
      return "Retry lookup"
    default:
      return "Not checked"
  }
}

export function ArtworkReviewPanel() {
  const { session, isAdmin } = useJukebox()
  const { mutate: mutateSWRCache } = useSWRConfig()
  const { mutateQueue, mutateNowPlaying } = usePartyData()
  const [offset, setOffset] = useState(0)
  const [busyTrackId, setBusyTrackId] = useState<string | null>(null)
  const [reviewTrackId, setReviewTrackId] = useState<string | null>(null)
  const [lookupResult, setLookupResult] = useState<ArtworkLookupResult | null>(null)
  const [selectingReleaseId, setSelectingReleaseId] = useState<string | null>(null)

  const backlog = useSWR(
    isAdmin && session ? ["missing-artwork", session.partyId, offset] : null,
    () => jukeboxApi.getMissingArtwork(session!, offset, PAGE_SIZE),
    { revalidateOnFocus: false, keepPreviousData: true },
  )
  const candidates = useSWR(
    isAdmin && session && reviewTrackId
      ? ["artwork-candidates", session.partyId, reviewTrackId]
      : null,
    () => jukeboxApi.getArtworkCandidates(session!, reviewTrackId!),
    { revalidateOnFocus: false },
  )

  const currentResult = lookupResult?.trackId === reviewTrackId
    ? lookupResult
    : candidates.data
  const tracks = backlog.data?.tracks ?? []
  const hasMore = offset + PAGE_SIZE < (backlog.data?.total ?? 0)

  async function refreshArtworkConsumers() {
    await Promise.all([
      mutateQueue(),
      mutateNowPlaying(),
      mutateSWRCache((key) => Array.isArray(key) && ARTWORK_SEARCH_CACHE_KEYS.has(String(key[0]))),
    ])
  }

  async function lookupTrack(track: Track) {
    if (!session) return
    setBusyTrackId(track.id)
    setReviewTrackId(track.id)
    setLookupResult(null)
    try {
      const shouldRetry = track.artworkLookupStatus === "NOT_FOUND" || track.artworkLookupStatus === "ERROR"
      const result = await jukeboxApi.lookupArtwork(session, track.id, shouldRetry)
      setLookupResult(result)
      if (result.status === "FOUND") {
        await refreshArtworkConsumers()
        toast.success("Album artwork saved", { description: `${track.title} — ${track.artist}` })
      } else if (result.status === "AMBIGUOUS") {
        toast.info("Choose the matching release", { description: "Artwork will be saved after you confirm a release." })
      }
      await backlog.mutate()
    } catch (error) {
      toast.error("Artwork lookup failed", {
        description: error instanceof ApiError ? error.message : error instanceof Error ? error.message : "Try again later.",
      })
    } finally {
      setBusyTrackId(null)
    }
  }

  async function chooseRelease(trackId: string, releaseId: string) {
    if (!session) return
    setSelectingReleaseId(releaseId)
    try {
      const result = await jukeboxApi.selectArtwork(session, trackId, releaseId)
      setLookupResult(result)
      await Promise.all([backlog.mutate(), candidates.mutate(), refreshArtworkConsumers()])
      toast.success("Artwork saved from the selected release")
    } catch (error) {
      toast.error("Could not save this artwork", {
        description: error instanceof ApiError ? error.message : error instanceof Error ? error.message : "Try again.",
      })
    } finally {
      setSelectingReleaseId(null)
    }
  }

  return (
    <Card className="overflow-hidden border-border/80 bg-card/80">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex flex-col gap-1">
            <CardTitle className="flex items-center gap-2 text-lg">
              <SearchCheck aria-hidden="true" className="size-4 text-primary" />Find missing artwork
            </CardTitle>
            <CardDescription>Match tracks to MusicBrainz releases and approved Cover Art Archive fronts.</CardDescription>
          </div>
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="Refresh missing artwork list"
            onClick={() => void backlog.mutate()}
            disabled={!session || backlog.isValidating}
          >
            {backlog.isValidating ? <LoaderCircle aria-hidden="true" className="size-4 animate-spin" /> : <RefreshCw aria-hidden="true" className="size-4" />}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {!isAdmin || !session ? (
          <Alert>
            <AlertTitle>Admin access required</AlertTitle>
            <AlertDescription>Artwork matching is available to the library administrator.</AlertDescription>
          </Alert>
        ) : backlog.error ? (
          <Alert variant="destructive">
            <AlertTitle>Could not load missing artwork</AlertTitle>
            <AlertDescription>{backlog.error.message}</AlertDescription>
          </Alert>
        ) : backlog.isLoading ? (
          <div className="flex items-center gap-2 py-5 text-sm text-muted-foreground">
            <LoaderCircle aria-hidden="true" className="size-4 animate-spin" />Loading library artwork status
          </div>
        ) : tracks.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border px-4 py-6 text-center">
            <p className="text-sm font-medium">All scanned tracks have artwork</p>
            <p className="mt-1 text-xs text-muted-foreground">Tracks without a cover will appear here after a library scan.</p>
          </div>
        ) : (
          <>
            <ul className="flex flex-col gap-2">
              {tracks.map((track) => {
                const isReviewing = reviewTrackId === track.id
                const canReview = track.artworkLookupStatus === "AMBIGUOUS"
                return (
                  <li key={track.id} className="rounded-xl border border-border/70 bg-background/40 p-3">
                    <div className="flex items-center gap-3">
                      <CoverArt
                        src={track.albumArtPath}
                        title={track.album ?? track.title}
                        className="size-12 shrink-0 rounded-lg"
                        sizes="48px"
                      />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{track.title}</p>
                        <p className="truncate text-xs text-muted-foreground">{track.artist}{track.album ? ` · ${track.album}` : ""}</p>
                      </div>
                      <Badge variant="outline" className="hidden shrink-0 sm:inline-flex">{getArtworkStatusLabel(track)}</Badge>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {canReview ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            setReviewTrackId(track.id)
                            setLookupResult(null)
                          }}
                        >
                          Review matches
                        </Button>
                      ) : (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => void lookupTrack(track)}
                          disabled={busyTrackId !== null}
                        >
                          {busyTrackId === track.id ? <LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" /> : <RefreshCw aria-hidden="true" data-icon="inline-start" />}
                          {track.artworkLookupStatus === "NOT_LOOKED_UP" ? "Find artwork" : "Retry lookup"}
                        </Button>
                      )}
                      {track.musicBrainzReleaseId && (
                        <Link href={`https://musicbrainz.org/release/${track.musicBrainzReleaseId}`} target="_blank" rel="noreferrer" className={buttonVariants({ variant: "ghost", size: "sm" })}>
                          MusicBrainz release<ExternalLink aria-hidden="true" data-icon="inline-end" />
                        </Link>
                      )}
                    </div>
                    {isReviewing && (
                      <div className="mt-3 rounded-xl border border-border/70 bg-card/70 p-3" aria-live="polite">
                        {currentResult?.message && <p className="mb-3 text-xs text-muted-foreground">{currentResult.message}</p>}
                        {!currentResult && candidates.isLoading ? (
                          <div className="flex items-center gap-2 py-3 text-xs text-muted-foreground">
                            <LoaderCircle aria-hidden="true" className="size-3.5 animate-spin" />Loading release matches
                          </div>
                        ) : currentResult?.candidates.length ? (
                          <ul className="flex flex-col gap-2">
                            {currentResult.candidates.map((candidate) => (
                              <li key={candidate.releaseId} className="flex items-center gap-3 rounded-lg border border-border/60 bg-background/50 p-2">
                                <CoverArt
                                  src={candidate.coverArtUrl}
                                  title={candidate.releaseTitle || candidate.recordingTitle}
                                  className="size-12 shrink-0 rounded-md"
                                  sizes="48px"
                                />
                                <div className="min-w-0 flex-1">
                                  <p className="truncate text-xs font-semibold">{candidate.releaseTitle || "Untitled release"}</p>
                                  <p className="truncate text-[11px] text-muted-foreground">
                                    {candidate.artist}{candidate.releaseDate ? ` · ${candidate.releaseDate.slice(0, 4)}` : ""}{candidate.country ? ` · ${candidate.country}` : ""}
                                  </p>
                                  <a href={candidate.sourceUrl} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 text-[10px] text-primary hover:underline">
                                    MusicBrainz · score {candidate.score}<ExternalLink aria-hidden="true" className="size-3" />
                                  </a>
                                </div>
                                <Button
                                  type="button"
                                  size="sm"
                                  onClick={() => void chooseRelease(track.id, candidate.releaseId)}
                                  disabled={selectingReleaseId !== null}
                                >
                                  {selectingReleaseId === candidate.releaseId ? <LoaderCircle aria-hidden="true" className="size-4 animate-spin" /> : "Use cover"}
                                </Button>
                              </li>
                            ))}
                          </ul>
                        ) : currentResult?.status === "NOT_FOUND" ? (
                          <p className="text-xs text-muted-foreground">No approved front-cover matches are available. You can retry the lookup later.</p>
                        ) : null}
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
            <div className="flex items-center justify-between border-t border-border/60 pt-3 text-xs text-muted-foreground">
              <span>{offset + 1}–{Math.min(offset + tracks.length, backlog.data?.total ?? 0)} of {backlog.data?.total ?? 0} tracks</span>
              <div className="flex gap-1">
                <Button type="button" variant="ghost" size="icon" aria-label="Previous missing artwork page" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>
                  <ArrowLeft aria-hidden="true" className="size-4" />
                </Button>
                <Button type="button" variant="ghost" size="icon" aria-label="Next missing artwork page" disabled={!hasMore} onClick={() => setOffset(offset + PAGE_SIZE)}>
                  <ArrowRight aria-hidden="true" className="size-4" />
                </Button>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
