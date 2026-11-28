import { Circle } from 'lucide-react'
import { type WorkflowEvent } from '@/features/rfq/types'
import { EmptyMessage, SectionHeading } from './OverviewPanels'

export function HistoryPanel({ events }: { events: WorkflowEvent[] }) {
  return (
    <div>
      <SectionHeading
        title="Workflow history"
        description="Traceable RFQ events, actors, timestamps, and decision context."
      />
      <div className="mt-5">
        {events.length ? (
          <ol className="space-y-0">
            {events.map((event, index) => (
              <li key={event.id} className="relative flex gap-4 pb-6 last:pb-0">
                <div className="flex flex-col items-center">
                  <span
                    className={`z-10 flex size-8 shrink-0 items-center justify-center rounded-full ${event.tone === 'green' ? 'bg-emerald-100 text-emerald-700' : event.tone === 'red' ? 'bg-red-brand-100 text-red-brand-700' : event.tone === 'amber' ? 'bg-amber-100 text-amber-700' : 'bg-brand-100 text-brand-900'}`}
                  >
                    <Circle className="size-2.5 fill-current" />
                  </span>
                  {index < events.length - 1 && (
                    <span className="absolute top-8 bottom-0 w-px bg-brand-100" />
                  )}
                </div>
                <div className="min-w-0 flex-1 pt-0.5">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <h3 className="text-sm font-semibold text-slate-800 capitalize">
                      {event.label}
                    </h3>
                    <time dateTime={event.createdAt} className="text-[11px] text-slate-400">
                      {event.createdAt
                        ? new Intl.DateTimeFormat('en-IN', {
                            dateStyle: 'medium',
                            timeStyle: 'short',
                          }).format(new Date(event.createdAt))
                        : ''}
                    </time>
                  </div>
                  <p className="mt-1 text-sm leading-5 whitespace-pre-wrap text-slate-600">
                    {event.detail}
                  </p>
                  <p className="mt-1.5 text-xs text-slate-400">Recorded by {event.actor}</p>
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <EmptyMessage>Workflow events will appear after import and assignment.</EmptyMessage>
        )}
      </div>
    </div>
  )
}
