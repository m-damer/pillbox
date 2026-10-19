package com.pillbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.pillbox.ui.MedicinesPreviewScreen
import com.pillbox.ui.MedicinesViewModel
import com.pillbox.ui.addmed.AddMedicineScreen
import com.pillbox.ui.addmed.AddMedicineViewModel
import com.pillbox.ui.theme.PillBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as PillBoxApp).repository
        val meName = getString(R.string.person_me)
        setContent {
            PillBoxTheme {
                // Simple state-based navigation until there is more than one real destination.
                var adding by rememberSaveable { mutableStateOf(false) }
                if (adding) {
                    val vm: AddMedicineViewModel = viewModel(factory = viewModelFactory { initializer { AddMedicineViewModel(repo, meName) } })
                    AddMedicineScreen(vm, onClose = { adding = false })
                } else {
                    val vm: MedicinesViewModel = viewModel(factory = viewModelFactory { initializer { MedicinesViewModel(repo, meName) } })
                    val medicines by vm.medicines.collectAsStateWithLifecycle()
                    MedicinesPreviewScreen(medicines, onAdd = { adding = true })
                }
            }
        }
    }
}
