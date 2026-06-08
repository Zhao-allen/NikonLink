package com.nikonlink.connection.di

import com.nikonlink.connection.ConnectionManager
import com.nikonlink.connection.ble.BleScanner
import com.nikonlink.connection.ble.BleGattManager
import com.nikonlink.connection.wifi.WifiConnectionManager
import com.nikonlink.connection.wifi.PtpIpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConnectionModule {

    // BleScanner has no @Inject constructor (uses BluetoothAdapter.getDefaultAdapter() internally)
    @Provides
    @Singleton
    fun provideBleScanner(): BleScanner = BleScanner()

    // PtpIpClient has no @Inject constructor
    @Provides
    @Singleton
    fun providePtpIpClient(): PtpIpClient = PtpIpClient()

    // BleGattManager and WifiConnectionManager have @Inject constructors with @ApplicationContext
    // Hilt can create them automatically, but we provide ConnectionManager explicitly
    @Provides
    @Singleton
    fun provideConnectionManager(
        bleScanner: BleScanner,
        bleGattManager: BleGattManager,
        wifiConnectionManager: WifiConnectionManager,
        ptpIpClient: PtpIpClient
    ): ConnectionManager = ConnectionManager(
        bleScanner, bleGattManager, wifiConnectionManager, ptpIpClient
    )
}
