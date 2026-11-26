import { fireEvent, screen, waitFor } from '@testing-library/react'
import type * as React from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CreateRfqPage } from '@/routes/_authenticated.rfqs.create'
import { ImportRfqPage } from '@/routes/_authenticated.rfqs.import'
import { renderWithProviders } from './test-utils'

const navigateMock = vi.fn().mockResolvedValue(undefined)
const previewImportMock =
  vi.fn<
    (
      file: File,
      sourceSystem: string,
      sourceRfqId?: string,
    ) => Promise<{
      fileName: string
      detectedFormat: string
      normalizedRfq: Record<string, unknown>
      fieldMappings: Record<string, string>
      warnings: string[]
      previewToken: string
      expiresAt: string
    }>
  >()
const createRfqMock = vi.fn<(payload: Record<string, unknown>) => Promise<{ id: string }>>()

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => () => ({}),
  useNavigate: () => navigateMock,
  Link: ({ children, ...props }: { children: React.ReactNode; to?: string }) => (
    <a href={props.to ?? '#'}>{children}</a>
  ),
}))

vi.mock('@/components/layout/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/features/auth/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, fullName: 'System Admin', roles: ['ADMIN'] },
    hasAnyRole: () => true,
  }),
}))

vi.mock('@/features/auth/demoSession', () => ({
  isDemoSession: () => false,
}))

vi.mock('@/features/rfq/api/rfqApi', () => ({
  previewImport: (file: File, sourceSystem: string, sourceRfqId?: string) =>
    previewImportMock(file, sourceSystem, sourceRfqId),
  confirmImport: vi.fn().mockResolvedValue({ rfq: { id: 'rfq-1' } }),
  createRfq: (payload: Record<string, unknown>) => createRfqMock(payload),
  uploadAttachment: vi.fn().mockResolvedValue({}),
}))

describe('ImportRfqPage and CreateRfqPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('allows previewing an Excel upload without pre-entering Customer RFQ number and extracts PR # from file', async () => {
    previewImportMock.mockResolvedValueOnce({
      fileName: 'v2x-110627.xlsx',
      detectedFormat: 'EXCEL',
      normalizedRfq: {
        sourceSystem: 'CUSTOMER_SOFTWARE',
        sourceRfqId: '110627',
        customerName: 'V2X Systems Corporation',
        customerCompany: 'V2X Systems Corporation',
        requestTitle: 'Request for Quote 110627',
        lineItems: [
          {
            sourceLineId: '1',
            lineNumber: 1,
            description: 'BELT, V, DUST BIN, RUBBER',
            requestedQuantity: 4,
            unitOfMeasure: 'EA',
            customerPartNumber: '3030-09-V00-0119',
            requestedManufacturer: 'BUFFALO TURBINE',
            requestedPartNumber: '1291',
            taaRequired: false,
          },
        ],
      },
      fieldMappings: { 'PR #': 'sourceRfqId' },
      warnings: [],
      previewToken: '11111111-1111-1111-1111-111111111111',
      expiresAt: new Date(Date.now() + 600_000).toISOString(),
    })

    renderWithProviders(<ImportRfqPage />)

    const fileInput = document.getElementById('source-file') as HTMLInputElement
    const excelFile = new File(['dummy-xlsx'], 'v2x-110627.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    fireEvent.change(fileInput, { target: { files: [excelFile] } })

    const previewBtn = await screen.findByRole('button', { name: /Preview import/i })
    expect(previewBtn).not.toBeDisabled()
    fireEvent.click(previewBtn)

    await waitFor(() =>
      expect(previewImportMock).toHaveBeenCalledWith(excelFile, 'CUSTOMER_SOFTWARE', undefined),
    )

    const prInput = await screen.findByLabelText(/Customer RFQ \/ PR number/i)
    expect((prInput as HTMLInputElement).value).toBe('110627')
  })

  it('prompts Admin to manually enter PR # when the uploaded file has no PR number', async () => {
    previewImportMock.mockResolvedValueOnce({
      fileName: 'no-pr.xlsx',
      detectedFormat: 'EXCEL',
      normalizedRfq: {
        sourceSystem: 'CUSTOMER_SOFTWARE',
        sourceRfqId: '',
        customerName: 'V2X Systems Corporation',
        requestTitle: '',
        lineItems: [
          {
            sourceLineId: '1',
            lineNumber: 1,
            description: 'Valve assembly',
            requestedQuantity: 2,
            unitOfMeasure: 'EA',
          },
        ],
      },
      fieldMappings: {},
      warnings: ['PR / Customer RFQ # was not found in the workbook.'],
      previewToken: '11111111-1111-1111-1111-111111111111',
      expiresAt: new Date(Date.now() + 600_000).toISOString(),
    })

    renderWithProviders(<ImportRfqPage />)

    const fileInput = document.getElementById('source-file') as HTMLInputElement
    const excelFile = new File(['dummy-xlsx'], 'no-pr.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    fireEvent.change(fileInput, { target: { files: [excelFile] } })
    fireEvent.click(screen.getByRole('button', { name: /Preview import/i }))

    expect(
      await screen.findByText(/PR \/ Customer RFQ number was not found in the uploaded file/i),
    ).toBeInTheDocument()
  })

  it('submits V2X / Customer Part #, Due Time, Terms, and Source URL from CreateRfqPage', async () => {
    createRfqMock.mockResolvedValueOnce({ id: 'rfq-created-1' })

    renderWithProviders(<CreateRfqPage />)

    fireEvent.change(screen.getByPlaceholderText('PR Number *'), { target: { value: '110627' } })
    fireEvent.change(screen.getByPlaceholderText('Name *'), {
      target: { value: 'V2X Systems Corporation' },
    })
    fireEvent.change(screen.getByPlaceholderText('V2X part #'), {
      target: { value: '3030-09-V00-0119' },
    })
    fireEvent.change(screen.getByPlaceholderText('Description'), {
      target: { value: 'BELT, V, DUST BIN, RUBBER' },
    })
    fireEvent.change(screen.getByPlaceholderText('https://...'), {
      target: { value: 'https://v2x.procuredinc.com/60277' },
    })
    fireEvent.change(screen.getByPlaceholderText(/TERMS OF PAYMENT: NET 60/i), {
      target: { value: 'TERMS OF PAYMENT: NET 60' },
    })

    fireEvent.click(screen.getByRole('button', { name: /Create RFQ/i }))

    await waitFor(() => expect(createRfqMock).toHaveBeenCalledTimes(1))
    const sentPayload = createRfqMock.mock.calls[0]?.[0]
    expect(sentPayload?.['sourceUrl']).toBe('https://v2x.procuredinc.com/60277')
    expect(sentPayload?.['termsAndConditions']).toBe('TERMS OF PAYMENT: NET 60')
    const lineItems = sentPayload?.['lineItems'] as Array<Record<string, unknown>>
    expect(lineItems[0]?.['customerPartNumber']).toBe('3030-09-V00-0119')
  })
})
