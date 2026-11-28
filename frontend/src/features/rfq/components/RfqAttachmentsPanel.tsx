import { Download, Eye, FileText, Loader2, ShieldCheck, UploadCloud, X } from 'lucide-react'
import * as React from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { downloadAttachment, uploadAttachment, type AttachmentDto } from '@/features/rfq/api/rfqApi'
import { type RfqRecord } from '@/features/rfq/types'
import { queryClient } from '@/lib/query-client'
import { normalizeError, unexpectedErrorMessage } from '@/lib/errors'
import { EmptyMessage, SectionHeading } from './OverviewPanels'

export function FilesPanel({
  rfq,
  demo,
  canUpload,
  attachments,
  loading,
  demoFiles,
  setDemoFiles,
  saving,
  setSaving,
}: {
  rfq: RfqRecord
  demo: boolean
  canUpload: boolean
  attachments: AttachmentDto[]
  loading: boolean
  demoFiles: Array<{ name: string; size: number; category: string; addedAt: string }>
  setDemoFiles: React.Dispatch<
    React.SetStateAction<Array<{ name: string; size: number; category: string; addedAt: string }>>
  >
  saving: boolean
  setSaving: (saving: boolean) => void
}) {
  const [category, setCategory] = React.useState('OTHER')
  const [selected, setSelected] = React.useState<File | null>(null)
  const [fileError, setFileError] = React.useState('')
  const [busyDownload, setBusyDownload] = React.useState<string | null>(null)
  const [preview, setPreview] = React.useState<{ name: string; mediaType: string; url: string } | null>(null)
  React.useEffect(() => {
    const url = preview?.url
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [preview?.url])

  React.useEffect(() => {
    if (!preview) return
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setPreview(null)
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [preview])
  const allowed = [
    'pdf',
    'png',
    'jpg',
    'jpeg',
    'webp',
    'doc',
    'docx',
    'xls',
    'xlsx',
    'csv',
    'txt',
    'ppt',
    'pptx',
  ]
  const stored = demo ? [] : attachments
  const displayed = [
    ...(rfq.sourceFileName
      ? [
          {
            id: 'source',
            fileName: rfq.sourceFileName,
            category: 'CUSTOMER_REQUEST',
            scanState: 'CLEAN',
            sizeBytes: 0,
            createdAt: rfq.createdAt,
          } as AttachmentDto,
        ]
      : []),
    ...stored,
  ]

  function chooseFile(file?: File) {
    setFileError('')
    setSelected(null)
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!allowed.includes(extension ?? '')) {
      setFileError(
        'This file type is not supported. Use PDF, image, Word, Excel, PowerPoint, CSV, or text.',
      )
      return
    }
    if (!file.size || file.size > 25 * 1024 * 1024) {
      setFileError(file.size ? 'Maximum attachment size is 25 MB.' : 'The selected file is empty.')
      return
    }
    setSelected(file)
  }
  async function uploadSelected() {
    if (!selected) return
    setSaving(true)
    try {
      if (demo)
        setDemoFiles((files) => [
          ...files,
          { name: selected.name, size: selected.size, category, addedAt: new Date().toISOString() },
        ])
      else {
        await uploadAttachment(rfq.id, selected, category)
        await queryClient.invalidateQueries({ queryKey: ['rfqs', rfq.id, 'attachments'] })
      }
      toast.success('Attachment added. It is quarantined until malware scanning completes.')
      setSelected(null)
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setSaving(false)
    }
  }
  async function download(row: AttachmentDto) {
    if (row.id === 'source') {
      toast.info('Demo source file is represented by sample metadata.')
      return
    }
    setBusyDownload(row.id)
    try {
      const blob = await downloadAttachment(rfq.id, row.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = row.fileName
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusyDownload(null)
    }
  }

  async function previewAttachment(row: AttachmentDto) {
    if (row.scanState !== 'CLEAN' || row.id === 'source') return
    if (row.mediaType !== 'application/pdf' && !row.mediaType.startsWith('image/')) return
    setBusyDownload(row.id)
    try {
      const original = await downloadAttachment(rfq.id, row.id)
      const blob = new Blob([original], { type: row.mediaType })
      setPreview({ name: row.fileName, mediaType: row.mediaType, url: URL.createObjectURL(blob) })
    } catch (error) {
      toast.error(unexpectedErrorMessage(normalizeError(error)))
    } finally {
      setBusyDownload(null)
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <SectionHeading
          title="RFQ attachments"
          description="Source documents, line-item images, customer responses, and quotation support files."
        />
      </div>
      {canUpload && (
        <div className="rounded-xl border border-dashed border-brand-200 bg-brand-50/40 p-4">
          <div className="grid gap-3 sm:grid-cols-[1fr_180px_auto]">
            <label className="flex min-w-0 cursor-pointer items-center gap-3 rounded-lg border border-input bg-white px-3 py-3 hover:border-brand-200">
              <UploadCloud className="size-5 shrink-0 text-brand-900" />
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">
                  {selected?.name ?? 'Choose supporting file'}
                </span>
                <span className="mt-0.5 block text-xs text-slate-500">
                  PDF, image, Word, Excel, PowerPoint, CSV, text · max 25 MB
                </span>
              </span>
              <input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.webp,.doc,.docx,.xls,.xlsx,.csv,.txt,.ppt,.pptx"
                onChange={(event) => chooseFile(event.target.files?.[0])}
                className="sr-only"
              />
            </label>
            <select
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              className="h-11 rounded-lg border border-input bg-white px-3 text-sm focus-visible:border-brand-900 focus-visible:ring-2 focus-visible:ring-brand-900/20 focus-visible:outline-none"
            >
              <option value="OTHER">Other document</option>
              <option value="CUSTOMER_REQUEST">Customer request</option>
              <option value="QUOTATION_SUPPORT">Quotation support</option>
              <option value="REVIEW_SUPPORT">Review support</option>
              <option value="CUSTOMER_RESPONSE">Customer response</option>
            </select>
            <Button
              type="button"
              onClick={() => void uploadSelected()}
              disabled={!selected || saving}
            >
              {saving ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <UploadCloud className="size-4" />
              )}
              Upload
            </Button>
          </div>
          {fileError && (
            <p className="mt-2 text-xs text-red-brand-700" role="alert">
              {fileError}
            </p>
          )}
          <p className="mt-2 flex items-start gap-2 text-[11px] leading-4 text-slate-500">
            <ShieldCheck className="mt-0.5 size-3.5 shrink-0 text-emerald-600" />
            The server validates content and stores the file in a quarantined state. Download
            remains unavailable until it is marked clean by the scanner.
          </p>
        </div>
      )}
      {loading ? (
        <p className="py-8 text-center text-sm text-slate-500">Loading attachments…</p>
      ) : displayed.length || demoFiles.length ? (
        <div className="space-y-2">
          {[
            ...displayed.map((row) => ({ ...row, demo: false })),
            ...demoFiles.map(
              (row, index) =>
                ({
                  id: `demo-${index}`,
                  fileName: row.name,
                  category: row.category,
                  scanState: 'PENDING_SCAN',
                  sizeBytes: row.size,
                  createdAt: row.addedAt,
                  demo: true,
                }) as AttachmentDto & { demo: boolean },
            ),
          ].map((row) => (
            <div
              key={row.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-brand-100 bg-white p-3"
            >
              <div className="flex min-w-0 items-center gap-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-900">
                  <FileText className="size-4" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{row.fileName}</p>
                  <p className="mt-0.5 text-[11px] text-slate-500">
                    {row.category.replaceAll('_', ' ')} ·{' '}
                    {row.sizeBytes ? `${(row.sizeBytes / 1024 / 1024).toFixed(2)} MB · ` : ''}
                    {new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(
                      new Date(row.createdAt),
                    )}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className={`rounded px-2 py-1 text-[10px] font-semibold ${row.scanState === 'CLEAN' ? 'bg-emerald-50 text-emerald-800' : row.scanState === 'INFECTED' ? 'bg-red-brand-50 text-red-brand-700' : 'bg-amber-50 text-amber-800'}`}
                >
                  {row.scanState === 'CLEAN' ? 'Clean' : row.scanState.replaceAll('_', ' ')}
                </span>
                {row.scanState === 'CLEAN' && (
                  <>
                    {(row.id !== 'source' &&
                      (row.mediaType === 'application/pdf' || row.mediaType.startsWith('image/'))) && (
                      <Button
                        type="button"
                        size="icon"
                        variant="ghost"
                        aria-label={`Preview ${row.fileName}`}
                        disabled={busyDownload === row.id}
                        onClick={() => void previewAttachment(row)}
                      >
                        <Eye className="size-4" />
                      </Button>
                    )}
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      aria-label={`Download ${row.fileName}`}
                      disabled={busyDownload === row.id}
                      onClick={() => void download(row)}
                    >
                      <Download className="size-4" />
                    </Button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyMessage>No files have been attached yet.</EmptyMessage>
      )}
      {demo && demoFiles.length > 0 && (
        <p className="text-xs text-slate-500">
          Demo uploads are only recorded in this open screen; they are not sent to storage.
        </p>
      )}
      {preview && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-brand-950/70 p-3 sm:p-6">
          <button
            type="button"
            aria-label="Close attachment preview"
            className="absolute inset-0 size-full cursor-default"
            onClick={() => setPreview(null)}
          />
          <section
            role="dialog"
            aria-modal="true"
            aria-label={`Attachment preview: ${preview.name}`}
            className="relative flex max-h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl"
          >
            <header className="flex items-center justify-between gap-3 border-b border-brand-100 px-4 py-3">
              <h2 className="min-w-0 truncate text-sm font-semibold text-brand-900">{preview.name}</h2>
              <Button type="button" size="icon" variant="ghost" aria-label="Close preview" onClick={() => setPreview(null)}>
                <X className="size-4" />
              </Button>
            </header>
            <div className="min-h-0 flex-1 overflow-auto bg-brand-50 p-3">
              {preview.mediaType.startsWith('image/') ? (
                <img className="mx-auto max-h-[78vh] max-w-full object-contain" src={preview.url} alt={preview.name} />
              ) : (
                <iframe className="h-[78vh] w-full rounded bg-white" src={preview.url} title={`PDF preview: ${preview.name}`} />
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
