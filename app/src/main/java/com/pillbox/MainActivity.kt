package com.pillbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pillbox.ui.MedicinesPreviewScreen
import com.pillbox.ui.MedicinesViewModel
import com.pillbox.ui.PlaceholderScreen
import com.pillbox.ui.addmed.AddMedicineScreen
import com.pillbox.ui.addmed.AddMedicineViewModel
import com.pillbox.ui.components.PillBoxBottomBar
import com.pillbox.ui.components.Tab
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.today.TodayScreen
import com.pillbox.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as PillBoxApp).repository
        val meName = getString(R.string.person_me)
        setContent {
            PillBoxTheme {
                // Simple state-based navigation until there are more real destinations.
                var adding by rememberSaveable { mutableStateOf(false) }
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                if (adding) {
                    val vm: AddMedicineViewModel = viewModel(factory = viewModelFactory { initializer { AddMedicineViewModel(repo, meName) } })
                    AddMedicineScreen(vm, onClose = { adding = false; tab = Tab.TODAY })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).statusBarsPadding()) {
                            when (tab) {
                                Tab.TODAY -> {
                                    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(repo, meName) } })
                                    TodayScreen(vm, onAddMedicine = { adding = true })
                                }
                                Tab.MEDS -> {
                                    val vm: MedicinesViewModel = viewModel(factory = viewModelFactory { initializer { MedicinesViewModel(repo, meName) } })
                                    val medicines by vm.medicines.collectAsStateWithLifecycle()
                                    MedicinesPreviewScreen(medicines)
                                }
                                Tab.PEOPLE -> PlaceholderScreen(stringResource(R.string.nav_people))
                                Tab.SETTINGS -> PlaceholderScreen(stringResource(R.string.nav_settings))
                            }
                        }
                        PillBoxBottomBar(tab, onSelect = { tab = it }, onAdd = { adding = true })
                    }
                }
            }
        }
    }
}
