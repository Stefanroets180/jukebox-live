"use client"

import { Client } from "@stomp/stompjs"
import SockJS from "sockjs-client"
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react"
import { getApiBaseUrl } from "@/lib/jukebox/api"
import type { AuthSession, NowPlaying, QueueItem, UserRole } from "@/lib/jukebox/types"

const SESSION_KEY = "jukebox-live-session-v1"

type RealtimeStatus = "idle" | "connecting" | "connected" | "offline"

type SessionContextValue = {
  session: AuthSession | null
  hydrated: boolean
  realtimeStatus: RealtimeStatus
  liveQueue: QueueItem[] | null
  liveNowPlaying: NowPlaying | null | undefined
  isAdmin: boolean
  authenticate: (input: {
    token: string
    partyId: string
    partyCode: string
    username: string
    credits: number
  }) => void
  updateCredits: (credits: number) => void
  updateParty: (party: { id: string; code: string }) => void
  logout: () => void
  resumePlayback?: () => void
}

const SessionContext = createContext<SessionContextValue | null>(null)

function readJwtClaims(token: string) {
  try {
    const payload = token.split(".")[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/")
    return JSON.parse(window.atob(normalized)) as Record<string, unknown>
  } catch {
    return null
  }
}

function readStoredSession(raw: string): AuthSession | null {
  try {
    const value = JSON.parse(raw) as Partial<AuthSession>
    if (
      typeof value.token !== "string" ||
      typeof value.partyId !== "string" ||
      typeof value.partyCode !== "string" ||
      typeof value.username !== "string" ||
      typeof value.credits !== "number"
    ) return null

    return { ...value, role: value.role === "ADMIN" ? "ADMIN" : "USER" } as AuthSession
  } catch {
    return null
  }
}

export function JukeboxProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [hydrated, setHydrated] = useState(false)
  const [realtimeStatus, setRealtimeStatus] = useState<RealtimeStatus>("idle")
  const [liveQueue, setLiveQueue] = useState<QueueItem[] | null>(null)
  const [liveNowPlaying, setLiveNowPlaying] = useState<NowPlaying | null | undefined>(undefined)

  useEffect(() => {
    const raw = window.localStorage.getItem(SESSION_KEY)
    if (raw) {
      const saved = readStoredSession(raw)
      if (saved) setSession(saved)
      else window.localStorage.removeItem(SESSION_KEY)
    }
    setHydrated(true)
  }, [])

  const persistSession = useCallback((next: AuthSession | null) => {
    setSession(next)
    if (next) window.localStorage.setItem(SESSION_KEY, JSON.stringify(next))
    else window.localStorage.removeItem(SESSION_KEY)
  }, [])

  const authenticate = useCallback((input: {
    token: string
    partyId: string | null
    partyCode: string
    username: string
    credits: number
  }) => {
    const claims = readJwtClaims(input.token)
    const roleClaim = claims?.role
    const rolesClaim = claims?.roles
    const role: UserRole = roleClaim === "ADMIN" || (Array.isArray(rolesClaim) && rolesClaim.includes("ADMIN"))
      ? "ADMIN"
      : "USER"

    persistSession({
      ...input,
      userId: typeof claims?.sub === "string" ? claims.sub : undefined,
      role,
    })
  }, [persistSession])

  const updateCredits = useCallback((credits: number) => {
    setSession((current) => {
      if (!current) return null
      const next = { ...current, credits }
      window.localStorage.setItem(SESSION_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  const updateParty = useCallback((party: { id: string; code: string }) => {
    setSession((current) => {
      if (!current) return null
      const next = { ...current, partyId: party.id, partyCode: party.code }
      window.localStorage.setItem(SESSION_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  const logout = useCallback(() => persistSession(null), [persistSession])

  useEffect(() => {
    if (!hydrated || !session?.partyId) return

    let active = true
    setRealtimeStatus("connecting")
    setLiveQueue(null)
    setLiveNowPlaying(undefined)

    const sessionToken = session.token
    const partyId = session.partyId
    const userId = session.userId

    const client = new Client({
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectHeaders: {
        Authorization: `Bearer ${sessionToken}`,
        "X-Party-ID": partyId,
      },
      webSocketFactory: () => new SockJS(`${getApiBaseUrl()}/ws`) as unknown as WebSocket,
      onConnect: () => {
        if (!active) return
        setRealtimeStatus("connected")
        client.subscribe(`/topic/queue/${partyId}`, (message) => {
          try {
            const queue = JSON.parse(message.body) as QueueItem[]
            if (Array.isArray(queue)) setLiveQueue(queue)
          } catch {
            setRealtimeStatus("offline")
          }
        })
        client.subscribe(`/topic/playback/${partyId}`, (message) => {
          try {
            const nowPlaying = JSON.parse(message.body) as NowPlaying | null
            setLiveNowPlaying(nowPlaying)
          } catch {
            setLiveNowPlaying(undefined)
          }
        })
        if (userId) {
          client.subscribe(`/topic/credits/${userId}`, (message) => {
            try {
              const update = JSON.parse(message.body) as { credits?: number } | number
              const credits = typeof update === "number" ? update : update.credits
              if (typeof credits === "number" && Number.isFinite(credits)) updateCredits(credits)
            } catch {
              return
            }
          })
        }
      },
      onStompError: () => {
        if (active) setRealtimeStatus("offline")
      },
      onWebSocketClose: () => {
        if (active) setRealtimeStatus("offline")
      },
      onWebSocketError: () => {
        if (active) setRealtimeStatus("offline")
      },
    })

    client.activate()
    return () => {
      active = false
      setRealtimeStatus("idle")
      void client.deactivate()
    }
  }, [hydrated, session?.token, session?.partyId, session?.userId, updateCredits])

  const value = useMemo<SessionContextValue>(() => ({
    session,
    hydrated,
    realtimeStatus,
    liveQueue,
    liveNowPlaying,
    isAdmin: session?.role === "ADMIN",
    authenticate,
    updateCredits,
    updateParty,
    logout,
    resumePlayback: undefined,
  }), [session, hydrated, realtimeStatus, liveQueue, liveNowPlaying, authenticate, updateCredits, updateParty, logout])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useJukebox() {
  const context = useContext(SessionContext)
  if (!context) throw new Error("useJukebox must be used within JukeboxProvider")
  return context
}
