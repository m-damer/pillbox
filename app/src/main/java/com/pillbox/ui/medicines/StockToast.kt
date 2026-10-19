package com.pillbox.ui.medicines

import android.content.res.Resources
import com.pillbox.R
import com.pillbox.domain.DoseUnit
import com.pillbox.ui.Formatter
import com.pillbox.ui.quantityFor
import com.pillbox.ui.unitPlural

/** "Stock updated" + "Metformin: 68 tablets, about 34 days left." Used by Medicine detail and Today. */
fun stockToastText(res: Resources, fmt: Formatter, unit: DoseUnit, r: StockResult): Pair<String, String> {
    val amount = fmt.amount(r.total) + " " + res.getQuantityString(unitPlural(unit), quantityFor(r.total))
    val body = if (r.days != null) {
        res.getString(R.string.toast_stock_body, r.name, amount, res.getQuantityString(R.plurals.n_days, r.days.coerceAtLeast(0), fmt.number(r.days)))
    } else {
        res.getString(R.string.toast_stock_body_nodays, r.name, amount)
    }
    return res.getString(R.string.toast_stock_title) to body
}
