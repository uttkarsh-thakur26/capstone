import { describe, expect, it } from 'vitest'
import { formatDateTime } from './format'

describe('formatDateTime', () => {
  // The API sends instants in UTC with their Z (ADR-016, ADR-040).
  const uploaded = '2026-11-03T06:03:14.323185Z'

  it("shows an instant from the server in the reader's time zone, with the zone", () => {
    const india = formatDateTime(uploaded, 'Asia/Kolkata')
    expect(india).toMatch(/11:33/)
    expect(india).toMatch(/3 Nov 2026|Nov 3, 2026/)
    expect(india).toMatch(/IST|GMT\+5:30/)
  })

  it("shows the same instant in another zone as that zone's time", () => {
    const utc = formatDateTime(uploaded, 'UTC')
    expect(utc).toMatch(/6:03/)
    expect(utc).toMatch(/UTC/)
    expect(formatDateTime(uploaded, 'America/New_York')).toMatch(/1:03/)
  })

  it('moves to the next day when the zone is ahead of UTC late at night', () => {
    expect(formatDateTime('2026-11-03T20:00:00Z', 'Asia/Kolkata')).toMatch(
      /4 Nov 2026|Nov 4, 2026/,
    )
  })
})
