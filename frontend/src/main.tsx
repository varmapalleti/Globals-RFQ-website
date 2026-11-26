import * as React from 'react'
import { createRoot } from 'react-dom/client'
import '@/styles/globals.css'
import { Providers } from '@/app/providers'
import { AppRouter } from '@/app/router'

const rootEl = document.getElementById('root')
if (!rootEl) {
  throw new Error('Root element not found. Did you forget to add <div id="root"> to index.html?')
}

createRoot(rootEl).render(
  <React.StrictMode>
    <Providers>
      <AppRouter />
    </Providers>
  </React.StrictMode>,
)
