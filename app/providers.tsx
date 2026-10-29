"use client"

import type { ReactNode } from "react"
import { ThemeProvider } from "next-themes"
import { MotionConfig } from "framer-motion"
import { Toaster } from "@/components/ui/sonner"
import { JukeboxProvider } from "@/lib/jukebox/context"
import { GlobalAudioPlayer } from "@/components/jukebox/global-audio-player"

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="dark" enableSystem={false} disableTransitionOnChange>
      <MotionConfig reducedMotion="user">
        <JukeboxProvider>
          {children}
          <GlobalAudioPlayer />
          <Toaster position="top-right" richColors />
        </JukeboxProvider>
      </MotionConfig>
    </ThemeProvider>
  )
}
