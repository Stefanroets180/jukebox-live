import { useState } from "react"
import { AnimatePresence, motion } from "framer-motion"
import { ArrowDown, ArrowUp, ArrowUpRight, Clock3, GripVertical, ListMusic, SkipForward } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { CoverArt } from "@/components/jukebox/cover-art"
import { cn } from "@/lib/utils"
import { formatDuration, type QueueItem } from "@/lib/jukebox/types"

function formatQueueTime(createdAt?: string) {
  if (!createdAt) return "Just added"
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000))
  if (minutes < 1) return "Just added"
  return `${minutes}m ago`
}

type QueuePanelProps = {
  items: QueueItem[]
  isPreview: boolean
  showHostControls?: boolean
  canReorder?: boolean
  skippingId?: string | null
  onSkip?: (item: QueueItem) => void
  onReorder?: (items: QueueItem[]) => void
}

export function QueuePanel({ items, isPreview, showHostControls = false, canReorder = false, skippingId, onSkip, onReorder }: QueuePanelProps) {
  const [draggedId, setDraggedId] = useState<string | null>(null)

  function reorderItem(sourceId: string, targetId: string) {
    const sourceIndex = items.findIndex((item) => item.id === sourceId)
    const targetIndex = items.findIndex((item) => item.id === targetId)
    if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) return

    const nextItems = [...items]
    const [movedItem] = nextItems.splice(sourceIndex, 1)
    if (!movedItem) return
    nextItems.splice(targetIndex, 0, movedItem)
    onReorder?.(nextItems)
  }

  function moveItem(itemId: string, direction: -1 | 1) {
    const currentIndex = items.findIndex((item) => item.id === itemId)
    const destinationIndex = currentIndex + direction
    if (currentIndex < 0 || destinationIndex < 0 || destinationIndex >= items.length) return

    const nextItems = [...items]
    const [movedItem] = nextItems.splice(currentIndex, 1)
    if (!movedItem) return
    nextItems.splice(destinationIndex, 0, movedItem)
    onReorder?.(nextItems)
  }

  return (
    <Card className="overflow-hidden border-border/80 bg-card/85">
      <CardHeader className="flex flex-row items-start justify-between gap-4 border-b border-border/60 px-5 py-5">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <CardTitle className="text-lg tracking-tight">Up next</CardTitle>
            <Badge variant="secondary" className="rounded-full px-2 text-[10px]">{items.length}</Badge>
          </div>
          <CardDescription className="text-xs">{canReorder ? "Drag or use arrows to reorder · this API has no save route" : "The room's next picks"}</CardDescription>
        </div>
        <div className="flex items-center gap-2">
          {isPreview && <Badge variant="outline" className="text-[10px]">Sample</Badge>}
          <span aria-label={isPreview ? "Preview queue" : "Live queue"} className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <ListMusic aria-hidden="true" className="size-4" />
          </span>
        </div>
      </CardHeader>
      <CardContent className="p-0">
        {items.length === 0 ? (
          <Empty className="px-6 py-10">
            <EmptyHeader>
              <EmptyMedia variant="icon"><ListMusic aria-hidden="true" /></EmptyMedia>
              <EmptyTitle>The queue is wide open</EmptyTitle>
              <EmptyDescription>Search the library and add the first track to set the mood.</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <ol aria-label="Upcoming tracks" aria-live="polite" aria-relevant="additions removals" className="flex flex-col divide-y divide-border/60">
            <AnimatePresence initial={false}>
              {items.map((item, index) => (
                <motion.li
                  key={item.id}
                  layout
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, x: 18, height: 0, marginTop: 0, marginBottom: 0 }}
                  transition={{ duration: 0.22, ease: "easeOut" }}
                  onDragOver={(event) => { if (canReorder) event.preventDefault() }}
                  onDrop={(event) => {
                    if (!canReorder) return
                    event.preventDefault()
                    const sourceId = draggedId ?? event.dataTransfer.getData("text/plain")
                    if (sourceId) reorderItem(sourceId, item.id)
                    setDraggedId(null)
                  }}
                  onDragEnd={() => setDraggedId(null)}
                  className={cn("flex items-center gap-3 px-4 py-3.5 sm:px-5", draggedId === item.id && "opacity-45")}
                >
                  <span className="w-4 shrink-0 text-right font-mono text-xs text-muted-foreground">{String(index + 1).padStart(2, "0")}</span>
                  <CoverArt
                    src={item.track.albumArtPath}
                                      title={item.track.album ?? item.track.title}
                    priority={index === 0}
                    className="size-12 shrink-0 rounded-lg"
                    sizes="48px"
                  />
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="truncate text-sm font-medium" title={item.track.title}>{item.track.title}</span>
                    <span className="truncate text-xs text-muted-foreground">{item.track.artist} <span className="text-border">·</span> {item.requestedBy.username}</span>
                    <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground sm:hidden">
                      <Clock3 aria-hidden="true" className="size-3" />{formatQueueTime(item.createdAt)}
                    </span>
                  </div>
                  <span className="hidden shrink-0 text-xs tabular-nums text-muted-foreground sm:block">{formatDuration(item.track.durationSeconds)}</span>
                  <span className="hidden shrink-0 text-[11px] text-muted-foreground md:block">{formatQueueTime(item.createdAt)}</span>
                  {canReorder && (
                    <div className="flex shrink-0 items-center gap-0.5">
                      <span
                        aria-hidden="true"
                        draggable
                        onDragStart={(event) => {
                          event.dataTransfer.effectAllowed = "move"
                          event.dataTransfer.setData("text/plain", item.id)
                          setDraggedId(item.id)
                        }}
                        className="hidden cursor-grab text-muted-foreground active:cursor-grabbing sm:block"
                        title="Drag to reorder"
                      >
                        <GripVertical />
                      </span>
                      <div className="flex flex-col">
                        <Button type="button" size="icon-sm" variant="ghost" disabled={index === 0} onClick={() => moveItem(item.id, -1)} aria-label={`Move ${item.track.title} up`}>
                          <ArrowUp aria-hidden="true" />
                        </Button>
                        <Button type="button" size="icon-sm" variant="ghost" disabled={index === items.length - 1} onClick={() => moveItem(item.id, 1)} aria-label={`Move ${item.track.title} down`}>
                          <ArrowDown aria-hidden="true" />
                        </Button>
                      </div>
                    </div>
                  )}
                  {showHostControls && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      disabled={isPreview || item.id.startsWith("preview-") || skippingId === item.id}
                      onClick={() => onSkip?.(item)}
                      aria-label={`Skip ${item.track.title}`}
                      title={isPreview ? "Connect the host API to manage the live queue" : "Skip this request"}
                    >
                      <SkipForward aria-hidden="true" />
                    </Button>
                  )}
                  {!showHostControls && <ArrowUpRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground/50" />}
                </motion.li>
              ))}
            </AnimatePresence>
          </ol>
        )}
      </CardContent>
    </Card>
  )
}
