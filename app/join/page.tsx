import Image from "next/image"
import { ArrowRight, AudioLines, CircleDot, Heart, MapPin, Sparkles, Wifi } from "lucide-react"
import { JoinForm } from "@/components/jukebox/join-form"
import { SiteHeader } from "@/components/jukebox/site-header"

export default async function JoinPage({
  searchParams,
}: {
  searchParams: Promise<{ party?: string; mode?: string }>
}) {
  const params = await searchParams
  const initialPartyCode = params.party?.trim().toUpperCase() ?? ""
  const initialMode = params.mode === "admin" ? "admin" : params.mode === "register" ? "register" : "login"

  // Don't pass party code when in admin mode
  const initialPartyCodeForMode = initialMode === "admin" ? "" : initialPartyCode

  return (
    <div className="min-h-screen overflow-hidden bg-background">
      <SiteHeader />
      <main className="relative mx-auto grid min-h-[calc(100svh-7rem)] max-w-[1440px] items-center gap-12 px-4 py-10 sm:px-6 lg:grid-cols-[1.05fr_0.95fr] lg:gap-14 lg:px-10 lg:py-14">
        <div aria-hidden="true" className="pointer-events-none absolute -left-48 top-12 size-[34rem] rounded-full bg-primary/10 blur-[120px]" />
        <section className="relative flex flex-col gap-8 lg:py-8">
          <div className="flex flex-col gap-6">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3 py-1.5 text-xs font-medium text-primary">
              <span className="relative flex size-2">
                <span className="absolute inline-flex size-full animate-ping rounded-full bg-primary opacity-40" />
                <span className="relative inline-flex size-2 rounded-full bg-primary" />
              </span>
              The room has a soundtrack
            </div>
            <h1 className="max-w-2xl text-4xl leading-[1.04] font-semibold tracking-[-0.045em] text-foreground sm:text-5xl lg:text-6xl xl:text-7xl">
              Make the next song <span className="text-primary">a group decision.</span>
            </h1>
            <p className="max-w-xl text-base leading-7 text-muted-foreground sm:text-lg sm:leading-8">
              Join your host&apos;s live queue, find your favorite track, and help shape the soundtrack for everyone in the room.
            </p>
          </div>

          <div className="relative grid min-h-[290px] grid-cols-[1.05fr_0.95fr] gap-3 sm:min-h-[360px] sm:gap-4">
            <div className="relative overflow-hidden rounded-[1.75rem] border border-white/10 bg-card shadow-2xl shadow-black/30">
              <Image src="/albums/after-hours.png" alt="Abstract violet and magenta after-hours album artwork" fill priority sizes="(max-width: 768px) 60vw, 420px" className="object-cover" />
              <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/10 to-transparent" />
              <div className="absolute inset-x-5 bottom-5 flex items-end justify-between gap-4 sm:inset-x-6 sm:bottom-6">
                <div className="flex flex-col gap-1">
                  <span className="text-[10px] font-semibold tracking-[0.22em] text-white/70 uppercase">Tonight&apos;s soundtrack</span>
                  <span className="text-lg font-semibold tracking-tight text-white sm:text-xl">After Hours</span>
                  <span className="text-sm text-white/70">Luna Vale</span>
                </div>
                <span className="flex size-10 items-center justify-center rounded-full border border-white/20 bg-white/10 text-white backdrop-blur-md">
                  <AudioLines aria-hidden="true" className="size-4" />
                </span>
              </div>
            </div>
            <div className="flex flex-col gap-3 sm:gap-4">
              <div className="relative min-h-0 flex-1 overflow-hidden rounded-[1.5rem] border border-white/10 bg-card">
                <Image src="/albums/golden-hour.png" alt="Warm amber sunset album artwork" fill sizes="(max-width: 768px) 40vw, 280px" className="object-cover" />
                <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
                <span className="absolute bottom-3 left-3 rounded-full border border-white/15 bg-black/30 px-2.5 py-1 text-[10px] font-medium text-white/90 backdrop-blur-md sm:bottom-4 sm:left-4 sm:text-xs">One shared queue</span>
              </div>
              <div className="flex min-h-0 flex-1 flex-col justify-between rounded-[1.5rem] border border-border/80 bg-card/80 p-4 sm:p-5">
                <div className="flex items-center justify-between gap-2">
                  <span className="flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary"><Heart aria-hidden="true" className="size-4" /></span>
                  <span className="flex items-center gap-1 text-[10px] font-medium text-muted-foreground sm:text-xs"><CircleDot aria-hidden="true" className="size-3 text-emerald-400" />LIVE ROOM</span>
                </div>
                <div className="flex flex-col gap-1.5">
                  <span className="text-lg font-semibold tracking-tight sm:text-xl">Your pick, next up.</span>
                  <span className="text-xs leading-5 text-muted-foreground sm:text-sm">Spend a credit. Set the mood.</span>
                  <span className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-primary">See how it works <ArrowRight aria-hidden="true" className="size-3.5" /></span>
                </div>
                <Sparkles aria-hidden="true" className="absolute right-7 top-[52%] size-4 text-primary/70" />
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-x-6 gap-y-3 border-t border-border/70 pt-5 text-xs text-muted-foreground sm:text-sm">
            <span className="inline-flex items-center gap-2"><Wifi aria-hidden="true" className="size-4 text-primary" />Same Wi-Fi as your host</span>
            <span className="inline-flex items-center gap-2"><MapPin aria-hidden="true" className="size-4 text-primary" />Location verified for the room</span>
          </div>
        </section>

        <section className="relative mx-auto w-full max-w-[520px] lg:ml-auto">
          <JoinForm initialPartyCode={initialPartyCodeForMode} initialMode={initialMode} />
        </section>
      </main>
      <footer className="mx-auto flex max-w-[1440px] items-center justify-between gap-4 border-t border-border/60 px-4 py-5 text-xs text-muted-foreground sm:px-6 lg:px-10">
        <span>JukeBox Live <span className="text-border">/</span> Your music, together.</span>
        <span className="hidden sm:inline">Built for the room you&apos;re in.</span>
      </footer>
    </div>
  )
}
