import { Buffer } from 'node:buffer'

/**
 * A one-page PDF with these lines of text in Helvetica, built in code so the repository holds no
 * binary fixture (as the backend's tests do, 19.1). The server opens it with PDFBox like any upload.
 */
export function pdfWithLines(lines: string[]): Buffer {
  const text = lines
    .map(
      (line, index) => `${index === 0 ? '' : '0 -18 Td '}(${escape(line)}) Tj`,
    )
    .join('\n')
  const content = `BT /F1 12 Tf 72 720 Td\n${text}\nET`
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R' +
      ' /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(content, 'latin1')} >>\nstream\n${content}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
  ]
  let pdf = '%PDF-1.4\n'
  const offsets: number[] = []
  objects.forEach((body, index) => {
    offsets.push(Buffer.byteLength(pdf, 'latin1'))
    pdf += `${index + 1} 0 obj\n${body}\nendobj\n`
  })
  const xref = Buffer.byteLength(pdf, 'latin1')
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  pdf += offsets
    .map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`)
    .join('')
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return Buffer.from(pdf, 'latin1')
}

function escape(line: string): string {
  return line.replace(/[\\()]/g, (c) => `\\${c}`)
}
