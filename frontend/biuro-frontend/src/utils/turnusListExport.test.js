import test from 'node:test'
import assert from 'node:assert/strict'
import { columnsForList } from './turnusLists.js'
import { createListExport, exportFileName } from './turnusListExportData.js'
import { createCsv } from './turnusListCsv.js'
import { createWorkbookSheets } from './turnusListXlsx.js'
import { createPdfDefinition, PdfLayoutError } from './turnusListPdf.js'
import { generateListFile, downloadTurnusList } from './turnusListExport.js'

function options(view = 'general', customColumns = []) {
  const person = { registrationCode: 'P1', registrationType: 'PARTICIPANT', firstName: 'Łucja', lastName: 'Żółć',
    pesel: '00210112345', personPhone: '001234567', minor: true, guardianPhone: '+48 111 222 333', icePhone: 'wrong-contact',
    address: { street: 'Żółta', houseNumber: '2', postalCode: '00-001', city: 'Łódź' }, health: 'not-for-export' }
  const staff = { ...person, registrationCode: 'S1', registrationType: 'STAFF', firstName: 'Jan', minor: false, icePhone: '009876543' }
  return { turnus: { turnusCode: 'T1', turnusName: 'Żagle — próba', startDate: '2027-01-17', endDate: '2027-01-24' },
    viewKey: view, viewLabel: 'Test listy', generatedAt: new Date('2026-10-15T12:00:00Z'), groups: [
      { type: 'PARTICIPANT', label: 'Uczestnicy', columns: columnsForList(view, customColumns, 'PARTICIPANT'), rows: [{ person, age: 16 }] },
      { type: 'STAFF', label: 'Kadra', columns: columnsForList(view, customColumns, 'STAFF'), rows: [{ person: staff, age: 24, roleLabel: 'Ksiądz', subroleLabel: 'Sternik' }] },
    ] }
}

test('snapshot includes only supplied visible rows and columns and does not change after filters/data mutate', () => {
  const source = options('insurance')
  source.groups[1].rows = []
  const snapshot = createListExport(source)
  assert.equal(snapshot.count, 1)
  assert.equal(snapshot.sections.length, 2)
  assert.deepEqual(snapshot.sections[0].rows[0], ['Łucja', 'Żółć', '00210112345'])
  assert.ok(!JSON.stringify(snapshot).includes('not-for-export'))
  source.groups[0].rows[0].person.firstName = 'Changed'
  source.groups[0].columns.length = 0
  assert.equal(snapshot.sections[0].rows[0][0], 'Łucja')
  assert.equal(snapshot.sections[0].columns.length, 3)
  assert.equal(exportFileName(snapshot, 'xlsx'), 'listy_T1_insurance_2026-10-15.xlsx')
  assert.equal(exportFileName({ ...snapshot, generatedAt: '2026-10-15T23:00:00Z' }, 'pdf'), 'listy_T1_insurance_2026-10-16.pdf')
})

test('staff-only selection excludes participants without applicable columns from export count', () => {
  const snapshot = createListExport(options('custom', ['qualifications']))
  assert.equal(snapshot.sections.length, 1)
  assert.equal(snapshot.sections[0].type, 'STAFF')
  assert.equal(snapshot.count, 1)
  const source = options('insurance')
  source.groups = [source.groups[1]]
  assert.equal(createListExport(source).sections[0].label, 'Kadra')
})

test('CSV has BOM consistent headers group type and escaped formula-like text and identifiers', () => {
  const source = options()
  source.groups[0].rows[0].person.firstName = '=1+1'
  source.groups[1].rows[0].person.firstName = 'Jan; "Nowy"\nDruga linia'
  const csv = createCsv(createListExport(source))
  assert.ok(csv.startsWith('\uFEFF"Typ osoby";"Imię";"Nazwisko";'))
  assert.ok(csv.includes('"Uczestnik";"\'=1+1"'))
  assert.ok(csv.includes('"00210112345"'))
  assert.ok(csv.includes('"001234567"'))
  assert.ok(csv.includes('"Jan; ""Nowy""\nDruga linia"'))
  assert.ok(csv.includes('"Ksiądz — Sternik"'))
  assert.ok(csv.indexOf('"Uczestnik"') < csv.indexOf('"Kadra"'))
})

test('XLSX has separate sheets, frozen headers, typed text identifiers and numeric age', () => {
  const source = options()
  source.groups[0].rows[0].person.firstName = '=1+1'
  const snapshot = createListExport(source), sheets = createWorkbookSheets(snapshot)
  assert.deepEqual(sheets.map(sheet => sheet.sheet), ['Uczestnicy', 'Kadra'])
  assert.equal(sheets[0].stickyRowsCount, 1)
  for (const key of ['pesel', 'personPhone']) {
    const index = snapshot.sections[0].columns.findIndex(column => column.key === key)
    assert.equal(sheets[0].data[1][index].type, String)
    assert.equal(sheets[0].data[1][index].format, '@')
    assert.ok(sheets[0].data[1][index].value.startsWith('00'))
  }
  const ageIndex = snapshot.sections[0].columns.findIndex(column => column.key === 'age')
  assert.equal(sheets[0].data[1][ageIndex].type, Number)
  assert.equal(sheets[0].data[1][0].value, '=1+1')
  assert.equal(sheets[0].data[1][0].type, String)
})

test('PDF keeps all selected columns, repeated section/column headings and non-splitting rows', () => {
  const snapshot = createListExport(options())
  const pdf = createPdfDefinition(snapshot)
  assert.equal(pdf.pageOrientation, 'landscape')
  assert.equal(pdf.defaultStyle.font, 'Roboto')
  for (const [index, section] of pdf.content.slice(3).entries()) {
    assert.equal(section.table.headerRows, 2)
    assert.equal(section.table.dontBreakRows, true)
    assert.equal(section.table.body[1].length, snapshot.sections[index].columns.length)
    assert.equal(section.table.body.length, 3)
  }
  assert.equal(pdf.footer(2, 5).columns[1].text, 'Strona 2 z 5')
  assert.equal(createPdfDefinition(createListExport(options('insurance'))).pageOrientation, 'portrait')
})

test('PDF rejects too-wide custom selections and oversized rows without omitting data', () => {
  const source = options('custom', ['firstName', 'lastName', 'roleLabel', 'pesel', 'address', 'email', 'guardianEmail', 'contactName', 'qualifications'])
  const snapshot = createListExport(source)
  assert.throws(() => createPdfDefinition(snapshot), error => error instanceof PdfLayoutError && /mniej kolumn/.test(error.message))
  assert.equal(snapshot.sections[1].columns.length, 9)
  const tall = options('custom', ['firstName', 'qualifications'])
  tall.groups[1].rows[0].qualificationLabels = ['W'.repeat(50000)]
  assert.throws(() => createPdfDefinition(createListExport(tall)), /zbyt dużo tekstu/)
})

test('real export engines produce XLSX, CSV and PDF blobs without downloading', async () => {
  const snapshot = createListExport(options('insurance'))
  const xlsx = await generateListFile(snapshot, 'xlsx')
  const zipHeader = new Uint8Array(await xlsx.arrayBuffer()).slice(0, 2)
  assert.deepEqual([...zipHeader], [0x50, 0x4b])
  const csv = await generateListFile(snapshot, 'csv')
  assert.ok((await csv.text()).includes('Łucja'))
  const pdf = await generateListFile(snapshot, 'pdf')
  assert.equal(new TextDecoder().decode((await pdf.arrayBuffer()).slice(0, 5)), '%PDF-')
})

test('empty exports are refused and an obsolete generation cannot trigger a download', async () => {
  const source = options('custom', [])
  await assert.rejects(() => generateListFile(createListExport(source), 'xlsx'), /Brak osób/)
  assert.equal(await downloadTurnusList(options('insurance'), 'csv', () => false), false)
})
