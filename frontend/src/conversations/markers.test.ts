import { describe, expect, it } from 'vitest'
import { segments } from './markers'

describe('segments', () => {
  it('splits an answer into text and the markers of its sources', () => {
    expect(segments('It lasts 55 minutes [1]. Marks [2, 3] apply.')).toEqual([
      { kind: 'text', text: 'It lasts 55 minutes ' },
      { kind: 'marker', text: '[1]', ordinals: [1] },
      { kind: 'text', text: '. Marks ' },
      { kind: 'marker', text: '[2, 3]', ordinals: [2, 3] },
      { kind: 'text', text: ' apply.' },
    ])
  })

  it('leaves a year in brackets and other brackets as text', () => {
    expect(segments('Regulations [2026] and [a] apply.')).toEqual([
      { kind: 'text', text: 'Regulations [2026] and [a] apply.' },
    ])
  })

  it('keeps markup as text: segments are never HTML', () => {
    expect(segments('<img src=x onerror=alert(1)> [1]')).toEqual([
      { kind: 'text', text: '<img src=x onerror=alert(1)> ' },
      { kind: 'marker', text: '[1]', ordinals: [1] },
    ])
  })
})
