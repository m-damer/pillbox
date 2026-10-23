package com.pillbox.domain

import java.time.YearMonth

/**
 * One bought box. [left] is how much it held when the list was last fixed (see [PackMath]); [expiry] is optional.
 * [id] only tells packs apart; the list order is the order they are used in.
 */
data class Pack(val id: Int, val left: Double, val expiry: YearMonth? = null)

/** A pack with what is still in it, worked out from the medicine's total stock. */
data class PackState(val pack: Pack, val remaining: Double, val inUse: Boolean) {
    val finished: Boolean get() = remaining <= 0.0
}

/**
 * Pure rules for the packs of one medicine. Stock stays the single number that goes down when a dose is taken; packs only
 * say how that stock is split. Doses are taken from the first pack in the list until it is empty, then the next one.
 * The user decides which pack is in use ([useNow]); the default order is the order they were bought.
 */
object PackMath {
    /** What is left in each pack: the stock that is gone (packs total minus stock) is taken from the front. */
    fun states(packs: List<Pack>, stock: Double): List<PackState> {
        var used = (packs.sumOf { it.left } - stock).coerceAtLeast(0.0)
        var inUseTaken = false
        return packs.map { p ->
            val taken = minOf(used, p.left)
            used -= taken
            val remaining = (p.left - taken).coerceAtLeast(0.0)
            val inUse = !inUseTaken && remaining > 0.0
            if (inUse) inUseTaken = true
            PackState(p, remaining, inUse)
        }
    }

    /** The pack the next dose comes from, or null when there are no packs or all are empty. */
    fun current(packs: List<Pack>, stock: Double): PackState? = states(packs, stock).firstOrNull { it.inUse }

    /** Packs with a date, as one list for "this one first" advice: only the ones that still have something in them. */
    fun nextAfterCurrent(packs: List<Pack>, stock: Double): PackState? =
        states(packs, stock).filter { it.remaining > 0.0 && !it.inUse }.firstOrNull()

    /** Makes every pack hold what it holds now and drops the empty ones. Order is kept. */
    fun rebase(packs: List<Pack>, stock: Double): List<Pack> =
        states(packs, stock).filter { it.remaining > 0.0 }.map { it.pack.copy(left = it.remaining) }

    /** The user says "I am using this one now": it goes to the front, the others keep their order. */
    fun useNow(packs: List<Pack>, stock: Double, packId: Int): List<Pack> {
        val rebased = rebase(packs, stock)
        val chosen = rebased.firstOrNull { it.id == packId } ?: return packs
        return listOf(chosen) + rebased.filter { it.id != packId }
    }

    /**
     * Stock was added. [stockBefore] is the stock before adding, [legacyExpiry] the date kept on the medicine before packs existed.
     * Without a date and without packs nothing is tracked, so the list stays empty (the feature is simply not used).
     */
    fun afterAdd(packs: List<Pack>, stockBefore: Double, legacyExpiry: YearMonth?, added: Double, expiry: YearMonth?): List<Pack> {
        if (packs.isEmpty() && expiry == null) return emptyList()
        val base = if (packs.isEmpty()) {
            if (stockBefore > 0.0) listOf(Pack(1, stockBefore, legacyExpiry)) else emptyList()
        } else rebase(packs, stockBefore)
        return base + Pack((base.maxOfOrNull { it.id } ?: 0) + 1, added, expiry)
    }

    /** Stock was set by hand to a higher number than the packs hold: the extra goes into the last pack. */
    fun fit(packs: List<Pack>, stock: Double): List<Pack> {
        if (packs.isEmpty()) return packs
        val extra = stock - packs.sumOf { it.left }
        if (extra <= 0.0) return packs
        return packs.dropLast(1) + packs.last().copy(left = packs.last().left + extra)
    }

    /** The date of the pack in use; null when no packs or none has a date. */
    fun currentExpiry(packs: List<Pack>, stock: Double): YearMonth? = current(packs, stock)?.pack?.expiry

    /** Changes the date of the pack in use (the edit form's date field). */
    fun setCurrentExpiry(packs: List<Pack>, stock: Double, expiry: YearMonth?): List<Pack> {
        val id = (current(packs, stock) ?: states(packs, stock).lastOrNull())?.pack?.id ?: return packs
        return packs.map { if (it.id == id) it.copy(expiry = expiry) else it }
    }

    fun setExpiry(packs: List<Pack>, packId: Int, expiry: YearMonth?): List<Pack> =
        packs.map { if (it.id == packId) it.copy(expiry = expiry) else it }

    /**
     * True when the pack in use will probably still be in use after the next pack's date: then the person may prefer the other
     * one first. [perDay] is how much is taken per day (null or 0 when unknown).
     */
    fun currentOutlastsNext(packs: List<Pack>, stock: Double, perDay: Double?, today: java.time.LocalDate): Boolean {
        val cur = current(packs, stock) ?: return false
        val nxt = nextAfterCurrent(packs, stock) ?: return false
        val nextDate = nxt.pack.expiry ?: return false
        if (perDay == null || perDay <= 0.0) return false
        val daysToFinish = Math.ceil(cur.remaining / perDay).toLong()
        val finish = today.plusDays(daysToFinish)
        return finish.isAfter(nextDate.atEndOfMonth()) && (cur.pack.expiry == null || cur.pack.expiry.isAfter(nextDate))
    }

    // ---- storage: "id,left,yyyy-MM;id,left," ----
    fun encode(packs: List<Pack>): String? =
        if (packs.isEmpty()) null else packs.joinToString(";") { "${it.id},${it.left},${it.expiry ?: ""}" }

    fun decode(text: String?): List<Pack> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(";").mapNotNull { part ->
            val f = part.split(",")
            if (f.size < 3) return@mapNotNull null
            val id = f[0].toIntOrNull() ?: return@mapNotNull null
            val left = f[1].toDoubleOrNull() ?: return@mapNotNull null
            Pack(id, left, ExpiryRules.parse(f[2].ifBlank { null }))
        }
    }
}
