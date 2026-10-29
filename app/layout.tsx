import { Analytics } from "@vercel/analytics/next"
import type { Metadata, Viewport } from "next"
import { AppProviders } from "@/app/providers"
import "./globals.css"

export const metadata: Metadata = {
  title: {
    default: "JukeBox Live — Your room, your soundtrack",
    template: "%s · JukeBox Live",
  },
  description: "A social jukebox for the room you are in. Join the live queue, find your track, and shape the soundtrack together.",
  applicationName: "JukeBox Live",
  generator: "v0.app",
  icons: {
    icon: [
      { url: "/favicon.ico" },
      { url: "/icon1.png", sizes: "32x32", type: "image/png" },
      { url: "/icon0.svg", type: "image/svg+xml" },
    ],
    apple: "/apple-icon.png",
  },
  manifest: "/manifest.json",
  other: {
    "apple-mobile-web-app-title": "Juke-Box-live",
  },
}

export const viewport: Viewport = {
  colorScheme: "dark",
  themeColor: "#0f0f0f",
  userScalable: true,
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="antialiased">
        <AppProviders>{children}</AppProviders>
        {process.env.NODE_ENV === "production" && <Analytics />}
      </body>
    </html>
  )
}
