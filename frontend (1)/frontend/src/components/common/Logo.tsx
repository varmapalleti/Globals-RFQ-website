import { brand } from '@/config/brand'
import { cn } from '@/lib/utils'

interface LogoProps {
  className?: string
  alt?: string
}

/** The GSS logo. The image path is defined once in src/config/brand.ts. */
export function Logo({ className, alt = brand.name }: LogoProps) {
  return <img src={brand.logo} alt={alt} className={cn('w-auto select-none', className)} />
}
