package com.example.locationlib.internal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.example.locationlib.LocationSDK
import com.example.locationlib.ui.SelectLocationActivity

internal class LocationHelper(private val appContext: Context) {

    private var locationClient: AMapLocationClient? = null
    private var listener: ((address: String?, lat: Double?, lng: Double?) -> Unit)? = null

    var lastLatLng: LatLng? = null
        private set
    private var lastAddress: String? = null

    fun setOnLocationListener(listener: ((address: String?, lat: Double?, lng: Double?) -> Unit)?) {
        this.listener = listener
    }

    fun startLocation() {
        locateOnce()
    }

    fun refreshLocation() {
        locateOnce()
    }

    private fun locateOnce() {
        try {
            initAmapPrivacy()

            stopLocation()
            val client = AMapLocationClient(appContext)
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isNeedAddress = true
                isMockEnable = false
                httpTimeOut = 10000
            }
            client.setLocationOption(option)
            client.setLocationListener(locationCallback)
            locationClient = client
            client.startLocation()
        } catch (e: Throwable) {
            Log.e(LocationSDK.TAG, "启动定位失败", e)
            listener?.invoke("定位启动失败: ${e.message}", null, null)
        }
    }

    fun stopLocation() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
    }

    fun openPicker(activity: Activity) {
        openPicker(activity, null, null)
    }

    fun openPicker(activity: Activity, fallbackLat: Double?, fallbackLng: Double?) {
        val intent = Intent(activity, SelectLocationActivity::class.java)
        val fixedLatLng = lastLatLng ?: if (fallbackLat != null && fallbackLng != null
            && !fallbackLat.isNaN() && !fallbackLng.isNaN()
            && (kotlin.math.abs(fallbackLat) > 0.000001 || kotlin.math.abs(fallbackLng) > 0.000001)
        ) LatLng(fallbackLat, fallbackLng) else null
        fixedLatLng?.let {
            intent.putExtra(SelectLocationActivity.EXTRA_CURRENT_LAT, it.latitude)
            intent.putExtra(SelectLocationActivity.EXTRA_CURRENT_LNG, it.longitude)
        }
        activity.startActivityForResult(intent, LocationSDK.REQ_SELECT_LOCATION)
    }

    fun showLocationOptions(activity: Activity) {
        showLocationOptions(activity, null, null)
    }

    fun showLocationOptions(activity: Activity, fallbackLat: Double?, fallbackLng: Double?) {
        val items = arrayOf("刷新当前位置", "手动选择位置")
        AlertDialog.Builder(activity)
            .setTitle("位置设置")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> refreshLocation()
                    1 -> openPicker(activity, fallbackLat, fallbackLng)
                }
            }
            .show()
    }

    fun handlePickerResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) return
        val lat = data.getDoubleExtra(SelectLocationActivity.EXTRA_RESULT_LAT, Double.NaN)
        val lng = data.getDoubleExtra(SelectLocationActivity.EXTRA_RESULT_LNG, Double.NaN)
        val pickedAddress = clean(data.getStringExtra(SelectLocationActivity.EXTRA_RESULT_ADDRESS))
        if (lat.isNaN() || lng.isNaN()) return

        val latLng = LatLng(lat, lng)
        lastLatLng = latLng
        if (pickedAddress != null && !looksLikeCoordinate(pickedAddress)) {
            lastAddress = pickedAddress
            listener?.invoke(pickedAddress, lat, lng)
        } else {
            listener?.invoke("手动选点，正在解析周边地点...", lat, lng)
        }
        reverseGeocode(latLng) { address ->
            lastAddress = chooseMeaningfulAddress(address, pickedAddress) ?: "手动选点附近，周边地点未获取"
            listener?.invoke(lastAddress, lat, lng)
        }
    }

    private fun initAmapPrivacy() {
        // 定位、地图、搜索是三个 SDK 入口，都做一次隐私合规确认，避免定位有坐标但搜索/逆地理编码不工作。
        try {
            AMapLocationClient.updatePrivacyShow(appContext, true, true)
            AMapLocationClient.updatePrivacyAgree(appContext, true)
        } catch (_: Throwable) {}
        try {
            MapsInitializer.updatePrivacyShow(appContext, true, true)
            MapsInitializer.updatePrivacyAgree(appContext, true)
        } catch (_: Throwable) {}
        try {
            ServiceSettings.updatePrivacyShow(appContext, true, true)
            ServiceSettings.updatePrivacyAgree(appContext, true)
        } catch (_: Throwable) {}
    }

    private val locationCallback = AMapLocationListener { location ->
        try {
            if (location != null && location.errorCode == 0) {
                val lat = location.latitude
                val lng = location.longitude
                val latLng = LatLng(lat, lng)
                lastLatLng = latLng

                // 第一时间先用定位 SDK 已经返回的 POI/AOI/地址，让水印不要一直停在“定位中”。
                val fastAddress = buildAddressFromLocation(location)
                lastAddress = chooseMeaningfulAddress(fastAddress, null) ?: "已获取坐标，正在解析周边地点..."
                listener?.invoke(lastAddress, lat, lng)
                Log.d(LocationSDK.TAG, "定位成功，初步地址: $lastAddress")

                // 再做一次逆地理编码，尽量取小区、商家、建筑物、道路等更像“人话”的位置。
                reverseGeocode(latLng) { detailedAddress ->
                    val finalAddress = chooseMeaningfulAddress(detailedAddress, fastAddress)
                        ?: "周边地点未获取，点击水印可刷新或手动选点"
                    lastAddress = finalAddress
                    listener?.invoke(finalAddress, lat, lng)
                    Log.d(LocationSDK.TAG, "逆地理编码地址: $finalAddress")
                }
            } else {
                val msg = location?.errorInfo ?: "未知错误"
                val code = location?.errorCode ?: -1
                Log.e(LocationSDK.TAG, "定位失败: $code $msg")
                listener?.invoke("定位失败: $msg", null, null)
            }
        } finally {
            stopLocation()
        }
    }

    private fun buildAddressFromLocation(location: AMapLocation): String? {
        val province = clean(location.province)
        val city = clean(location.city)
        val district = clean(location.district)
        val street = clean(location.street)
        val streetNum = clean(location.streetNum)
        val poi = clean(location.poiName)
        val aoi = clean(location.aoiName)
        val desc = clean(location.description)
        val address = clean(location.address)

        val core = bestOfNames(poi, aoi, desc, address, joinNonBlank(street, streetNum))
        if (core != null && !looksLikeCoordinate(core)) {
            if (containsRegionName(core, city, district)) return core
            return if (isDetailedPlaceName(core)) {
                joinNonBlank(province, city, district, core)
            } else {
                joinNonBlank(province, city, district, street, streetNum, core)
            }
        }
        return joinNonBlank(province, city, district, street, streetNum)
    }

    private fun reverseGeocode(latLng: LatLng, callback: (String?) -> Unit) {
        try {
            initAmapPrivacy()
            val search = GeocodeSearch(appContext)
            val point = LatLonPoint(latLng.latitude, latLng.longitude)
            // 半径保持 500 米，但不再简单取第一条 POI/AOI，而是按“更像具体地点”的规则选。
            val query = RegeocodeQuery(point, 500f, GeocodeSearch.AMAP)
            search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (rCode == 1000) {
                        callback(buildAddressFromRegeocode(result))
                    } else {
                        callback(null)
                    }
                }

                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) = Unit
            })
            search.getFromLocationAsyn(query)
        } catch (e: Throwable) {
            Log.e(LocationSDK.TAG, "逆地理编码失败", e)
            callback(null)
        }
    }

    private fun buildAddressFromRegeocode(result: RegeocodeResult?): String? {
        val addr = result?.regeocodeAddress ?: return null

        val aoiName = bestNameFromGetter(addr, "getAois", "getAoiName", "getName", "getTitle")
        val poiName = bestNameFromGetter(addr, "getPois", "getTitle", "getName", "getSnippet")
        val business = bestNameFromGetter(addr, "getBusinessAreas", "getName")
        val road = bestNameFromGetter(addr, "getRoads", "getName")

        val province = clean(addr.province)
        val city = clean(addr.city)
        val district = clean(addr.district)
        val township = clean(addr.township)
        val building = clean(addr.building)
        val neighborhood = clean(addr.neighborhood)
        val formatAddress = clean(addr.formatAddress)

        // 高德有时第一条结果只是“街道/片区”，真正有价值的小区门、商铺、楼宇在后面的 POI/AOI 里。
        // 所以这里不再固定 AOI > POI > 道路，而是按细节程度选最像现场地点的名称。
        val coreName = bestOfNames(poiName, aoiName, building, neighborhood, business, road, formatAddress)
        if (coreName != null) {
            if (containsRegionName(coreName, city, district)) return coreName
            return if (isDetailedPlaceName(coreName)) {
                joinNonBlank(province, city, district, coreName)
            } else {
                joinNonBlank(province, city, district, township, coreName)
            }
        }
        if (formatAddress != null && !looksLikeCoordinate(formatAddress)) return formatAddress
        return joinNonBlank(province, city, district, township)
    }

    private fun bestNameFromGetter(target: Any, listGetterName: String, vararg nameGetterNames: String): String? {
        return try {
            val listObj = target.javaClass.getMethod(listGetterName).invoke(target)
            val list = listObj as? List<*> ?: return null
            val names = mutableListOf<String>()
            for (item in list) {
                if (item == null) continue
                for (getter in nameGetterNames) {
                    try {
                        val value = item.javaClass.getMethod(getter).invoke(item) as? String
                        val cleanValue = clean(value)
                        if (cleanValue != null) names.add(cleanValue)
                    } catch (_: Throwable) {}
                }
            }
            bestOfNames(*names.toTypedArray())
        } catch (_: Throwable) {
            null
        }
    }

    private fun chooseMeaningfulAddress(primary: String?, fallback: String?): String? {
        val p = clean(primary)
        val f = clean(fallback)
        val candidates = listOfNotNull(p, f).filter { !looksLikeCoordinate(it) }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { addressScore(it) }
    }

    private fun bestOfNames(vararg names: String?): String? {
        val candidates = names.mapNotNull { clean(it) }
            .filter { !looksLikeCoordinate(it) }
            .distinct()
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { addressScore(it) }
    }

    /**
     * 目标不是法律地址，而是水印里能判断现场的“具体地点”。
     * 小区门、商铺、楼宇、停车场等应优先于“某某街道/河东”这种片区级描述。
     */
    private fun containsRegionName(value: String?, city: String?, district: String?): Boolean {
        val v = clean(value)?.replace(" ", "") ?: return false
        val c = clean(city)?.replace(" ", "")
        val d = clean(district)?.replace(" ", "")
        return (!c.isNullOrEmpty() && v.contains(c)) || (!d.isNullOrEmpty() && v.contains(d))
    }

    private fun addressScore(value: String?): Int {
        val v = clean(value) ?: return -9999
        if (looksLikeCoordinate(v)) return -9999
        val compact = v.replace(" ", "")
        var score = compact.length.coerceAtMost(80)
        val detailKeywords = listOf(
            "西1门", "西2门", "东1门", "东2门", "南1门", "南2门", "北1门", "北2门",
            "门", "入口", "出口", "旁", "附近", "小区", "家园", "花园", "公寓", "大厦", "广场",
            "商场", "超市", "饭店", "餐厅", "银行", "学校", "医院", "药房", "便利店", "停车场",
            "楼", "号", "院", "市场", "公司", "店", "南区", "北区", "东区", "西区"
        )
        for (kw in detailKeywords) if (compact.contains(kw)) score += 25
        if (Regex(".*市.*区.*街道.{0,4}$").matches(compact)) score -= 80
        if (compact.endsWith("街道") || compact.endsWith("社区") || compact.endsWith("村") || compact.endsWith("镇") || compact.endsWith("乡")) score -= 40
        if (compact.length <= 6) score -= 30
        return score
    }

    private fun isDetailedPlaceName(value: String?): Boolean {
        val v = clean(value)?.replace(" ", "") ?: return false
        return addressScore(v) >= 55 || Regex(".*(门|入口|出口|小区|家园|花园|公寓|大厦|广场|商场|超市|饭店|餐厅|银行|学校|医院|药房|便利店|停车场|楼|号|院|市场|公司|店|南区|北区|东区|西区).*", RegexOption.IGNORE_CASE).matches(v)
    }

    private fun looksLikeCoordinate(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        if (v.isEmpty()) return false
        if (v.contains("经度") || v.contains("纬度") || v.contains("longitude") || v.contains("latitude")) return true
        return Regex(""".*[-+]?\d{2,3}\.\d{4,}.*[-+]?\d{1,2}\.\d{4,}.*""").matches(v)
    }

    private fun clean(value: String?): String? {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return null
        if (v == "[]" || v == "null") return null
        return v
    }

    private fun joinNonBlank(vararg parts: String?): String? {
        val list = parts.mapNotNull { clean(it) }.distinct()
        if (list.isEmpty()) return null
        return list.joinToString(" ")
    }

    private fun formatCoord(lat: Double, lng: Double): String {
        return "经度 %.6f  纬度 %.6f".format(lng, lat)
    }

    fun release() {
        stopLocation()
        listener = null
    }
}
