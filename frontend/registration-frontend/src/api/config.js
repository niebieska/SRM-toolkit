// Vite replaces this at build time; '/' uses the container's same-origin API proxy.
export const API_BASE = (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
