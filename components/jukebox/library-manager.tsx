"use client"

import { useEffect, useMemo, useState, type FormEvent } from "react"
import useSWR from "swr"
import { motion } from "framer-motion"
import { AlertCircle, Check, Database, FolderOpen, HardDrive, LoaderCircle, Music2, Search, ScanSearch, Sparkles } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ArtworkReviewPanel } from "@/components/jukebox/artwork-review-panel"
import { CoverArt } from "@/components/jukebox/cover-art"
import { SiteHeader } from "@/components/jukebox/site-header"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useJukebox } from "@/lib/jukebox/context"
import { jukeboxApi } from "@/lib/jukebox/api"
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

export function LibraryManager() {
  const { session, isAdmin } = useJukebox()
  const [libraryPath, setLibraryPath] = useState("")
  const [scanning, setScanning] = useState(false)
  const [scanCount, setScanCount] = useState<number | null>(null)
  const [lastScan, setLastScan] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const query = useDebouncedValue(search.trim())

  const statsQuery = useSWR(
    isAdmin && session ? ["library-stats"] : null,
    () => jukeboxApi.getLibraryStats(session!),
    { errorRetryCount: 1, revalidateOnFocus: false },
  )
  const searchQuery = useSWR(
    query && session ? ["admin-library-search", session.partyId, query] : null,
    () => jukeboxApi.searchTracks(query, session!),
    { keepPreviousData: true, errorRetryCount: 1 },
  )

  const previewLibrary = !session || !isAdmin
  const tracks: Track[] = query
    ? searchQuery.error ? [] : searchQuery.data ?? []
    : previewLibrary ? [] : []
  const totalTracks = scanCount ?? statsQuery.data?.count ?? null

  async function handleScan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session || !isAdmin || !libraryPath.trim()) return
    setScanning(true)
    try {
      const result = await jukeboxApi.scanLibrary(session, libraryPath.trim())
      setScanCount(result.scannedCount)
      setLastScan(new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }))
      await statsQuery.mutate({ count: result.scannedCount }, { revalidate: false })
      toast.success("Library scan complete", { description: `${result.scannedCount.toLocaleString()} tracks indexed.` })
    } catch (error) {
      toast.error("Scan could not start", { description: error instanceof Error ? error.message : "Check the folder path and try again." })
    } finally {
      setScanning(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto max-w-[1440px] px-4 py-8 sm:px-6 sm:py-10 xl:px-10">
        <section className="mb-8 flex flex-col gap-4 sm:mb-10 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2"><Badge variant="secondary" className="rounded-full px-3 py-1 text-[11px]">HOST TOOLS</Badge></div>
            <h1 className="text-3xl font-semibold tracking-[-0.04em] sm:text-4xl">Music library</h1>
            <p className="max-w-2xl text-sm leading-6 text-muted-foreground sm:text-base">Index the host&apos;s local music folder and keep the room&apos;s searchable catalog fresh.</p>
          </div>
          <div className="flex items-center gap-3 rounded-2xl border border-border/70 bg-card/70 px-4 py-3">
            <span className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary"><Database aria-hidden="true" className="size-4" /></span>
            <div className="flex flex-col gap-0.5"><span className="text-[10px] font-medium tracking-[0.15em] text-muted-foreground uppercase">Indexed tracks</span><span className="text-lg font-semibold tabular-nums">{totalTracks ? totalTracks.toLocaleString('en-US') : (statsQuery.isLoading ? "…" : "—")}</span></div>
          </div>
        </section>

        {!isAdmin && (
          <Alert className="mb-6 border-primary/20 bg-primary/[0.06]">
            <AlertCircle aria-hidden="true" />
            <AlertTitle>{session ? "Admin session required" : "Host sign-in required"}</AlertTitle>
            <AlertDescription>{session ? "Your current account can browse the library, but only an ADMIN account can scan the host library." : "Sign in with an ADMIN account to scan a real music folder."}</AlertDescription>
          </Alert>
        )}

        <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(340px,0.75fr)]">
          <Card className="overflow-hidden border-border/80 bg-card/75">
            <CardHeader className="flex flex-col gap-4 border-b border-border/60 sm:flex-row sm:items-end sm:justify-between">
              <div className="flex flex-col gap-1"><CardTitle className="text-xl">Browse the catalog</CardTitle><CardDescription>Search by track, artist, album, or genre.</CardDescription></div>
              <Badge variant="outline" className="w-fit gap-1.5 text-[10px]"><span className={`size-1.5 rounded-full ${session && isAdmin && !statsQuery.error ? "bg-emerald-400" : "bg-muted-foreground"}`} />{session && isAdmin && !statsQuery.error ? "Host library" : "Not connected"}</Badge>
            </CardHeader>
            <CardContent className="flex flex-col gap-5 pt-5">
              <div className="relative">
                <Search aria-hidden="true" className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search the music library…" aria-label="Search library" className="h-11 bg-background/70 pl-10" />
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="inline-flex items-center gap-2 text-xs text-muted-foreground"><Sparkles aria-hidden="true" className="size-3.5 text-primary" />{query ? `Search results for “${query}”` : "Search the host catalog"}</span>
                {query && searchQuery.error && <Badge variant="outline" className="text-[10px]">Search unavailable</Badge>}
              </div>
              {query && searchQuery.isLoading && !searchQuery.data && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground"><LoaderCircle aria-hidden="true" className="size-3.5 animate-spin" />Searching the host catalog…</div>
              )}
              {tracks.length ? (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                  {tracks.slice(0, 8).map((track, index) => (
                    <motion.div key={track.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.025 }}>
                      <Card className="overflow-hidden border-border/70 bg-background/55">
                        <CardContent className="flex flex-col gap-3 p-3">
                          <CoverArt src={track.albumArtPath} title={track.album ?? track.title} priority={index === 0} sizes="(max-width: 640px) 44vw, 180px" />
                          <div className="flex min-w-0 flex-col gap-1"><span className="truncate text-sm font-semibold" title={track.title}>{track.title}</span><span className="truncate text-xs text-muted-foreground" title={track.artist}>{track.artist}</span></div>
                          <span className="inline-flex items-center gap-1.5 truncate text-[10px] text-muted-foreground"><Music2 aria-hidden="true" className="size-3 shrink-0" />{track.album ?? "Single"}</span>
                        </CardContent>
                      </Card>
                    </motion.div>
                  ))}
                </div>
              ) : (
                <div className="rounded-2xl border border-dashed border-border px-6 py-12 text-center">
                  <ScanSearch aria-hidden="true" className="mx-auto size-6 text-muted-foreground" />
                  <p className="mt-3 text-sm font-medium">{query ? "No tracks found" : "Search the host catalog"}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{query ? "Try a different artist, title, or album." : "Enter a track, artist, album, or genre to browse indexed music."}</p>
                </div>
              )}
            </CardContent>
          </Card>

          <div className="flex flex-col gap-5">
            <Card className="border-border/80 bg-card/75">
              <CardHeader>
                <div className="flex items-center gap-3">
                  <span className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary"><FolderOpen aria-hidden="true" className="size-4" /></span>
                  <div className="flex flex-col gap-1"><CardTitle className="text-lg">Scan a music folder</CardTitle><CardDescription>Runs on the host machine</CardDescription></div>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-5">
                <div className="flex items-start gap-3 rounded-xl border border-border/70 bg-background/50 p-3.5">
                  <HardDrive aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                  <p className="text-xs leading-5 text-muted-foreground">Use the absolute path to the music directory on the computer running Spring Boot. The browser cannot browse the host filesystem directly.</p>
                </div>
                <form onSubmit={handleScan} className="flex flex-col gap-4">
                  <FieldGroup>
                    <Field>
                      <FieldLabel htmlFor="music-library-path">Folder path</FieldLabel>
                      <Input id="music-library-path" value={libraryPath} onChange={(event) => setLibraryPath(event.target.value)} placeholder="C:\Users\YourName\Music" required />
                      <FieldDescription>Example: C:\Users\YourName\Music</FieldDescription>
                    </Field>
                  </FieldGroup>
                  <Button type="submit" disabled={!session || !isAdmin || scanning || !libraryPath.trim()} className="w-full">
                    {scanning ? <LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" /> : <ScanSearch aria-hidden="true" data-icon="inline-start" />}
                    {scanning ? "Scanning folder…" : "Scan library"}
                  </Button>
                </form>
                {lastScan ? (
                  <div className="flex items-center gap-2 rounded-xl border border-emerald-400/15 bg-emerald-400/5 px-3.5 py-3 text-xs text-emerald-200">
                    <Check aria-hidden="true" className="size-4" />{scanCount?.toLocaleString()} tracks scanned at {lastScan}.
                  </div>
                ) : (
                  <span className="text-[11px] leading-5 text-muted-foreground">{isAdmin ? "A scan refreshes the server-side music index." : "An authenticated ADMIN session is required to scan."}</span>
                )}
              </CardContent>
            </Card>

            <ArtworkReviewPanel />

            <Card className="border-border/70 bg-card/55">
              <CardContent className="flex items-center gap-3.5 p-4">
                <span className="flex size-10 items-center justify-center rounded-xl bg-secondary text-muted-foreground"><Database aria-hidden="true" className="size-4" /></span>
                <div className="flex min-w-0 flex-1 flex-col gap-1"><span className="text-sm font-semibold">Index status</span><span className="text-xs text-muted-foreground">{lastScan ? `Last scan ${lastScan}` : statsQuery.isLoading ? "Checking host catalog…" : "Connected to host catalog"}</span></div>
                <Badge variant={lastScan ? "secondary" : "outline"} className="shrink-0 text-[10px]">{lastScan ? "Updated" : statsQuery.isLoading ? "Checking" : "Ready"}</Badge>
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  )
}
