export function createWorkbookSheets(model) {
  return model.sections.map(section => ({
    sheet: section.label,
    stickyRowsCount: 1,
    columns: section.columns.map(column => ({ width: column.key === 'address' || column.key === 'qualifications' ? 40 : 24 })),
    data: [
      section.columns.map(column => ({ value: column.label, type: String, fontWeight: 'bold', backgroundColor: '#E2E8F0', wrap: true })),
      ...section.rows.map(row => row.map((value, index) => {
        if (value === '') return null
        if (section.columns[index].key === 'age') return { value, type: Number }
        return { value: String(value), type: String, format: '@', wrap: true }
      })),
    ],
  }))
}
