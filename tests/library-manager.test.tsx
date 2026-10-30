import { render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { LibraryManager } from "@/components/jukebox/library-manager"

const doubles = vi.hoisted(() => ({
  session: {
    token: "host-token",
    partyId: "party-1",
    partyCode: "ROOM",
    username: "Host",
    userId: "host-1",
    role: "ADMIN" as "ADMIN" | "USER",
    credits: 0,
  },
  api: {
    getLibraryStats: vi.fn(),
    searchTracks: vi.fn(),
    scanLibrary: vi.fn(),
  },
}))

vi.mock("@/lib/jukebox/context", () => ({
  useJukebox: () => ({
    hydrated: true,
    session: doubles.session,
    isAdmin: doubles.session.role === "ADMIN",
  }),
  JukeboxProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock("@/lib/jukebox/api", () => ({
  ApiError: class ApiError extends Error {
    status: number
    code?: string

    constructor(message: string, status: number, code?: string) {
      super(message)
      this.status = status
      this.code = code
    }
  },
  jukeboxApi: doubles.api,
}))

vi.mock("swr", () => ({
  default: () => ({ data: undefined, error: undefined, isLoading: false, mutate: vi.fn() }),
  useSWRConfig: () => ({ mutate: vi.fn() }),
  mutate: vi.fn(),
}))

vi.mock("@/components/jukebox/cover-art", () => ({
  CoverArt: ({ title, src }: { title: string; src?: string | null }) => (
    <div role="img" aria-label={`${title} album artwork`} data-src={src || ""} />
  ),
}))

vi.mock("@/components/jukebox/site-header", () => ({
  SiteHeader: () => <div data-testid="site-header" />,
}))

vi.mock("@/components/jukebox/artwork-review-panel", () => ({
  ArtworkReviewPanel: () => <div data-testid="artwork-review-panel" />,
}))

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
  Toaster: () => <div data-testid="toaster" />,
}))

beforeEach(() => {
  vi.clearAllMocks()
  doubles.session.role = "ADMIN"

  // Mock window.matchMedia for next-themes
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe("library manager browsing UI", () => {
  it("renders the library manager with basic elements", () => {
    render(<LibraryManager />)

    expect(screen.getByText("Music library")).toBeTruthy()
    expect(screen.getByText("Browse the catalog")).toBeTruthy()
  })

  it("shows admin required message when user is not admin", () => {
    doubles.session.role = "USER"
    render(<LibraryManager />)

    expect(screen.getByText(/Admin session required/i)).toBeTruthy()
  })
})
