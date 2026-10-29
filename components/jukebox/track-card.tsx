import { Music2, Plus, LoaderCircle } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter } from "@/components/ui/card"
import { CoverArt } from "@/components/jukebox/cover-art"
import { formatDuration, type Track } from "@/lib/jukebox/types"

type TrackCardProps = {
  track: Track
  disabled?: boolean
  busy?: boolean
  queued?: boolean
  needsCredits?: boolean
  onQueue?: (track: Track) => void
}

export function TrackCard({ track, disabled = false, busy = false, queued = false, needsCredits = false, onQueue }: TrackCardProps) {
  return (
    <Card className="group overflow-hidden border-border/70 bg-card/70 transition-colors hover:border-primary/25 hover:bg-card">
      <CardContent className="flex flex-col gap-3 p-3.5 sm:p-4">
        <div className="relative">
          <CoverArt
            src={track.albumArtPath}
                title={track.album ?? track.title}
            className="w-full rounded-xl"
            sizes="(max-width: 640px) 42vw, (max-width: 1024px) 32vw, 220px"
          />
          <span className="absolute right-2.5 top-2.5 rounded-full border border-white/10 bg-black/50 px-2 py-1 text-[10px] font-medium text-white/85 backdrop-blur-md">
            {formatDuration(track.durationSeconds)}
          </span>
        </div>
        <div className="flex min-w-0 flex-col gap-1">
          <h3 className="truncate text-sm font-semibold tracking-tight text-foreground sm:text-base" title={track.title}>{track.title}</h3>
          <p className="truncate text-xs text-muted-foreground sm:text-sm" title={track.artist}>{track.artist}</p>
        </div>
        {track.genre && <Badge variant="secondary" className="w-fit max-w-full truncate text-[10px] font-medium">{track.genre}</Badge>}
      </CardContent>
      <CardFooter className="flex items-center justify-between border-t border-border/60 px-3.5 py-3 sm:px-4">
        <span className="inline-flex min-w-0 items-center gap-1.5 truncate text-[11px] text-muted-foreground">
          <Music2 aria-hidden="true" className="size-3.5 shrink-0" />
          <span className="truncate">{track.album ?? "Single"}</span>
        </span>
        <Button
          type="button"
          size="sm"
          variant={queued ? "secondary" : "outline"}
          disabled={disabled || busy || queued}
          onClick={() => onQueue?.(track)}
          aria-label={queued ? `${track.title} is already queued` : needsCredits ? "Ask the host for credits" : `Queue ${track.title} for one credit`}
          className="shrink-0"
        >
          {busy ? <LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" /> : <Plus aria-hidden="true" data-icon="inline-start" />}
          {queued ? "Queued" : busy ? "Adding" : needsCredits ? "Ask host" : "Queue · 1"}
        </Button>
      </CardFooter>
    </Card>
  )
}
