import { describe, it, expect } from 'vitest'
import * as React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OtpInput } from '@/features/auth/components/OtpInput'

function Harness() {
  const [value, setValue] = React.useState('')
  return <OtpInput value={value} onChange={setValue} />
}

const box = (n: number) => screen.getByLabelText<HTMLInputElement>(`Digit ${n} of 6`)

describe('OtpInput', () => {
  it('shows each digit as it is typed and moves focus forward', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await user.click(box(1))
    await user.keyboard('123456')
    expect([1, 2, 3, 4, 5, 6].map((n) => box(n).value).join('')).toBe('123456')
    expect(box(6)).toHaveFocus()
  })

  it('ignores non-digit characters', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await user.click(box(1))
    await user.keyboard('a1b2')
    expect(box(1).value).toBe('1')
    expect(box(2).value).toBe('2')
    expect(box(3).value).toBe('')
  })

  it('backspace removes the last digit and steps back', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await user.click(box(1))
    await user.keyboard('123')
    await user.keyboard('{Backspace}')
    expect(box(3).value).toBe('')
    expect(box(2).value).toBe('2')
    await user.keyboard('{Backspace}')
    expect(box(2).value).toBe('')
    expect(box(1).value).toBe('1')
  })

  it('fills every box on paste', () => {
    render(<Harness />)
    fireEvent.paste(box(1), { clipboardData: { getData: () => '987654' } })
    expect([1, 2, 3, 4, 5, 6].map((n) => box(n).value).join('')).toBe('987654')
  })
})
