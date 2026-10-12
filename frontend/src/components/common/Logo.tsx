import * as React from 'react'

/** Logo SVG — inline for zero network request and crisp rendering at any size. */
export function Logo({ className }: { className?: string }) {
  return (
    <div className={className} aria-label="Global RFQ">
      <svg
        viewBox="0 0 160 36"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        role="img"
        aria-label="Global RFQ"
      >
        <title>Global RFQ</title>
        {/* Globe icon */}
        <circle cx="18" cy="18" r="16" stroke="#2563eb" strokeWidth="2.5" />
        <ellipse cx="18" cy="18" rx="7" ry="16" stroke="#2563eb" strokeWidth="2" />
        <line x1="2" y1="18" x2="34" y2="18" stroke="#2563eb" strokeWidth="2" />
        <line x1="4" y1="10" x2="32" y2="10" stroke="#2563eb" strokeWidth="1.5" />
        <line x1="4" y1="26" x2="32" y2="26" stroke="#2563eb" strokeWidth="1.5" />
        {/* Text */}
        <text
          x="44"
          y="24"
          fontFamily="Inter,sans-serif"
          fontWeight="700"
          fontSize="17"
          fill="#0f172a"
        >
          Global
        </text>
        <text
          x="98"
          y="24"
          fontFamily="Inter,sans-serif"
          fontWeight="700"
          fontSize="17"
          fill="#2563eb"
        >
          RFQ
        </text>
      </svg>
    </div>
  )
}
