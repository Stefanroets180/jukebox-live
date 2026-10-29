"use client"

import { useState, useEffect, type FormEvent } from "react"
import { useRouter } from "next/navigation"
import { AnimatePresence, motion } from "framer-motion"
import { Camera, Check, CircleAlert, LoaderCircle, MapPin, Radio, ScanLine, ShieldCheck, Wifi } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useJukebox } from "@/lib/jukebox/context"
import { jukeboxApi, ApiError } from "@/lib/jukebox/api"

type JoinFormProps = { initialPartyCode?: string; initialMode?: "login" | "register" | "admin" }

type JoinError = { title: string; message: string }

function explainLoginError(error: unknown): JoinError {
  const apiError = error instanceof ApiError ? error : null
  const details = `${apiError?.code ?? ""} ${apiError?.message ?? ""}`.toLowerCase()
  if (details.includes("wifi") || details.includes("wi-fi") || details.includes("network") || details.includes("ip")) {
    return { title: "Not on party Wi-Fi", message: "Connect to the same Wi-Fi as your host, then try again." }
  }
  if (details.includes("ended") || details.includes("inactive") || details.includes("party_not_active")) {
    return { title: "Party ended", message: "This room is no longer active. Ask your host for a current party code." }
  }
  if (details.includes("credentials") || details.includes("password") || apiError?.status === 401) {
    return { title: "Check your sign-in details", message: "That username or password did not match. Try again." }
  }
  if (apiError?.status === 0 || details.includes("host_unreachable")) {
    return { title: "Host not reachable", message: "Check that you are on the host's Wi-Fi and that JukeBox Live is running." }
  }
  if (apiError?.status === 404 || details.includes("party")) {
    return { title: "Party code not found", message: "Double-check the code on the host screen and try again." }
  }
  return { title: "We couldn't connect", message: apiError?.message ?? "Please check the details and try again." }
}

export function JoinForm({ initialPartyCode = "", initialMode = "login" }: JoinFormProps) {
  const router = useRouter()
  const { authenticate } = useJukebox()
  const [mode, setMode] = useState<"login" | "register" | "admin">(initialMode)
  const [partyCode, setPartyCode] = useState(initialPartyCode.toUpperCase())
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [scannerOpen, setScannerOpen] = useState(false)
  const [error, setError] = useState<JoinError | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [checkingSetup, setCheckingSetup] = useState(mode === "admin")

  useEffect(() => {
    async function checkSetupForAdmin() {
      if (mode !== "admin") return
      try {
        const status = await jukeboxApi.getSetupStatus()
        if (status.available) {
          router.replace("/admin/setup")
        }
      } catch {
        // If check fails, proceed with normal admin login
      } finally {
        setCheckingSetup(false)
      }
    }
    checkSetupForAdmin()
  }, [mode, router])

  // Clear party code when in admin mode
  useEffect(() => {
    if (mode === "admin") {
      setPartyCode("")
    }
  }, [mode])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return

    if (mode === "login") {
      const normalizedCode = partyCode.trim().toUpperCase()
      if (!/^[A-Z0-9]{6}$/.test(normalizedCode)) {
        setError({ title: "Check your party code", message: "Enter the 6-character code shown on the host screen." })
        return
      }
    }

    setError(null)
    setNotice(null)
    setSubmitting(true)
    try {
      if (mode === "register") {
        await jukeboxApi.register({ username: username.trim(), password })
        setMode("login")
        setPassword("")
        setNotice("Account created. Enter your party code and sign in to join the room.")
        return
      }

      if (mode === "admin") {
        const result = await jukeboxApi.adminLogin({ username: username.trim(), password })
        authenticate({
          token: result.token,
          partyId: result.partyId ?? "",
          partyCode: "",
          username: username.trim(),
          credits: result.credits,
        })
        router.push("/admin/library")
        return
      }

      const normalizedCode = partyCode.trim().toUpperCase()
      const result = await jukeboxApi.login({
        username: username.trim(),
        password,
        partyCode: normalizedCode,
      })
      authenticate({
        token: result.token,
        partyId: result.partyId,
        partyCode: normalizedCode,
        username: username.trim(),
        credits: result.credits,
      })
      router.push("/dashboard")
    } catch (submitError) {
      if (mode === "register") {
        const apiError = submitError instanceof ApiError ? submitError : null
        const conflict = apiError?.status === 409 || apiError?.code?.toLowerCase().includes("username")
        setError({
          title: conflict ? "Username already in use" : "Account could not be created",
          message: apiError?.message ?? "Check the username and try again.",
        })
      } else {
        setError(explainLoginError(submitError))
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (checkingSetup) {
    return (
      <div className="flex items-center justify-center py-12">
        <LoaderCircle className="animate-spin text-primary" size={32} />
      </div>
    )
  }

  return (
    <>
      <Card className="relative overflow-hidden border-border/80 bg-card/90 shadow-2xl shadow-black/25 backdrop-blur-sm">
        <div aria-hidden="true" className="pointer-events-none absolute -right-16 -top-20 size-48 rounded-full bg-primary/12 blur-3xl" />
        <CardHeader className="relative gap-2 pb-5">
          <div className="mb-1 flex size-11 items-center justify-center rounded-2xl border border-primary/20 bg-primary/10 text-primary">
            <Radio aria-hidden="true" className="size-5" />
          </div>
          <CardTitle className="text-2xl tracking-tight">{mode === "admin" ? "Host sign in" : "Step into the room"}</CardTitle>
          <CardDescription className="max-w-sm text-sm leading-6">
            {mode === "login"
              ? "Enter the code from your host. We’ll verify you’re nearby before you join the queue."
              : mode === "register"
                ? "Create a guest account once, then sign in with a party code whenever you’re invited."
                : "Sign in with an ADMIN account to manage the host library and start parties."}
          </CardDescription>
        </CardHeader>
        <CardContent className="relative">
          <ToggleGroup
            aria-label="Account action"
            multiple={false}
            value={[mode]}
            onValueChange={(values) => {
              const nextMode = values[0]
              if (nextMode === "login" || nextMode === "register" || nextMode === "admin") {
                setMode(nextMode)
                setError(null)
                setNotice(null)
              }
            }}
            variant="outline"
            size="sm"
            spacing={0}
            className="mb-5 grid w-full grid-cols-3 rounded-xl border border-border/70 bg-background/70 p-1"
          >
            <ToggleGroupItem value="login" className="h-9 rounded-lg">Sign in</ToggleGroupItem>
            <ToggleGroupItem value="register" className="h-9 rounded-lg">Create account</ToggleGroupItem>
            <ToggleGroupItem value="admin" className="h-9 rounded-lg">Admin</ToggleGroupItem>
          </ToggleGroup>
          <form onSubmit={handleSubmit} className="flex flex-col gap-5">
            <FieldGroup>
              {mode === "login" && (
                <Field>
                  <div className="flex items-center justify-between gap-3">
                    <FieldLabel htmlFor="party-code">Party code</FieldLabel>
                    <button
                      type="button"
                      onClick={() => setScannerOpen(true)}
                      className="inline-flex items-center gap-1.5 text-xs font-medium text-primary transition-colors hover:text-primary/80"
                    >
                      <ScanLine aria-hidden="true" className="size-3.5" />
                      Scan QR instead
                    </button>
                  </div>
                  <Input
                    id="party-code"
                    value={partyCode}
                    onChange={(event) => setPartyCode(event.target.value.toUpperCase().replace(/\s/g, ""))}
                    placeholder="ABC123"
                    autoComplete="off"
                    maxLength={16}
                    aria-describedby="party-code-help"
                    className="h-12 font-mono text-base tracking-[0.18em] uppercase"
                    required
                  />
                  <FieldDescription id="party-code-help">The 6-character code on the big screen.</FieldDescription>
                </Field>
              )}

              <div className="grid gap-4 sm:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor="username">Username</FieldLabel>
                  <Input
                    id="username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="your.name"
                    autoComplete="username"
                    maxLength={50}
                    required
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                  <Input
                    id="password"
                    type="password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder={mode === "register" ? "At least 8 characters" : "Your password"}
                    autoComplete={mode === "register" ? "new-password" : "current-password"}
                    minLength={mode === "register" ? 8 : undefined}
                    required
                  />
                </Field>
              </div>
            </FieldGroup>

            <AnimatePresence initial={false}>
              {error && (
                <motion.div
                  key={error.title}
                  initial={{ opacity: 0, height: 0, y: -5 }}
                  animate={{ opacity: 1, height: "auto", y: 0 }}
                  exit={{ opacity: 0, height: 0, y: -5 }}
                  transition={{ duration: 0.2 }}
                >
                  <Alert variant="destructive" className="border-destructive/30 bg-destructive/5">
                    <CircleAlert aria-hidden="true" />
                    <AlertTitle>{error.title}</AlertTitle>
                    <AlertDescription>{error.message}</AlertDescription>
                  </Alert>
                </motion.div>
              )}
            </AnimatePresence>

            <AnimatePresence initial={false}>
              {notice && (
                <motion.div
                  key="registration-notice"
                  initial={{ opacity: 0, height: 0, y: -5 }}
                  animate={{ opacity: 1, height: "auto", y: 0 }}
                  exit={{ opacity: 0, height: 0, y: -5 }}
                  transition={{ duration: 0.2 }}
                >
                  <Alert className="border-primary/20 bg-primary/[0.06]">
                    <Check aria-hidden="true" />
                    <AlertTitle>Account created</AlertTitle>
                    <AlertDescription>{notice}</AlertDescription>
                  </Alert>
                </motion.div>
              )}
            </AnimatePresence>

            <Button type="submit" size="lg" disabled={submitting} className="h-12 w-full rounded-xl text-sm font-semibold shadow-lg shadow-primary/15">
              {submitting ? (
                <><LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" />{mode === "register" ? "Creating account…" : mode === "admin" ? "Signing in as host…" : "Joining party…"}</>
              ) : mode === "register" ? (
                <><Check aria-hidden="true" data-icon="inline-start" />Create account</>
              ) : mode === "admin" ? (
                <><ShieldCheck aria-hidden="true" data-icon="inline-start" />Sign in as host</>
              ) : (
                <><Wifi aria-hidden="true" data-icon="inline-start" />Join the queue</>
              )}
            </Button>

            <p className="text-center text-xs leading-5 text-muted-foreground">
              {mode === "login"
                ? "By joining, you confirm you're on the same Wi-Fi network as the host."
                : mode === "register"
                  ? "Create an account first. You'll need a party code to join a room."
                  : "Host access requires an account with the ADMIN role."}
            </p>
          </form>
        </CardContent>
      </Card>

      <Dialog open={scannerOpen} onOpenChange={setScannerOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Scan the host QR code</DialogTitle>
            <DialogDescription>This preview shows the scanner layout. Enter the code printed below the host QR to join.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col items-center gap-5 py-2">
            <div className="relative grid aspect-square w-full max-w-[260px] place-items-center overflow-hidden rounded-2xl border border-primary/20 bg-secondary/60">
              <div className="absolute inset-6 rounded-xl border border-dashed border-primary/30" />
              <div aria-hidden="true" className="absolute inset-x-8 top-8 bottom-8">
                <span className="absolute left-0 top-0 size-7 rounded-tl-md border-l-2 border-t-2 border-primary" />
                <span className="absolute right-0 top-0 size-7 rounded-tr-md border-r-2 border-t-2 border-primary" />
                <span className="absolute bottom-0 left-0 size-7 rounded-bl-md border-b-2 border-l-2 border-primary" />
                <span className="absolute bottom-0 right-0 size-7 rounded-br-md border-b-2 border-r-2 border-primary" />
                <span className="absolute inset-x-1 top-1/2 h-px animate-pulse bg-primary shadow-[0_0_16px_2px_var(--primary)]" />
              </div>
              <div className="relative flex flex-col items-center gap-3 text-center">
                <span className="flex size-14 items-center justify-center rounded-2xl bg-background text-primary shadow-lg">
                  <Camera aria-hidden="true" className="size-6" />
                </span>
                <span className="text-sm font-medium">Scanner preview</span>
                <span className="text-xs text-muted-foreground">Camera access is not requested here</span>
              </div>
            </div>
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <ShieldCheck aria-hidden="true" className="size-4 text-primary" />
              Location verification still happens when you join.
            </div>
            <Button type="button" variant="outline" className="w-full" onClick={() => setScannerOpen(false)}>
              <Check aria-hidden="true" data-icon="inline-start" />Got it
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
