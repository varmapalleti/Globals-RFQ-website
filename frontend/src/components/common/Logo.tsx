import * as React from 'react'
import gssLogo from '@/assets/gss-logo.png'
import gssGlobe from '@/assets/gss-globe.png'

interface LogoProps {
  className?: string
  /** 'full' = globe + "Global Services Solution" text, 'icon' = globe only */
  variant?: 'full' | 'icon'
}

/** GSS brand logo (image based). Pass height via className, e.g. "h-16". */
export function Logo({ className, variant = 'full' }: LogoProps) {
  return (
    <div className={className} aria-label="Global Services Solution">
      <img
        src={variant === 'icon' ? gssGlobe : gssLogo}
        alt="Global Services Solution"
        className="h-full w-auto object-contain"
        draggable={false}
      />
    </div>
  )
}
