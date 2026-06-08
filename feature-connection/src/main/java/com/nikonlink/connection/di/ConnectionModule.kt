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

    @Provides
    @Singleton
    fun provideBleScanner(): BleScanner = BleScanner()

    @Provides
    @Singleton
    fun provideBleGattManager(): BleGattManager = BleGattManager()

    @Provides
    @Singleton
    fun provideWifiConnectionManager(): WifiConnectionManager = WifiConnectionManager()

    @Provides
    @Singleton
    fun providePtpIpClient(): PtpIpClient = PtpIpClient()

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
