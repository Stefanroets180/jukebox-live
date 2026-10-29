"use client"

import Image from "next/image"
import { useEffect, useState } from "react"
import { Disc3 } from "lucide-react"
import { cn } from "@/lib/utils"
import { getCoverImageSrc } from "@/lib/jukebox/types"

type CoverArtProps = {
  src?: string | null
  title: string
  className?: string
  imageClassName?: string
  priority?: boolean
  sizes?: string
}

export function CoverArt({
  src,
  title,
  className,
  imageClassName,
  priority = false,
  sizes = "(max-width: 768px) 40vw, 160px",
}: CoverArtProps) {
  const imageSrc = getCoverImageSrc(src)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)

  useEffect(() => {
    setFailedSrc(null)
  }, [imageSrc])

  const hasArtwork = Boolean(imageSrc && failedSrc !== imageSrc)

  return (
    <div className={cn("relative aspect-square overflow-hidden rounded-xl bg-secondary", className)}>
      {hasArtwork ? (
        <Image
          src={imageSrc}
          alt={`${title} album artwork`}
          fill
          sizes={sizes}
          className={cn("object-cover", imageClassName)}
          loading={priority ? "eager" : "lazy"}
          unoptimized={imageSrc.startsWith("https://")}
          referrerPolicy="no-referrer"
          onError={() => setFailedSrc(imageSrc)}
        />
      ) : (
        <div
          role="img"
          aria-label={`No artwork available for ${title}`}
          className="flex size-full flex-col items-center justify-center gap-3 bg-gradient-to-br from-secondary via-muted to-background text-muted-foreground"
        >
          <Disc3 aria-hidden="true" className="size-12 opacity-70" />
          <span className="text-[10px] font-medium tracking-[0.14em] uppercase">Artwork unavailable</span>
        </div>
      )}
    </div>
  )
}
