// Synthetic fixtures only. This prototype never reads registration APIs.
export function createDemoTurnusy() {
  const names = [['Anna', 'Przykładowa'], ['Jan', 'Testowy'], ['Maja', 'Pokazowa'], ['Piotr', 'Przykładowy'], ['Zofia', 'Testowa'], ['Adam', 'Pokazowy'], ['Julia', 'Przykładowa'], ['Michał', 'Testowy']]
  return [1, 2, 3].map((number) => ({
    code: `DEMO26T${number}`,
    name: `Żagle 2026 · Turnus ${['I', 'II', 'III'][number - 1]}`,
    start: `2026-08-${String(1 + (number - 1) * 10).padStart(2, '0')}`,
    end: `2026-08-${String(11 + (number - 1) * 10).padStart(2, '0')}`,
    capacity: number === 2 ? 6 : 12,
    open: number !== 3,
    location: 'Ośrodek nad jeziorem · lokalizacja przykładowa',
    people: names.map(([firstName, lastName], i) => ({
      code: `REG-${i === 7 ? 'S' : 'P'}-DEMO26T${number}-${i + 1}`,
      firstName, lastName,
      birthDate: `${i === 7 ? 2000 : 2011 + i % 3}-05-${String(i + 10).padStart(2, '0')}`,
      type: i === 7 ? 'STAFF' : 'PARTICIPANT',
      status: i === 6 ? 'WAITLIST' : 'ACCEPTED',
      role: i === 7 ? 'Wychowawca' : 'Uczestnik',
      arrived: false,
    })),
  }))
}
