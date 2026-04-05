package com.proxichat.app.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.proxichat.app.data.bluetooth.BluetoothController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @Provides
    @Singleton
    fun provideBluetoothController(
        @ApplicationContext context: Context,
        bluetoothManager: BluetoothManager
    ): BluetoothController {
        return BluetoothController(context, bluetoothManager)
    }
}
