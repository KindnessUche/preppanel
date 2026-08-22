# PrepPanel

An AI mock-interview panel: pick 1-4 named interviewer personas (each with
their own personality, tone, and mood), practice through a real conversation
that reacts to what you actually say, and get scored against a rubric -
not a canned quiz.

**This is a local, self-contained demo - no live hosted version.** Clone it
and run it yourself with the one command below.

## Run it

You need [Docker Desktop](https://www.docker.com/products/docker-desktop/)
and a free [Groq API key](https://console.groq.com) (no card required).

```bash
git clone <this-repo-url>
cd preppanel
cp .env.example .env
# open .env and paste your GROQ_API_KEY

docker compose up --build
```

First build takes a few minutes (downloading dependencies, compiling both
apps). Once it's up:

1. Open **http://localhost:3000**
2. Register an account
3. Go to **Practice**, cast a panel, and try a session

That's the whole setup. Postgres, the Spring Boot API, and the Next.js
frontend all start together from one command.

## What this actually is

- **Backend** (`/backend`) - Java 21 / Spring Boot. Self-built JWT auth,
  Postgres + Flyway migrations, a multi-panelist conversational interview
  engine (Groq-primary, Gemini-fallback LLM calls), and an LLM-as-judge
  scoring pipeline. See `backend/README.md` for the architecture, auth
  design, and what's deliberately not built yet.
- **Frontend** (`/frontend`) - Next.js 14 / TypeScript / Tailwind / GSAP. A
  landing page and a 5-tab app (Home, Prep, Practice, Growth, Account) built
  from a real UI/UX design doc. See `frontend/README.md` for the design
  system, the panel-casting flow, and known limitations.

## What's real vs. what's a preview

Every screen in this project is honest about its own status - nothing fakes
data. Live today: auth, multi-panelist conversational interviews, LLM
scoring, the full casting flow. Clearly labeled as previews/planned: company
research, voice mode, the full paid archetype tier, billing. You'll see
SHIPPED / PLANNED tags throughout the UI itself.

## Cost

$0 to run. Groq and Google AI Studio both offer genuinely free API tiers
with no card required - this project stays within them.

## Local dev (without Docker)

Each sub-project also works independently for active development - see
their individual READMEs for running the backend via `mvn spring-boot:run`
and the frontend via `npm run dev`, which is faster for iterating on one
side at a time than rebuilding the full Docker Compose stack.
