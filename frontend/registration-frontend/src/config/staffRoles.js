export const staffRoles = [
    {value: 'kandydat_na_animatora', label: 'Kandydat na animatora', minorOnly: true},
    {value: 'sternik_z_opiekunem', label: 'Sternik z opiekunem', minorOnly: true},

    {
        value: 'animator',
        label: 'Animator',
        adultOnly: true,
        subroles: [
            { value: 'kandydat_na_animatora', label: 'Kandydat na animatora' },
            { value: 'animator', label: 'Animator' },
            { value: 'animator_wychowawca', label: 'Animator wychowawca' },
        ],
    },
    {
        value: 'ksiadz',
        label: 'Ksiądz',
        adultOnly: true,
        subroles: [
            {value: 'prowadzacy', label: 'Prowadzący'},
            {value: 'wychowawca', label: 'Wychowawca'},
            {value: 'sternik', label: 'Sternik'},
            {value: 'pomoc', label: 'Pomoc duszpasterska'},

        ],
    },
    {value: 'sternik', label: 'Sternik', adultOnly: true},
    {value: 'kucharka', label: 'Kucharka', adultOnly: true},
    {value: 'ratownik', label: 'Ratownik', adultOnly: true},
    {value: 'kierowca', label: 'Kierowca', adultOnly: true},
]

export const certificatesByRole = {
    kandydat_na_animatora: [
        {id: 'kurs_wychowawcy_w_trakcie', label: 'Kurs wychowawcy wypoczynku (w trakcie)'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    sternik_z_opiekunem: [
        {id: 'patent_zeglarski', label: 'Patent żeglarski'},
        {id: 'patent_motorowodny', label: 'Patent motorowodny'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    animator: [
        {id: 'wychowawca_wypoczynku', label: 'Wychowawca wypoczynku'},
        {id: 'kpp', label: 'KPP – Kwalifikowana Pierwsza Pomoc'},
        {id: 'prawo_jazdy_b', label: 'Prawo jazdy kat. B'},
        {id: 'patent_zeglarski', label: 'Patent żeglarski'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    sternik: [
        {id: 'patent_zeglarski', label: 'Patent żeglarski'},
        {id: 'kurs_wychowawcy', label: 'Kurs wychowawcy wypoczynku'},
        {id: 'patent_motorowodny', label: 'Patent motorowodny'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    ksiadz_sternik: [
        {id: 'patent_zeglarski', label: 'Patent żeglarski'},
        {id: 'patent_motorowodny', label: 'Patent motorowodny'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    ksiadz_prowadzacy: [
        {id: 'kierownik_wypoczynku', label: 'Kierownik wypoczynku'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    ksiadz: [
        {id: 'kierownik_wypoczynku', label: 'Kierownik wypoczynku'},
        {id: 'prawo_jazdy_b', label: 'Prawo jazdy kat. B'},
        {id: 'kpp', label: 'KPP – Kwalifikowana Pierwsza Pomoc'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    kucharka: [
        {id: 'ksiazeczka_sanepid', label: 'Książeczka sanepidowska'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    ratownik: [
        {id: 'uprawnienia_ratownicze', label: 'Uprawnienia ratownicze'},
        {id: 'kpp', label: 'KPP – Kwalifikowana Pierwsza Pomoc'},
        {id: 'patent_motorowodny', label: 'Patent motorowodny'},
        {id: 'patent_zeglarski', label: 'Patent żeglarski'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
    kierowca: [
        {id: 'prawo_jazdy_b', label: 'Prawo jazdy kat. B'},
        {id: 'inne', label: 'Inne (jakie?)', hasDetails: true},
    ],
}