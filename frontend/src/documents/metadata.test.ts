import { describe, expect, it } from 'vitest'
import { latestDate } from './metadata'

describe('latestDate', () => {
  it('is one year after the reader’s today', () => {
    expect(latestDate(new Date(2026, 10, 9))).toBe('2027-11-09')
  })

  it('takes 29 February to 28 February a year later, as the server does', () => {
    // Java's plusYears(1) keeps February: 2028-02-29 becomes 2029-02-28. Rolling over to
    // 1 March would let the form accept a day the server refuses.
    expect(latestDate(new Date(2028, 1, 29))).toBe('2029-02-28')
  })
})
