import { describe, expect, it } from 'vitest'
import { parseAnswer, parseInline, type Block, type Inline } from './answerText'

/** The text of inline nodes, with markers as [n] and bold as **…**, for short assertions. */
function flat(nodes: Inline[]): string {
  return nodes
    .map((node) => {
      switch (node.kind) {
        case 'text':
          return node.text
        case 'strong':
          return `**${flat(node.children)}**`
        case 'em':
          return `_${flat(node.children)}_`
        case 'marker':
          return `[${node.ordinals.join(',')}]`
        case 'break':
          return '\n'
      }
    })
    .join('')
}

function shape(blocks: Block[]) {
  return blocks.map((block) =>
    block.kind === 'list'
      ? { [block.ordered ? 'ol' : 'ul']: block.items.map(flat) }
      : { [block.kind === 'heading' ? 'h' : 'p']: flat(block.content) },
  )
}

describe('parseAnswer', () => {
  it('reads paragraphs, line breaks, headings and both kinds of list', () => {
    const text = [
      'The course has two modes. [1]',
      '',
      '## Online',
      '- Assignments: 25 marks',
      '- External exam: 75 marks',
      '',
      'Steps:',
      '1. Register',
      '2. Sit the exam',
      'Last line',
      'goes on here',
    ].join('\n')

    expect(shape(parseAnswer(text))).toEqual([
      { p: 'The course has two modes. [1]' },
      { h: 'Online' },
      { ul: ['Assignments: 25 marks', 'External exam: 75 marks'] },
      { p: 'Steps:' },
      { ol: ['Register', 'Sit the exam'] },
      { p: 'Last line\ngoes on here' },
    ])
  })

  it('keeps a # that ends a heading word, and drops closing hashes', () => {
    expect(shape(parseAnswer('## Notes on C#\n\n## Units ##'))).toEqual([
      { h: 'Notes on C#' },
      { h: 'Units' },
    ])
  })

  it('keeps the number an ordered list starts at', () => {
    const [list] = parseAnswer('3. Third\n4. Fourth')
    expect(list).toMatchObject({ kind: 'list', ordered: true, start: 3 })
  })

  it('reads bold labels and italics, with markers inside them', () => {
    expect(flat(parseInline('**ONLINE (through NPTEL):** 25 marks [1]'))).toBe(
      '**ONLINE (through NPTEL):** 25 marks [1]',
    )
    const [strong] = parseInline('**Total 40/100 [2]**')
    expect(strong).toEqual({
      kind: 'strong',
      children: [
        { kind: 'text', text: 'Total 40/100 ' },
        { kind: 'marker', ordinals: [2] },
      ],
    })
    expect(flat(parseInline('an *important* rule'))).toBe('an _important_ rule')
  })

  it('leaves underscores inside names and lone asterisks as they are', () => {
    expect(
      parseInline('CSET203_Microprocessor_Course_Evaluation Policy'),
    ).toEqual([
      {
        kind: 'text',
        text: 'CSET203_Microprocessor_Course_Evaluation Policy',
      },
    ])
    expect(parseInline('5 * 3 = 15 and a ** b')).toEqual([
      { kind: 'text', text: '5 * 3 = 15 and a ** b' },
    ])
  })

  it('keeps HTML as plain text, never as an element', () => {
    expect(
      parseAnswer('<img src=x onerror="alert(1)"> and <b>Bold?</b>'),
    ).toEqual([
      {
        kind: 'paragraph',
        content: [
          {
            kind: 'text',
            text: '<img src=x onerror="alert(1)"> and <b>Bold?</b>',
          },
        ],
      },
    ])
  })

  it('joins markers written next to each other into one group', () => {
    expect(parseInline('Both say so [1][3].')).toEqual([
      { kind: 'text', text: 'Both say so ' },
      { kind: 'marker', ordinals: [1, 3] },
      { kind: 'text', text: '.' },
    ])
  })

  it('shows a marker repeated on every item once, at the end of the list', () => {
    const [list] = parseAnswer(
      '- Input output [1]\n- Operators [1]\n- Switch [1]',
    )

    expect(shape([list])).toEqual([
      { ul: ['Input output', 'Operators', 'Switch [1]'] },
    ])
  })

  it('shows a marker repeated after every sentence of a paragraph once, at its end', () => {
    const [paragraph] = parseAnswer(
      'Two modes [1]. Both mandatory [1]. Certificate after both [1].',
    )

    expect(shape([paragraph])).toEqual([
      { p: 'Two modes. Both mandatory. Certificate after both [1].' },
    ])
  })

  it('never drops a marker that names other sources', () => {
    const [list] = parseAnswer('- A [1]\n- B [2]\n- C [1]\n- D [1]')

    expect(shape([list])).toEqual([{ ul: ['A [1]', 'B [2]', 'C', 'D [1]'] }])
  })

  it('keeps repeated markers in separate paragraphs', () => {
    expect(shape(parseAnswer('First [1].\n\nSecond [1].'))).toEqual([
      { p: 'First [1].' },
      { p: 'Second [1].' },
    ])
  })
})
