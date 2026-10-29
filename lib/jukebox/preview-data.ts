import type { NowPlaying, PartyGuest, QueueItem, Track } from "@/lib/jukebox/types"

export const previewTracks: Track[] = [
  {
    id: "preview-track-1",
    title: "Nothing on the Radio",
    artist: "Luna Vale",
    album: "After Hours",
    durationSeconds: 218,
    albumArtPath: "/albums/after-hours.png",
    genre: "Indie pop",
  },
  {
    id: "preview-track-2",
    title: "Blue in the Morning",
    artist: "Juno & the Tide",
    album: "Blue Hour",
    durationSeconds: 194,
    albumArtPath: "/albums/blue-hour.png",
    genre: "Dream pop",
  },
  {
    id: "preview-track-3",
    title: "Golden Again",
    artist: "Theo Gray",
    album: "Sunroom",
    durationSeconds: 231,
    albumArtPath: "/albums/golden-hour.png",
    genre: "Electronic",
  },
  {
    id: "preview-track-4",
    title: "Violet Season",
    artist: "Mira Sol",
    album: "Neon Bloom",
    durationSeconds: 207,
    albumArtPath: "/albums/violet-disco.png",
    genre: "Alt R&B",
  },
  {
    id: "preview-track-5",
    title: "The Long Way Home",
    artist: "Lena & the Afterparty",
    album: "Soft Focus",
    durationSeconds: 246,
    albumArtPath: "/albums/after-hours.png",
    genre: "Indie",
  },
  {
    id: "preview-track-6",
    title: "Stay Until the Lights",
    artist: "Nova June",
    album: "Night Bloom",
    durationSeconds: 202,
    albumArtPath: "/albums/violet-disco.png",
    genre: "Pop",
  },
]

export const previewNowPlaying: NowPlaying = {
  track: previewTracks[0],
  progressSeconds: 82,
  isPlaying: true,
  requestedBy: { username: "maya.r" },
}

export const previewQueue: QueueItem[] = [
  {
    id: "preview-queue-1",
    track: previewTracks[1],
    requestedBy: { username: "sam.w" },
    status: "PENDING",
    position: 1,
    createdAt: "2026-10-26T20:38:00.000Z",
  },
  {
    id: "preview-queue-2",
    track: previewTracks[2],
    requestedBy: { username: "maya.r" },
    status: "PENDING",
    position: 2,
    createdAt: "2026-10-26T20:40:00.000Z",
  },
  {
    id: "preview-queue-3",
    track: previewTracks[3],
    requestedBy: { username: "alex.k" },
    status: "PENDING",
    position: 3,
    createdAt: "2026-10-26T20:42:00.000Z",
  },
  {
    id: "preview-queue-4",
    track: previewTracks[4],
    requestedBy: { username: "jo.lee" },
    status: "PENDING",
    position: 4,
    createdAt: "2026-10-26T20:45:00.000Z",
  },
]

export const previewGuests: PartyGuest[] = [
  { username: "maya.r", credits: 4, distanceFromParty: 18, joinedAt: "2026-10-26T20:10:00.000Z" },
  { username: "sam.w", credits: 2, distanceFromParty: 26, joinedAt: "2026-10-26T20:15:00.000Z" },
  { username: "alex.k", credits: 5, distanceFromParty: 11, joinedAt: "2026-10-26T20:22:00.000Z" },
  { username: "jo.lee", credits: 1, distanceFromParty: 43, joinedAt: "2026-10-26T20:29:00.000Z" },
]

export function filterPreviewTracks(query: string) {
  const normalized = query.trim().toLowerCase()
  if (!normalized) return previewTracks
  return previewTracks.filter((track) =>
    [track.title, track.artist, track.album, track.genre]
      .some((value) => value?.toLowerCase().includes(normalized)),
  )
}
