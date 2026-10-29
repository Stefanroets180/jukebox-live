"use client"

import { AudioLines, Clock3 } from "lucide-react"
import { Progress } from "@/components/ui/progress"
import { CoverArt } from "@/components/jukebox/cover-art"
import { formatDuration, type NowPlaying } from "@/lib/jukebox/types"

type NowPlayingBarProps = {
  nowPlaying: NowPlaying | null
  isPreview: boolean
}

export function NowPlayingBar({ nowPlaying, isPreview }: NowPlayingBarProps) {
  if (!nowPlaying) {
    return (
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border/80 bg-card/95 px-4 py-4 backdrop-blur-xl">
        <div className="mx-auto flex max-w-[1440px] items-center gap-3 text-sm text-muted-foreground">
          <AudioLines aria-hidden="true" className="size-4 text-primary" />Nothing is playing right now.
        </div>
      </div>
    )
  }

  const duration = nowPlaying.track.durationSeconds ?? 210
  const progress = Math.min(100, Math.max(0, (nowPlaying.progressSeconds / duration) * 100))

  return (
    <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border/80 bg-card/95 px-3 py-3 shadow-[0_-12px_45px_-30px_rgba(0,0,0,0.85)] backdrop-blur-xl sm:px-6">
      <div className="mx-auto grid max-w-[1440px] grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)_auto] items-center gap-3 sm:gap-6">
        <div className="flex min-w-0 items-center gap-3">
          <CoverArt
            src={nowPlaying.track.albumArtPath}
                title={nowPlaying.track.album ?? nowPlaying.track.title}
            className="size-11 shrink-0 rounded-lg sm:size-12"
            sizes="48px"
          />
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="truncate text-sm font-semibold" title={nowPlaying.track.title}>{nowPlaying.track.title}</span>
            <span className="truncate text-xs text-muted-foreground">{nowPlaying.track.artist}</span>
          </div>
        </div>
        <div className="hidden min-w-0 flex-col gap-2 sm:flex">
          <div className="flex items-center justify-center gap-2 text-[10px] font-semibold tracking-[0.16em] text-primary uppercase">
            {nowPlaying.isPlaying ? <span className="flex gap-0.5" aria-hidden="true">{[0, 1, 2, 3, 4].map((item) => <span key={item} className="visualizer-bar h-3 w-0.5 rounded-full bg-primary" />)}</span> : <Clock3 aria-hidden="true" className="size-3" />}
            Now playing
          </div>
          <Progress value={progress} aria-label={`${Math.floor(progress)} percent of ${nowPlaying.track.title} played`} className="h-1.5" />
          <div className="flex justify-between text-[10px] tabular-nums text-muted-foreground">
            <span>{formatDuration(nowPlaying.progressSeconds)}</span>
            <span>{formatDuration(duration)}</span>
          </div>
        </div>
        <div className="flex items-center justify-end gap-2 text-xs text-muted-foreground">
          <AudioLines aria-hidden="true" className="size-4 text-primary" />
          <span className="hidden sm:inline">Room audio</span>
        </div>
      </div>
      <div className="mt-3 sm:hidden"><Progress value={progress} aria-label={`${Math.floor(progress)} percent of ${nowPlaying.track.title} played`} className="h-1" /></div>
    </div>
  )
}
