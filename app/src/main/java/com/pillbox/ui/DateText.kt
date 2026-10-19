package com.pillbox.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Fri 23 Oct", with digits following the Settings numeral choice. */
fun shortDate(d: LocalDate, fmt: Formatter): String =
    fmt.digits(d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())))

/** "23 Oct" for the Finished list. */
fun dayMonth(d: LocalDate, fmt: Formatter): String =
    fmt.digits(d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())))
