import { createListExport, exportFileName } from './turnusListExportData.js'
import { createCsv } from './turnusListCsv.js'
import { createWorkbookSheets } from './turnusListXlsx.js'
import { createPdfDefinition } from './turnusListPdf.js'
export { PdfLayoutError } from './turnusListPdf.js'

function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = fileName
  document.body.appendChild(anchor)
  try { anchor.click() }
  finally {
    anchor.remove()
    // Give the browser time to consume the object URL before releasing it.
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
}

export async function generateListFile(model, format) {
  if (!model.count) throw new Error('Brak osób lub kolumn do eksportu.')
  if (format === 'csv') return new Blob([createCsv(model)], { type: 'text/csv;charset=utf-8' })
  if (format === 'xlsx') {
    const { default: writeExcelFile } = await import('write-excel-file/browser')
    return writeExcelFile(createWorkbookSheets(model)).toBlob()
  }
  if (format === 'pdf') {
    // Validate layout before loading the PDF engine and its bundled Unicode fonts.
    const definition = createPdfDefinition(model)
    const [{ default: pdfMake }, { default: fonts }] = await Promise.all([
      import('pdfmake/build/pdfmake.js'), import('pdfmake/build/vfs_fonts.js'),
    ])
    pdfMake.addVirtualFileSystem(fonts)
    return pdfMake.createPdf(definition).getBlob()
  }
  throw new Error('Nieobsługiwany format eksportu.')
}

export async function downloadTurnusList(options, format, isCurrent = () => true) {
  const model = createListExport(options)
  const blob = await generateListFile(model, format)
  if (!isCurrent()) return false
  downloadBlob(blob, exportFileName(model, format))
  return true
}
