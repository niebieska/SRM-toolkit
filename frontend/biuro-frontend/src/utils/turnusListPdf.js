import { exportSubtitle, exportTimestamp } from './turnusListExportData.js'

const widths = {
  firstName: 62, lastName: 75, pesel: 70, address: 125, birthDate: 65, gender: 50,
  age: 26, personPhone: 70, contactPhone: 90, roleLabel: 105, email: 120,
  contactName: 110, guardianEmail: 120, contactRelation: 85, city: 85,
  qualifications: 140, imageConsent: 90,
}
const margin = 28, cellPadding = 4, fontSize = 9
const portraitWidth = 595.28, landscapeWidth = 841.89

export class PdfLayoutError extends Error {}

export function pdfLayout(model) {
  const needed = Math.max(0, ...model.sections.map(section =>
    section.columns.reduce((sum, column) => sum + (widths[column.key] || 90) + cellPadding * 2, 0)))
  if (needed > landscapeWidth - margin * 2) {
    throw new PdfLayoutError('Wybrane kolumny nie mieszczą się czytelnie w PDF. Wybierz mniej kolumn w widoku Własny lub użyj XLSX.')
  }
  const orientation = needed > portraitWidth - margin * 2 ? 'landscape' : 'portrait'
  return {
    orientation,
    availableWidth: (orientation === 'landscape' ? landscapeWidth : portraitWidth) - margin * 2,
    // Reserve space for page margins, repeated group/column headers and padding.
    maxRowHeight: (orientation === 'landscape' ? portraitWidth : landscapeWidth) - 180,
  }
}

function sectionWidths(section, layout) {
  const minimum = section.columns.map(column => widths[column.key] || 90)
  const free = layout.availableWidth - minimum.reduce((sum, width) => sum + width + cellPadding * 2, 0)
  return minimum.map(width => width + free / minimum.length)
}

function validateRowHeights(section, columnWidths, layout) {
  for (const row of section.rows) {
    const maxLines = Math.max(1, ...row.map((value, index) =>
      String(value).split(/\r?\n/).reduce((sum, line) => sum + Math.max(1, Math.ceil(line.length * fontSize / columnWidths[index])), 0)))
    if (maxLines * fontSize * 1.3 + 10 > layout.maxRowHeight) {
      throw new PdfLayoutError('Jeden z wierszy zawiera zbyt dużo tekstu, aby zmieścić go na stronie PDF bez dzielenia. Wybierz mniej kolumn lub użyj XLSX.')
    }
  }
}

export function createPdfDefinition(model) {
  const layout = pdfLayout(model)
  const content = [
    { text: model.turnus.turnusName, fontSize: 16, bold: true, margin: [0, 0, 0, 5] },
    { text: exportSubtitle(model), fontSize: 10, margin: [0, 0, 0, 4] },
    { text: `Wygenerowano: ${exportTimestamp(model)} · Liczba osób: ${model.count}`, color: '#475569', margin: [0, 0, 0, 14] },
  ]
  for (const section of model.sections) {
    const columnWidths = sectionWidths(section, layout)
    validateRowHeights(section, columnWidths, layout)
    const heading = [{ text: `${section.label} (${section.rows.length})`, colSpan: section.columns.length,
      bold: true, fontSize: 10, fillColor: '#E2E8F0' }, ...section.columns.slice(1).map(() => ({ text: '' }))]
    const rows = section.rows.length ? section.rows.map(row => row.map(value => ({ text: String(value), wordBreak: 'break-all' })))
      : [[{ text: 'Brak osób.', colSpan: section.columns.length }, ...section.columns.slice(1).map(() => ({ text: '' }))]]
    content.push({
      margin: [0, 0, 0, 14],
      table: { headerRows: 2, keepWithHeaderRows: 1, dontBreakRows: true, widths: columnWidths,
        body: [heading, section.columns.map(column => ({ text: column.label, bold: true, fillColor: '#F1F5F9', wordBreak: 'break-all' })), ...rows] },
      layout: { hLineWidth: () => 0.4, vLineWidth: () => 0, hLineColor: () => '#CBD5E1',
        paddingLeft: () => cellPadding, paddingRight: () => cellPadding, paddingTop: () => 5, paddingBottom: () => 5 },
    })
  }
  return {
    pageSize: 'A4', pageOrientation: layout.orientation, pageMargins: [margin, 38, margin, 42],
    info: { title: `${model.turnus.turnusCode} — ${model.viewLabel}`, author: 'Panel Biuro' },
    defaultStyle: { font: 'Roboto', fontSize, color: '#0F172A' },
    header: { text: `${model.turnus.turnusCode} · ${model.viewLabel}`, margin: [margin, 14, margin, 0], fontSize: 8, color: '#475569' },
    footer: (page, pages) => ({ margin: [margin, 12, margin, 0], fontSize: 8, color: '#475569',
      columns: [{ text: `Wygenerowano: ${exportTimestamp(model)}` }, { text: `Strona ${page} z ${pages}`, alignment: 'right' }] }),
    content,
  }
}
