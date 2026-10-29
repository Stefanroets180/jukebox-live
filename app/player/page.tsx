import type { Metadata } from "next"
import { DedicatedPlayer } from "@/components/jukebox/dedicated-player"

export const metadata: Metadata = {
  title: "Dedicated Player",
  description: "The exclusive host player for streaming a JukeBox Live party from the host's local music library.",
}

export default function PlayerPage() {
  return <DedicatedPlayer />
}
