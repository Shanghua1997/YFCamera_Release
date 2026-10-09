package com.example.locationlib

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.locationlib.internal.LocationHelper

/**
 * 对外暴露给宿主 App 使用的定位入口。
 *
 * 设计边界：
 * 1. 宿主 App 负责申请定位权限和配置高德 Key；
 * 2. 本库只负责启动定位、刷新定位、停止定位、手动选点；
 * 3. 高德 SDK 依赖只声明在 locationlib 模块里。
 */
class LocationSDK(context: Context) {

    companion object {
        const val TAG = "LocationSDK"
        const val REQ_SELECT_LOCATION = 62001
    }

    private val helper = LocationHelper(context.applicationContext)

    fun setOnLocationChangedListener(listener: ((address: String?, lat: Double?, lng: Double?) -> Unit)?) {
        helper.setOnLocationListener(listener)
    }

    fun start() {
        helper.startLocation()
    }

    fun stop() {
        helper.stopLocation()
    }

    fun refresh() {
        helper.refreshLocation()
    }

    fun openPicker(activity: Activity) {
        helper.openPicker(activity)
    }

    fun openPicker(activity: Activity, fallbackLat: Double?, fallbackLng: Double?) {
        helper.openPicker(activity, fallbackLat, fallbackLng)
    }

    fun showLocationOptions(activity: Activity) {
        helper.showLocationOptions(activity)
    }

    fun showLocationOptions(activity: Activity, fallbackLat: Double?, fallbackLng: Double?) {
        helper.showLocationOptions(activity, fallbackLat, fallbackLng)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_SELECT_LOCATION) {
            helper.handlePickerResult(resultCode, data)
        }
    }

    fun release() {
        helper.release()
    }
}
