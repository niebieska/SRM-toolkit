# SRM Frontend

Vue 3 + Vite + Tailwind CSS frontend for SRM registration forms.

## Prerequisites

- Node.js 18+
- Backend API running at `http://localhost:8080`

## Setup

```bash
npm install
```

## Development

```bash
npm run dev
```

Opens at http://localhost:5173

## Build

```bash
npm run build
```

## Routes

- `/` — Home page (choose form type)
- `/kadra` — Kadra registration form (4 steps)
- `/uczestnik` — Uczestnik registration form (4 steps)

## API

The app connects to `GET http://localhost:8080/api/turnusy` to fetch the list of available turnuses.
