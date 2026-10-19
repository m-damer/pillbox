package com.pillbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.data.PillBoxRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Temporary list of "my" medicines for M2. Today (M3) and Medicines (M5) replace it. */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicinesViewModel(repo: PillBoxRepository, meName: String) : ViewModel() {
    val medicines: StateFlow<List<MedicineWithSchedule>> =
        flow { emit(repo.ensureMe(meName)) }
            .flatMapLatest { repo.observeMedicines(it.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
