"use client"

import { useState, type FormEvent } from "react"
import { Activity, AlertCircle, ArrowDownToLine, Check, Copy, CreditCard, Ellipsis, MapPin, Shield, SlidersHorizontal, Users, Wifi } from "lucide-react"
import { toast } from "sonner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Slider } from "@/components/ui/slider"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { SiteHeader } from "@/components/jukebox/site-header"
import { useJukebox } from "@/lib/jukebox/context"
import { usePartyData } from "@/lib/jukebox/use-party-data"
import { jukeboxApi } from "@/lib/jukebox/api"

function joinedTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return "—"
  return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })
}

export function PartySettings() {
  const { session, isAdmin } = useJukebox()
  const { guests, isGuestsPreview, mutateGuests } = usePartyData()
  const [radius, setRadius] = useState(100)
  const [creditsPerSong, setCreditsPerSong] = useState(1)
  const [partyActive, setPartyActive] = useState(true)
  const [grantOpen, setGrantOpen] = useState(false)
  const [grantUsername, setGrantUsername] = useState("")
  const [grantAmount, setGrantAmount] = useState("3")
  const [granting, setGranting] = useState(false)
  const [partyCodeCopied, setPartyCodeCopied] = useState(false)
  const guestsPreview = isGuestsPreview

  async function copyPartyCode() {
    try {
      await navigator.clipboard.writeText(session?.partyCode ?? "------")
      setPartyCodeCopied(true)
      window.setTimeout(() => setPartyCodeCopied(false), 1500)
      toast.success("Party code copied")
    } catch {
      toast.error("Could not copy the party code")
    }
  }

  async function handleGrant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const amount = Number(grantAmount)
    if (!session || !isAdmin || !grantUsername.trim() || !Number.isInteger(amount) || amount < 1 || amount > 100) {
      toast.error("Check the username and credit amount", { description: "Grant between 1 and 100 credits at a time." })
      return
    }

    setGranting(true)
    try {
      await jukeboxApi.grantCredits(session, grantUsername.trim(), amount)
      await mutateGuests()
      setGrantOpen(false)
      setGrantUsername("")
      setGrantAmount("3")
      toast.success("Credits granted", { description: `${amount} credits sent to ${grantUsername.trim()}.` })
    } catch (error) {
      toast.error("Could not grant credits", { description: error instanceof Error ? error.message : "Try again." })
    } finally {
      setGranting(false)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto max-w-[1440px] px-4 py-8 sm:px-6 sm:py-10 xl:px-10">
        <section className="mb-8 flex flex-col gap-4 sm:mb-10 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2"><Badge variant="secondary" className="rounded-full px-3 py-1 text-[11px]">HOST TOOLS</Badge><Badge variant="outline" className="text-[10px]">{guestsPreview ? "No active party" : "Live party"}</Badge></div>
            <h1 className="text-3xl font-semibold tracking-[-0.04em] sm:text-4xl">Party settings</h1>
            <p className="max-w-2xl text-sm leading-6 text-muted-foreground sm:text-base">Manage the room rules, keep guests in the loop, and top up credits when the queue needs a boost.</p>
          </div>
          <Button type="button" variant="outline" onClick={copyPartyCode}><Copy aria-hidden="true" data-icon="inline-start" />{partyCodeCopied ? "Copied" : `Copy ${session?.partyCode ?? "------"}`}</Button>
        </section>

        {!isAdmin && (
          <Alert className="mb-6 border-primary/20 bg-primary/[0.06]">
            <AlertCircle aria-hidden="true" />
            <AlertTitle>{session ? "Admin session required" : "Preview settings"}</AlertTitle>
            <AlertDescription>{session ? "You can view this screen, but only an ADMIN session can grant credits. Rule changes are drafts because the current Spring API contract has no settings-mutation route." : "Sign in with a host account to see a real guest list and grant credits."}</AlertDescription>
          </Alert>
        )}
        {isAdmin && (
          <Alert className="mb-6 border-border bg-card/60">
            <Shield aria-hidden="true" />
            <AlertTitle>Some controls are preview-only</AlertTitle>
            <AlertDescription>Credit grants and guest lists use the live API. Geofence, song cost, active state, guest removal, and transaction export need endpoints not present in the supplied API contract.</AlertDescription>
          </Alert>
        )}

        <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.2fr)_minmax(360px,0.8fr)]">
          <div className="flex flex-col gap-5">
            <Card className="border-border/80 bg-card/75">
              <CardHeader className="flex flex-row items-start justify-between gap-3 border-b border-border/60">
                <div className="flex flex-col gap-1"><CardTitle className="text-xl">Room rules</CardTitle><CardDescription>Adjustable preview · changes are not sent to the server yet</CardDescription></div>
                <Badge variant="outline" className="shrink-0 gap-1.5 text-[10px]"><SlidersHorizontal aria-hidden="true" className="size-3" />Draft only</Badge>
              </CardHeader>
              <CardContent className="flex flex-col gap-7 pt-6">
                <Field>
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex flex-col gap-1"><FieldLabel htmlFor="geofence-radius">Geofence radius</FieldLabel><FieldDescription>Guests must be within this distance of the host.</FieldDescription></div>
                    <span className="w-fit rounded-lg border border-border bg-background px-3 py-1.5 font-mono text-sm tabular-nums">{radius} m</span>
                  </div>
                  <Slider id="geofence-radius" aria-label="Geofence radius in meters" min={50} max={500} step={10} value={[radius]} onValueChange={(value) => setRadius(Array.isArray(value) ? value[0] : value)} className="mt-5" />
                  <div className="mt-2 flex justify-between text-[10px] text-muted-foreground"><span>50 m</span><span>500 m</span></div>
                </Field>
                <Field>
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex flex-col gap-1"><FieldLabel htmlFor="credits-per-song">Credits per song</FieldLabel><FieldDescription>Credits spent when a guest adds a track.</FieldDescription></div>
                    <span className="w-fit rounded-lg border border-border bg-background px-3 py-1.5 font-mono text-sm tabular-nums">{creditsPerSong} {creditsPerSong === 1 ? "credit" : "credits"}</span>
                  </div>
                  <Slider id="credits-per-song" aria-label="Credits required per song" min={1} max={5} step={1} value={[creditsPerSong]} onValueChange={(value) => setCreditsPerSong(Array.isArray(value) ? value[0] : value)} className="mt-5" />
                  <div className="mt-2 flex justify-between text-[10px] text-muted-foreground"><span>1 credit</span><span>5 credits</span></div>
                </Field>
                <Field>
                  <div className="flex items-center justify-between gap-4 rounded-xl border border-border/70 bg-background/45 p-4">
                    <div className="flex min-w-0 flex-col gap-1"><FieldLabel htmlFor="party-active">Party is active</FieldLabel><FieldDescription>When inactive, new guests cannot join.</FieldDescription></div>
                    <Switch id="party-active" checked={partyActive} onCheckedChange={setPartyActive} aria-label="Party is active" />
                  </div>
                </Field>
                <div className="flex items-start gap-3 rounded-xl border border-border/70 bg-background/50 p-4">
                  <Activity aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-primary" />
                  <p className="text-xs leading-5 text-muted-foreground">The configured backend default is 100 m and one credit per track. These controls demonstrate the intended settings surface; saving requires a party-settings endpoint.</p>
                </div>
              </CardContent>
            </Card>

            <Card className="overflow-hidden border-border/80 bg-card/75">
              <CardHeader className="flex flex-col gap-4 border-b border-border/60 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-center gap-3">
                  <span className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary"><Users aria-hidden="true" className="size-4" /></span>
                  <div className="flex flex-col gap-1"><CardTitle className="text-lg">Guests in the room</CardTitle><CardDescription>{guestsPreview ? "No active party" : `${guests.length} active guests`}</CardDescription></div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button type="button" variant="outline" size="sm" disabled title="The API contract does not include a transaction-history route"><ArrowDownToLine aria-hidden="true" data-icon="inline-start" />Export history</Button>
                  <Button type="button" size="sm" onClick={() => setGrantOpen(true)} disabled={!session || !isAdmin} title={!isAdmin ? "An admin account is required" : undefined}><CreditCard aria-hidden="true" data-icon="inline-start" />Grant credits</Button>
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead className="pl-5">Guest</TableHead>
                      <TableHead>Credits</TableHead>
                      <TableHead className="hidden sm:table-cell">Distance</TableHead>
                      <TableHead className="hidden md:table-cell">Joined</TableHead>
                      <TableHead className="w-10 pr-4"><span className="sr-only">Guest actions</span></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {guests.map((guest) => (
                      <TableRow key={guest.username}>
                        <TableCell className="pl-5">
                          <div className="flex items-center gap-2.5">
                            <Avatar className="size-8"><AvatarFallback className="bg-secondary text-[10px] font-semibold">{guest.username.slice(0, 2).toUpperCase()}</AvatarFallback></Avatar>
                            <div className="flex min-w-0 flex-col gap-0.5"><span className="max-w-[115px] truncate text-xs font-medium sm:max-w-none">{guest.username}</span><span className="text-[10px] text-muted-foreground sm:hidden">{Math.round(guest.distanceFromParty)} m away</span></div>
                          </div>
                        </TableCell>
                        <TableCell><Badge variant="secondary" className="gap-1 text-[10px]"><CreditCard aria-hidden="true" className="size-3" />{guest.credits}</Badge></TableCell>
                        <TableCell className="hidden text-xs text-muted-foreground sm:table-cell">{Math.round(guest.distanceFromParty)} m</TableCell>
                        <TableCell className="hidden text-xs text-muted-foreground md:table-cell">{joinedTime(guest.joinedAt)}</TableCell>
                        <TableCell className="pr-4">
                          <Button type="button" variant="ghost" size="icon-sm" disabled title="Kick and ban endpoints are not included in the current API contract" aria-label={`More actions for ${guest.username}`}><Ellipsis aria-hidden="true" /></Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </div>

          <aside className="flex flex-col gap-5">
            <Card className="overflow-hidden border-border/80 bg-card/75">
              <CardHeader>
                <div className="flex items-center gap-3"><span className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary"><Wifi aria-hidden="true" className="size-4" /></span><div className="flex flex-col gap-1"><CardTitle className="text-lg">Party access</CardTitle><CardDescription>Share this code with the room.</CardDescription></div></div>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <div className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-background/50 p-4">
                  <div className="flex flex-col gap-1"><span className="text-[10px] font-semibold tracking-[0.15em] text-muted-foreground uppercase">Party code</span><span className="font-mono text-2xl font-semibold tracking-[0.18em]">{session?.partyCode ?? "------"}</span></div>
                  <Button type="button" size="icon" variant="outline" onClick={copyPartyCode} aria-label="Copy party code">{partyCodeCopied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}</Button>
                </div>
                <div className="flex items-center gap-3 text-xs text-muted-foreground"><MapPin aria-hidden="true" className="size-4 shrink-0 text-primary" /><span>GPS geofence · {radius} m {session ? "· active session" : "· no active party"}</span></div>
                <div className="flex items-center gap-3 text-xs text-muted-foreground"><Shield aria-hidden="true" className="size-4 shrink-0 text-primary" /><span>Same Wi-Fi and nearby location required.</span></div>
                <Badge variant="outline" className="w-fit gap-1.5 text-[10px]"><span className={`size-1.5 rounded-full ${partyActive ? "bg-emerald-400" : "bg-muted-foreground"}`} />{partyActive ? "Accepting guests" : "Paused"}</Badge>
              </CardContent>
            </Card>

            <Card className="border-border/80 bg-card/75">
              <CardHeader><div className="flex items-center gap-3"><span className="flex size-10 items-center justify-center rounded-xl bg-secondary text-muted-foreground"><CreditCard aria-hidden="true" className="size-4" /></span><div className="flex flex-col gap-1"><CardTitle className="text-lg">Credit economy</CardTitle><CardDescription>Keep requests moving fairly.</CardDescription></div></div></CardHeader>
              <CardContent className="flex flex-col gap-3">
                <div className="flex items-center justify-between rounded-xl border border-border/70 bg-background/50 px-4 py-3"><span className="text-xs text-muted-foreground">Price per request</span><span className="text-sm font-semibold">{creditsPerSong} {creditsPerSong === 1 ? "credit" : "credits"}</span></div>
                <div className="flex items-center justify-between rounded-xl border border-border/70 bg-background/50 px-4 py-3"><span className="text-xs text-muted-foreground">Active guests</span><span className="text-sm font-semibold tabular-nums">{guests.length}</span></div>
                <p className="text-[11px] leading-5 text-muted-foreground">Guest credits live in the Spring backend and are updated over the room&apos;s WebSocket connection.</p>
              </CardContent>
            </Card>
          </aside>
        </div>
      </main>

      <Dialog open={grantOpen} onOpenChange={setGrantOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Grant party credits</DialogTitle><DialogDescription>Add credits to a guest&apos;s account. The server validates the balance change.</DialogDescription></DialogHeader>
          <form onSubmit={handleGrant} className="flex flex-col gap-5">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="grant-username">Guest username</FieldLabel>
                <Input id="grant-username" value={grantUsername} onChange={(event) => setGrantUsername(event.target.value)} placeholder="guest-username" autoComplete="off" required />
              </Field>
              <Field>
                <FieldLabel htmlFor="grant-amount">Credits to add</FieldLabel>
                <Input id="grant-amount" type="number" min="1" max="100" step="1" value={grantAmount} onChange={(event) => setGrantAmount(event.target.value)} required />
                <FieldDescription>Choose a whole number from 1 to 100.</FieldDescription>
              </Field>
            </FieldGroup>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setGrantOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={granting || !session || !isAdmin}>{granting ? "Granting…" : "Grant credits"}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
