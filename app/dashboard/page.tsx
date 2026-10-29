import type { Metadata } from "next"
import { GuestDashboard } from "@/components/jukebox/guest-dashboard"

export const metadata: Metadata = {
  title: "Guest room",
  description: "Search the party library and add the next song to the live queue.",
}

export default function DashboardPage() {
  return <GuestDashboard />
}
