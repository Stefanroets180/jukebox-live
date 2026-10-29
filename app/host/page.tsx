import type { Metadata } from "next"
import { HostDisplay } from "@/components/jukebox/host-display"

export const metadata: Metadata = {
  title: "Host display",
  description: "A real-time big-screen display for the party queue, current track, and guest QR code.",
}

export default function HostPage() {
  return <HostDisplay />
}
