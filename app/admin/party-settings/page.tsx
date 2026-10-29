import type { Metadata } from "next"
import { PartySettings } from "@/components/jukebox/party-settings"

export const metadata: Metadata = {
  title: "Party settings",
  description: "Manage party rules, guest access, and credit balances.",
}

export default function PartySettingsPage() {
  return <PartySettings />
}
