package com.lucento.app.core

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lucento.app.AppContainer
import com.lucento.app.LucentoApplication

/** Builds a ViewModel factory with access to the [AppContainer]. */
inline fun <reified VM : androidx.lifecycle.ViewModel> containerFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(appContainer()) }
}

fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LucentoApplication).container
