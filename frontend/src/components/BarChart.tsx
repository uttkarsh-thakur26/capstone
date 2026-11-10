import { useState } from 'react'
import { TABLE_HEAD, TABLE_ROW } from './ui'

/** One bar: a day and its value for each series, stacked in series order. */
export interface BarDatum {
  date: string
  values: number[]
}

export interface BarSeries {
  label: string
  /** A CSS colour, normally one of the --series-N tokens of index.css. */
  color: string
}

const WIDTH = 640
const HEIGHT = 180
const LEFT = 44
const BOTTOM = 22
const TOP = 8
const GAP = 2

/** "9 Nov" for an ISO date, read as a calendar day (no time zone shift). */
function shortDay(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
  })
}

/**
 * Bars per day, stacked when there are several series. Plain SVG with no chart library: thin bars
 * from a zero baseline, a 2 px gap between stacked parts, rounded tops, a recessive grid with
 * three values, a legend for two or more series, a tooltip on hover, and the same numbers as a
 * table for keyboards, screen readers and anyone who cannot tell the colours apart.
 */
export function BarChart({
  label,
  data,
  series,
  format = (n) => String(n),
}: {
  label: string
  data: BarDatum[]
  series: BarSeries[]
  format?: (value: number) => string
}) {
  const [hovered, setHovered] = useState<number | null>(null)
  const totals = data.map((d) => d.values.reduce((a, b) => a + b, 0))
  const max = Math.max(...totals, 0)
  const scaleMax = max === 0 ? 1 : niceCeiling(max)
  const plotWidth = WIDTH - LEFT
  const plotHeight = HEIGHT - BOTTOM - TOP
  const step = plotWidth / Math.max(data.length, 1)
  const barWidth = Math.max(2, Math.min(24, step * 0.6))
  const y = (value: number) =>
    TOP + plotHeight - (value / scaleMax) * plotHeight
  const ticks = [0, scaleMax / 2, scaleMax]
  const labelled = new Set([
    0,
    Math.floor((data.length - 1) / 2),
    data.length - 1,
  ])
  const active = hovered === null ? null : data[hovered]

  return (
    <figure className="space-y-3">
      {series.length > 1 && (
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
          {series.map((s) => (
            <li key={s.label} className="flex items-center gap-1.5">
              <span
                aria-hidden="true"
                className="inline-block h-2.5 w-2.5 rounded-sm"
                style={{ background: s.color }}
              />
              {s.label}
            </li>
          ))}
        </ul>
      )}
      <div className="relative">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          className="h-auto w-full"
          role="img"
          aria-label={`${label}. The table below lists every value.`}
          onMouseLeave={() => setHovered(null)}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line
                x1={LEFT}
                x2={WIDTH}
                y1={y(tick)}
                y2={y(tick)}
                stroke="var(--line)"
                strokeWidth={1}
              />
              <text
                x={LEFT - 6}
                y={y(tick) + 4}
                textAnchor="end"
                fontSize={11}
                fill="var(--muted)"
              >
                {format(tick)}
              </text>
            </g>
          ))}
          {data.map((d, i) => {
            const x = LEFT + i * step + (step - barWidth) / 2
            let base = 0
            const lastShown = d.values.reduce(
              (last, value, s) => (value > 0 ? s : last),
              -1,
            )
            return (
              <g key={d.date}>
                {d.values.map((value, s) => {
                  if (value <= 0) return null
                  const bottom = y(base)
                  base += value
                  const top = y(base)
                  const height = Math.max(1, bottom - top - (s > 0 ? GAP : 0))
                  return (
                    <path
                      key={series[s].label}
                      d={barPath(
                        x,
                        bottom - (s > 0 ? GAP : 0) - height,
                        barWidth,
                        height,
                        s === lastShown,
                      )}
                      fill={series[s].color}
                      opacity={hovered === null || hovered === i ? 1 : 0.45}
                    />
                  )
                })}
                {/* A hit area taller and wider than the bar. */}
                <rect
                  x={LEFT + i * step}
                  y={TOP}
                  width={step}
                  height={plotHeight}
                  fill="transparent"
                  onMouseEnter={() => setHovered(i)}
                />
                {labelled.has(i) && (
                  <text
                    x={LEFT + i * step + step / 2}
                    y={HEIGHT - 6}
                    textAnchor="middle"
                    fontSize={11}
                    fill="var(--muted)"
                  >
                    {shortDay(d.date)}
                  </text>
                )}
              </g>
            )
          })}
          <line
            x1={LEFT}
            x2={WIDTH}
            y1={y(0)}
            y2={y(0)}
            stroke="var(--line-strong)"
            strokeWidth={1}
          />
        </svg>
        {active && hovered !== null && (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute top-0 rounded-md border border-line bg-surface px-3 py-2 text-xs shadow-md"
            style={{
              left: `${Math.min(80, ((LEFT + hovered * step) / WIDTH) * 100)}%`,
            }}
          >
            <p className="font-medium">{shortDay(active.date)}</p>
            {series.map((s, index) => (
              <p key={s.label} className="flex items-center gap-1.5 text-muted">
                <span
                  className="inline-block h-2 w-2 rounded-sm"
                  style={{ background: s.color }}
                />
                {s.label}:{' '}
                <span className="text-fg">{format(active.values[index])}</span>
              </p>
            ))}
          </div>
        )}
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer text-muted">Show as a table</summary>
        <div className="mt-2 max-h-64 overflow-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">{label}</caption>
            <thead className={TABLE_HEAD}>
              <tr>
                <th scope="col" className="px-2 py-1">
                  Day
                </th>
                {series.map((s) => (
                  <th
                    key={s.label}
                    scope="col"
                    className="px-2 py-1 text-right"
                  >
                    {s.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((d) => (
                <tr key={d.date} className={TABLE_ROW}>
                  <th scope="row" className="px-2 py-1 font-normal">
                    {shortDay(d.date)}
                  </th>
                  {d.values.map((value, s) => (
                    <td
                      key={series[s].label}
                      className="px-2 py-1 text-right tabular-nums"
                    >
                      {format(value)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  )
}

/** A bar with its top corners rounded when it is the top of its stack. */
function barPath(
  x: number,
  top: number,
  width: number,
  height: number,
  rounded: boolean,
): string {
  const r = rounded ? Math.min(4, width / 2, height) : 0
  return [
    `M${x},${top + height}`,
    `V${top + r}`,
    r ? `Q${x},${top} ${x + r},${top}` : '',
    `H${x + width - r}`,
    r ? `Q${x + width},${top} ${x + width},${top + r}` : '',
    `V${top + height}`,
    'Z',
  ].join(' ')
}

/** The axis top: 1, 2 or 5 times a power of ten, at or above the largest value. */
function niceCeiling(value: number): number {
  const power = 10 ** Math.floor(Math.log10(value))
  for (const m of [1, 2, 5, 10]) {
    if (m * power >= value) return m * power
  }
  return 10 * power
}
