package com.lucento.app.core

import android.app.Application
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

/** Like [containerFactory], for the rare ViewModel (cloud sync scheduling) that also needs the [Application]. */
inline fun <reified VM : androidx.lifecycle.ViewModel> applicationContainerFactory(
    crossinline create: (Application, AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(lucentoApplication(), appContainer()) }
}

fun CreationExtras.appContainer(): AppContainer = lucentoApplication().container

fun CreationExtras.lucentoApplication(): LucentoApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LucentoApplication
