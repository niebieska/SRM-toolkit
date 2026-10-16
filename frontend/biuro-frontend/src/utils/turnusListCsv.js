// CSV has no cell types. Keep identifiers unchanged in the file, quote all cells,
// and neutralize formula-like input. Use XLSX when opening typed text in Excel.
function csvCell(value) {
  let text = String(value ?? '')
  if (text && (/^[\s\u0000-\u001F\uFEFF]*[=+\-@]/u.test(text) || /^[\t\r\n]/u.test(text))) text = `'${text}`
  return `"${text.replaceAll('"', '""')}"`
}

export function createCsv(model) {
  const columns = []
  for (const section of model.sections) {
    for (const column of section.columns) {
      if (!columns.some(item => item.key === column.key)) columns.push(column)
    }
  }
  const lines = [[csvCell('Typ osoby'), ...columns.map(column => csvCell(column.label))].join(';')]
  for (const section of model.sections) {
    const indexes = new Map(section.columns.map((column, index) => [column.key, index]))
    for (const row of section.rows) {
      lines.push([csvCell(section.type === 'STAFF' ? 'Kadra' : 'Uczestnik'), ...columns.map(column => {
        const index = indexes.get(column.key)
        return csvCell(index === undefined ? '' : row[index])
      })].join(';'))
    }
  }
  return '\uFEFF' + lines.join('\r\n') + '\r\n'
}
