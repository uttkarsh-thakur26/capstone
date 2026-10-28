import { describe, expect, it } from 'vitest'
import { EXCERPT_CHARS, excerptRange, segments } from './excerpt'

describe('segments', () => {
  it('marks the highlighted runs and keeps the rest as plain text', () => {
    const text = 'Submit the form by Friday'

    expect(
      segments(text, [
        { start: 11, end: 15 },
        { start: 19, end: 25 },
      ]),
    ).toEqual([
      { text: 'Submit the ', marked: false },
      { text: 'form', marked: true },
      { text: ' by ', marked: false },
      { text: 'Friday', marked: true },
    ])
  })

  it('clips highlights to the excerpt', () => {
    expect(segments('abcdefgh', [{ start: 1, end: 5 }], 3, 6)).toEqual([
      { text: 'de', marked: true },
      { text: 'f', marked: false },
    ])
  })
})

describe('excerptRange', () => {
  it('shows a short passage whole', () => {
    expect(excerptRange('A short rule.', [])).toEqual({ start: 0, end: 13 })
  })

  it('starts at the beginning and ends on a space', () => {
    const text = 'word '.repeat(100)
    const { start, end } = excerptRange(text, [])

    expect(start).toBe(0)
    expect(end).toBeLessThanOrEqual(EXCERPT_CHARS)
    expect(text[end]).toBe(' ')
  })

  it('moves to the first matched word when it would be cut off', () => {
    const text = `${'filler '.repeat(80)}deadline ${'more '.repeat(40)}`
    const at = text.indexOf('deadline')
    const { start, end } = excerptRange(text, [{ start: at, end: at + 8 }])

    expect(start).toBeGreaterThan(0)
    expect(start).toBeLessThan(at)
    expect(end).toBeGreaterThan(at + 8)
    expect(text[start - 1]).toBe(' ')
  })
})
