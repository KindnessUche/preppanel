# preppanel-frontend

> Fastest way to run the whole project (this + backend + Postgres) is one
> command from the repo root - see the top-level `README.md`. Everything
> below is for running/developing this frontend on its own.

Next.js 14 (App Router) + TypeScript + Tailwind + GSAP. Covers the marketing
landing page, real auth, and the product itself: a 5-tab app built from a
UI/UX design doc that maps its IA to a real interview's actual sequence —
Apply → Invite → Lobby → The Room → Debrief → Track record.

## Visual identity

One consistent dark theme across the whole product — the landing page's
palette (near-black canvas, off-white ink, muted blue accent `#5b8def`,
`IBM Plex Mono` for data/labels) is reused for the app itself rather than
introducing a second palette, by design decision.

The design doc's structural and interaction ideas (the breathing orb, the
"never instant" AI response floor, progressive-reveal instead of spinners,
tone/coaching-not-scorecard rules) are all implemented — just re-skinned
dark instead of the doc's original warm palette.

## Stack
- Next.js 14 (App Router), TypeScript
- Tailwind CSS
- GSAP + ScrollTrigger for animation

## Running locally

```bash
npm install
npm run dev
```

Then open http://localhost:3000.

**Your backend needs to be running** for anything past the landing page —
Postgres up, `PrepPanelApplication` started on `:8080`. Copy
`.env.local.example` to `.env.local` and set `NEXT_PUBLIC_API_BASE_URL` if
your backend isn't on the default `localhost:8080`. Your backend's
`SecurityConfig` already allows CORS from `localhost:3000`, so no backend
config changes are needed to test this locally.

```bash
npm run build   # verify production build compiles
```

Both were run and verified during this build — clean production build with
full TypeScript checking across all 10 routes, plus a dev server serving
every route successfully.

## Pages

**Marketing** — `/` — the chapter-based landing page (see git history /
prior README revisions for the full breakdown if needed; unchanged in this
pass except persona names now match the canonical archetype library below).

**Auth** — `/login`, `/register` — real forms calling
`preppanel-backend`'s actual `AuthController`. Tokens persist in
`localStorage` via `lib/auth-context.tsx`, which also handles proactive +
reactive token refresh.

**The app** (protected, redirects to `/login` if signed out):

| Route | What it is |
|---|---|
| `/home` | "What should I do right now" — continue/start card, 14-day momentum strip |
| `/prep` | "You" (local profile form) + "Them" (company-dossier preview, not live) |
| `/practice` | **The core feature.** Casting (role + tone) → lobby → live session → debrief. Fully wired to your backend. |
| `/growth` | Session history + a full report per session (4-axis score radar, per-question feedback) |
| `/growth/[sessionId]` | Report detail |
| `/account` | Sign out, a working **Reduce motion** toggle, honest placeholders for what's not built |

## The casting system — what's actually live vs. the vision

The backend now accepts an optional `tone` (`friendly` \| `neutral` \|
`direct`) on `POST /api/interviews`, which shapes the LLM's phrasing —
this is genuinely live, not a UI mockup. `/practice`'s setup screen exposes
exactly this: role + tone preset, mode locked to Text (Voice/Panel shown
but visibly greyed "coming soon," per the design doc's "visible, not
hidden" paywall principle rather than hiding the feature entirely).

The full **archetype library** — The Hiring Manager, The Tech Lead, The
Skeptic, The Bar Raiser, The Peer (`lib/casting.ts`) — is the paid-tier
vision from the design doc. It's not selectable in the app yet (that needs
server-side versioned prompt templates + tone/pace/depth sliders, per the
doc's Section 8), but it is showcased on the landing page's persona grid so
the vision is visible without claiming it's live.

## The breathing orb

The design doc's one deliberate signature animation
(`components/app/Orb.tsx`) — an abstract circle with an idle breathing
pulse, a faster "thinking" pulse, and a "speaking" ripple. No face, no
avatar — this is what avoids uncanny-valley risk while still reading as
"someone is present." Used in the live session, the debrief transition, and
the landing page's persona grid. Respects `prefers-reduced-motion` and the
manual toggle in `/account`.

## Known limitation: no session-list endpoint yet

The backend only has `GET /api/interviews/{id}` — no `GET /api/interviews`
to list a user's own sessions. `/home` and `/growth` work around this with
`lib/session-store.ts`, which remembers session IDs in `localStorage` as
they're created **on this device**. This is a genuine stopgap, documented
as one — the real fix is a list endpoint on the backend, after which
`session-store.ts` can be deleted in favor of a real server call.

## What's honestly not built yet

Company research (Prep's "Them" half is a preview only), the full archetype
library, voice/panel mode, technical-question scoring, billing, and
data-deletion endpoints. Each has a clearly labeled placeholder in the UI —
nothing is silently missing or faked with dummy data.

## Known items
- Google Fonts requires outbound internet at build/dev time.
- One `npm audit` warning remains in a dev-only dependency bundled inside
  Next.js's own build tooling (not exploitable in the shipped app; fixing
  it means jumping to Next 15/16, deliberately not forced through untested
  here).
- No headless browser was available in the sandbox this was built in, so
  verification was a clean production build + type-check + a live dev
  server serving every route, not a visual screenshot. Take a first look
  yourself and flag anything that doesn't match what you pictured.
