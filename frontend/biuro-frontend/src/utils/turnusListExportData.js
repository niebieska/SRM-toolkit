import { listValue } from './turnusLists.js'

export function createListExport({ turnus, groups, viewKey, viewLabel, generatedAt = new Date() }) {
  const sections = groups.filter(group => group.columns.length).map(group => ({
    type: group.type,
    label: group.label,
    columns: group.columns.map(column => ({ ...column })),
    rows: group.rows.map(entry => group.columns.map(column => listValue(entry, column.key) ?? '')),
  }))
  return {
    turnus: { turnusCode: turnus.turnusCode, turnusName: turnus.turnusName, startDate: turnus.startDate, endDate: turnus.endDate },
    viewKey, viewLabel, generatedAt: generatedAt.toISOString(), sections,
    count: sections.reduce((sum, section) => sum + section.rows.length, 0),
  }
}

export function exportFileName(model, extension) {
  const code = model.turnus.turnusCode.replace(/[^a-zA-Z0-9_-]/g, '_')
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Warsaw', year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date(model.generatedAt))
  return `listy_${code}_${model.viewKey}_${day}.${extension}`
}

export function formatExportDate(value) {
  return value ? value.split('-').reverse().join('.') : ''
}

export function exportSubtitle(model) {
  return `${model.turnus.turnusCode} · ${formatExportDate(model.turnus.startDate)} – ${formatExportDate(model.turnus.endDate)} · ${model.viewLabel}`
}

export function exportTimestamp(model) {
  return new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Warsaw' })
    .format(new Date(model.generatedAt))
}
