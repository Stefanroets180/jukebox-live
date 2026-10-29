import type { Metadata } from "next"
import { LibraryManager } from "@/components/jukebox/library-manager"

export const metadata: Metadata = {
  title: "Music library",
  description: "Search and scan the host's local music library for JukeBox Live.",
}

export default function LibraryPage() {
  return <LibraryManager />
}
