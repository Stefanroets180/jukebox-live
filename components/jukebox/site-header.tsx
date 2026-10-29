"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import {
  AudioLines,
  Disc3,
  Headphones,
  LibraryBig,
  Settings2,
  Sparkles,
} from "lucide-react"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { buttonVariants } from "@/components/ui/button"
import { useJukebox } from "@/lib/jukebox/context"
import { cn } from "@/lib/utils"

const navigation = [
  { href: "/dashboard", label: "Guest room", mobileLabel: "Room", icon: Headphones },
  { href: "/host", label: "Host display", mobileLabel: "Host", icon: Disc3 },
  { href: "/player", label: "Dedicated player", mobileLabel: "Player", icon: AudioLines },
  { href: "/admin/library", label: "Music library", mobileLabel: "Library", icon: LibraryBig },
  { href: "/admin/party-settings", label: "Party settings", mobileLabel: "Settings", icon: Settings2 },
]

export function SiteHeader() {
  const pathname = usePathname()
  const { session, realtimeStatus } = useJukebox()

  return (
    <header className="sticky top-0 z-40 border-b border-border/70 bg-background/85 backdrop-blur-xl">
      <div className="mx-auto flex min-h-16 max-w-[1440px] items-center justify-between gap-4 px-4 sm:px-6 xl:px-10">
        <Link href="/join" className="flex shrink-0 items-center gap-2.5" aria-label="JukeBox Live home">
          <span className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-[0_0_28px_-8px_var(--primary)]">
            <Disc3 aria-hidden="true" className="size-5" />
          </span>
          <span className="hidden text-[15px] font-semibold tracking-tight sm:inline">JukeBox <span className="text-muted-foreground">Live</span></span>
        </Link>

        <nav aria-label="Main navigation" className="hidden items-center gap-1 lg:flex">
          {navigation.map(({ href, label, icon: Icon }) => {
            const active = pathname === href
            return (
              <Link
                key={href}
                href={href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  active ? "bg-secondary text-foreground" : "text-muted-foreground hover:bg-secondary/70 hover:text-foreground",
                )}
              >
                <Icon aria-hidden="true" className="size-4" />
                {label}
              </Link>
            )
          })}
        </nav>

        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          {session ? (
            <div className="hidden items-center gap-2 rounded-full border border-border bg-card px-3 py-1.5 text-xs sm:flex">
              <span className={cn("size-1.5 rounded-full", realtimeStatus === "connected" ? "bg-emerald-400" : "bg-muted-foreground")} />
              <span className="text-muted-foreground">{session.partyCode}</span>
              <span className="font-semibold text-primary">{session.credits} cr</span>
            </div>
          ) : (
            <span className="hidden items-center gap-1.5 text-xs text-muted-foreground sm:flex">
              <Sparkles aria-hidden="true" className="size-3.5 text-primary" />
              One room. One soundtrack.
            </span>
          )}
          {session ? (
            <Avatar className="size-9 border border-border">
              <AvatarFallback className="bg-secondary text-xs font-semibold text-foreground">
                {session.username.slice(0, 2).toUpperCase()}
              </AvatarFallback>
            </Avatar>
          ) : (
            <Link href="/join" className={buttonVariants({ size: "sm" })}>
              Join a party
            </Link>
          )}
        </div>
      </div>

      <nav aria-label="Mobile navigation" className="grid grid-cols-5 gap-1 border-t border-border/60 px-2 py-2 lg:hidden">
        {navigation.map(({ href, label, mobileLabel, icon: Icon }) => {
          const active = pathname === href
          return (
            <Link
              key={href}
              href={href}
              aria-label={label}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-w-0 flex-col items-center justify-center gap-1 rounded-lg px-1 py-2 text-[10px] font-medium transition-colors",
                active ? "bg-secondary text-foreground" : "text-muted-foreground hover:bg-secondary/70 hover:text-foreground",
              )}
            >
              <Icon aria-hidden="true" className="size-4" />
              <span className="truncate">{mobileLabel}</span>
            </Link>
          )
        })}
      </nav>
    </header>
  )
}
