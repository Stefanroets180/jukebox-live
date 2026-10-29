"use client"

import { useState, useEffect, type FormEvent } from "react"
import { useRouter } from "next/navigation"
import { AnimatePresence, motion } from "framer-motion"
import { Check, CircleAlert, LoaderCircle, ShieldCheck } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useJukebox } from "@/lib/jukebox/context"
import { jukeboxApi, ApiError } from "@/lib/jukebox/api"

type SetupError = { title: string; message: string }

export default function FirstRunSetupPage() {
  const router = useRouter()
  const { authenticate } = useJukebox()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<SetupError | null>(null)
  const [isChecking, setIsChecking] = useState(true)
  const [setupAvailable, setSetupAvailable] = useState(false)

  useEffect(() => {
    async function checkSetupStatus() {
      try {
        const status = await jukeboxApi.getSetupStatus()
        if (!status.available) {
          router.replace("/join?mode=admin")
          return
        }
        setSetupAvailable(true)
      } catch {
        router.replace("/join?mode=admin")
      } finally {
        setIsChecking(false)
      }
    }
    checkSetupStatus()
  }, [router])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting || !setupAvailable) return

    setError(null)
    setSubmitting(true)
    try {
      const result = await jukeboxApi.createFirstAdmin({ username: username.trim(), password })
      authenticate({
        token: result.token,
        partyId: result.partyId ?? "",
        partyCode: "",
        username: username.trim(),
        credits: result.credits,
      })
      router.push("/admin/library")
    } catch (submitError) {
      const apiError = submitError instanceof ApiError ? submitError : null
      setError({
        title: "Setup failed",
        message: apiError?.message ?? "Could not create administrator. Please try again.",
      })
    } finally {
      setSubmitting(false)
    }
  }

  if (isChecking) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <LoaderCircle className="animate-spin text-primary" size={32} />
      </div>
    )
  }

  if (!setupAvailable) {
    return null // Redirecting
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="relative overflow-hidden border-border/80 bg-card/90 shadow-2xl shadow-black/25 backdrop-blur-sm w-full max-w-md">
        <div aria-hidden="true" className="pointer-events-none absolute -right-16 -top-20 size-48 rounded-full bg-primary/12 blur-3xl" />
        <CardHeader className="relative gap-2 pb-5">
          <div className="mb-1 flex size-11 items-center justify-center rounded-2xl border border-primary/20 bg-primary/10 text-primary">
            <ShieldCheck aria-hidden="true" className="size-5" />
          </div>
          <CardTitle className="text-2xl tracking-tight">Create Administrator</CardTitle>
          <CardDescription className="text-sm leading-6">
            This is your first time setting up JukeBox Live. Create your administrator account to manage the host library and start parties.
          </CardDescription>
        </CardHeader>
        <CardContent className="relative">
          <form onSubmit={handleSubmit} className="flex flex-col gap-5">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="username">Username</FieldLabel>
                <Input
                  id="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  placeholder="admin"
                  autoComplete="username"
                  maxLength={50}
                  required
                  disabled={submitting}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="password">Password</FieldLabel>
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="At least 8 characters"
                  autoComplete="new-password"
                  minLength={8}
                  required
                  disabled={submitting}
                />
              </Field>
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

            <Button type="submit" size="lg" disabled={submitting} className="h-12 w-full rounded-xl text-sm font-semibold shadow-lg shadow-primary/15">
              {submitting ? (
                <><LoaderCircle aria-hidden="true" data-icon="inline-start" className="animate-spin" />Creating administrator…</>
              ) : (
                <><Check aria-hidden="true" data-icon="inline-start" />Create administrator</>
              )}
            </Button>

            <p className="text-center text-xs leading-5 text-muted-foreground">
              This account will have full administrative access. Keep your password secure.
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
