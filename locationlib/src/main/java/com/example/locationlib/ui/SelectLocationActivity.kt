package com.example.locationlib.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.example.locationlib.R
import com.example.locationlib.internal.TipAdapter

class SelectLocationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CURRENT_LAT = "current_lat"
        const val EXTRA_CURRENT_LNG = "current_lng"
        const val EXTRA_RESULT_LAT = "lat"
        const val EXTRA_RESULT_LNG = "lng"
        const val EXTRA_RESULT_ADDRESS = "address"
    }

    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnConfirm: Button
    private lateinit var rvTips: RecyclerView
    private lateinit var tvHint: TextView
    private lateinit var searchAdapter: TipAdapter

    private val tipsList: MutableList<Tip> = mutableListOf()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var selectedLatLng: LatLng? = null
    private var selectedAddress: String? = null
    private var currentLatLng: LatLng = LatLng(39.909187, 116.397451)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAmapPrivacy()

        val intentLat = intent.getDoubleExtra(EXTRA_CURRENT_LAT, Double.NaN)
        val intentLng = intent.getDoubleExtra(EXTRA_CURRENT_LNG, Double.NaN)
        if (!intentLat.isNaN() && !intentLng.isNaN()) {
            currentLatLng = LatLng(intentLat, intentLng)
        }
        selectedLatLng = currentLatLng
        selectedAddress = null

        setContentView(R.layout.activity_select_location)
        mapView = findViewById(R.id.mapView)
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        btnConfirm = findViewById(R.id.btnConfirm)
        rvTips = findViewById(R.id.rvTips)
        tvHint = findViewById(R.id.tvHint)

        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        aMap.uiSettings.isMyLocationButtonEnabled = false
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
        addMarker(currentLatLng, "当前位置")

        searchAdapter = TipAdapter(tipsList) { tip -> selectTip(tip) }
        rvTips.layoutManager = LinearLayoutManager(this)
        rvTips.adapter = searchAdapter

        aMap.setOnMapClickListener { latLng ->
            chooseMapPoint(latLng, "地图点选位置", false)
        }
        aMap.setOnMapLongClickListener { latLng ->
            chooseMapPoint(latLng, "地图长按位置", false)
        }
        aMap.setOnPOIClickListener { poi ->
            val latLng = poi.coordinate
            if (latLng != null) chooseMapPoint(latLng, poi.name ?: "地图POI", true)
        }

        btnSearch.setOnClickListener { doSearch(etSearch.text.toString().trim()) }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(etSearch.text.toString().trim())
                true
            } else false
        }
        etSearch.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) rvTips.bringToFront() }

        btnConfirm.setOnClickListener { confirmSelection() }

        // 进入页面后先以当前坐标附近做一轮常用地点提示，不再默认停在天安门。
        mainHandler.postDelayed({ doSearch("") }, 300)
    }

    private fun initAmapPrivacy() {
        try {
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
        } catch (_: Throwable) {}
        try {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
        } catch (_: Throwable) {}
        try {
            ServiceSettings.updatePrivacyShow(this, true, true)
            ServiceSettings.updatePrivacyAgree(this, true)
        } catch (_: Throwable) {}
    }

    private fun doSearch(rawKeyword: String) {
        val keyword = if (rawKeyword.isBlank()) "小区 写字楼 商场 超市 饭店" else rawKeyword
        tvHint.text = "正在搜索当前位置附近：$keyword"
        val query = InputtipsQuery(keyword, "")
        query.cityLimit = false
        query.location = LatLonPoint(currentLatLng.latitude, currentLatLng.longitude)
        val inputTips = Inputtips(this, query)
        inputTips.setInputtipsListener { tipList, rCode ->
            if (rCode == 1000) {
                tipsList.clear()
                val filtered = (tipList ?: emptyList()).filter { it.point != null && !it.name.isNullOrBlank() }
                tipsList.addAll(filtered.take(30))
                searchAdapter.notifyDataSetChanged()
                tvHint.text = if (tipsList.isEmpty()) {
                    "没有搜到结果。可以换关键词，或直接在地图上点选。"
                } else {
                    "搜索到 ${tipsList.size} 个地点，点击条目即可选中"
                }
            } else {
                tipsList.clear()
                searchAdapter.notifyDataSetChanged()
                tvHint.text = "搜索失败：$rCode。请检查高德 Key、网络，或直接点地图选位置。"
            }
        }
        inputTips.requestInputtipsAsyn()
    }

    private fun selectTip(tip: Tip) {
        val point = tip.point ?: return
        val latLng = LatLng(point.latitude, point.longitude)
        currentLatLng = latLng
        selectedLatLng = latLng

        // 搜索结果本身有时只有“街道/片区”级别，先展示它，再用该坐标做逆地理编码补一轮 POI/AOI 细节。
        val tipAddress = buildAddressFromTip(tip)
        selectedAddress = tipAddress
        addMarker(latLng, tip.name ?: "选中位置")
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        tvHint.text = "已选择：${tipAddress ?: tip.name ?: "当前位置"}，正在细化周边地点..."

        reverseGeocode(latLng) { address ->
            runOnUiThread {
                val selected = selectedLatLng
                if (selected == null || !sameLatLng(selected, latLng)) return@runOnUiThread
                val refined = chooseMeaningfulAddress(address, tipAddress)
                selectedAddress = refined ?: tipAddress
                tvHint.text = "已选择：${selectedAddress ?: "当前位置"}"
            }
        }
    }

    private fun chooseMapPoint(latLng: LatLng, title: String, fallbackToTitle: Boolean) {
        currentLatLng = latLng
        selectedLatLng = latLng
        selectedAddress = null
        addMarker(latLng, title)
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        tvHint.text = "已点选地图位置，正在解析周边地点..."

        reverseGeocode(latLng) { address ->
            runOnUiThread {
                val selected = selectedLatLng
                if (selected == null || !sameLatLng(selected, latLng)) return@runOnUiThread

                val readable = chooseMeaningfulAddress(address, if (fallbackToTitle) title else null)
                selectedAddress = readable
                tvHint.text = if (!readable.isNullOrBlank()) {
                    "已选择：$readable"
                } else {
                    "已点选地图位置，但暂未解析出地点名。可以直接点“使用此位置”，主界面会继续解析。"
                }
            }
        }
    }

    private fun sameLatLng(a: LatLng, b: LatLng): Boolean {
        return kotlin.math.abs(a.latitude - b.latitude) < 0.000001 &&
                kotlin.math.abs(a.longitude - b.longitude) < 0.000001
    }

    private fun buildAddressFromTip(tip: Tip): String? {
        val district = clean(tip.district)
        val name = clean(tip.name)
        val address = clean(tip.address)
        val core = bestOfNames(name, address)
        val result = if (core == null) {
            joinNonBlank(district, name, address)
        } else if (containsRegionName(core, null, district)) {
            core
        } else if (isDetailedPlaceName(core)) {
            joinNonBlank(district, core)
        } else {
            joinNonBlank(district, name, address)
        }
        return omitProvincePrefix(result)
    }

    private fun reverseGeocode(latLng: LatLng, callback: (String?) -> Unit) {
        try {
            initAmapPrivacy()
            val search = GeocodeSearch(this)
            val point = LatLonPoint(latLng.latitude, latLng.longitude)
            val query = RegeocodeQuery(point, 500f, GeocodeSearch.AMAP)
            search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (rCode == 1000) callback(buildAddressFromRegeocode(result)) else callback(null)
                }

                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) = Unit
            })
            search.getFromLocationAsyn(query)
        } catch (_: Throwable) {
            callback(null)
        }
    }

    private fun buildAddressFromRegeocode(result: RegeocodeResult?): String? {
        val addr = result?.regeocodeAddress ?: return null
        val aoiName = bestNameFromGetter(addr, "getAois", "getAoiName", "getName", "getTitle")
        val poiName = bestNameFromGetter(addr, "getPois", "getTitle", "getName", "getSnippet")
        val building = clean(readStringGetter(addr, "getBuilding"))
        val neighborhood = clean(readStringGetter(addr, "getNeighborhood"))
        val road = bestNameFromGetter(addr, "getRoads", "getName")
        val business = bestNameFromGetter(addr, "getBusinessAreas", "getName")
        val formatAddress = clean(readStringGetter(addr, "getFormatAddress"))

        val city = clean(readStringGetter(addr, "getCity"))
        val district = clean(readStringGetter(addr, "getDistrict"))
        val township = clean(readStringGetter(addr, "getTownship"))
        val coreName = bestOfNames(poiName, aoiName, building, neighborhood, business, road, formatAddress)
        if (coreName != null) {
            if (containsRegionName(coreName, city, district)) return omitProvincePrefix(coreName)
            return omitProvincePrefix(if (isDetailedPlaceName(coreName)) {
                joinNonBlank(city, district, coreName)
            } else {
                joinNonBlank(city, district, township, coreName)
            })
        }
        return omitProvincePrefix(formatAddress)
    }

    private fun bestNameFromGetter(target: Any, listGetterName: String, vararg nameGetterNames: String): String? {
        return try {
            val listObj = target.javaClass.getMethod(listGetterName).invoke(target)
            val list = listObj as? List<*> ?: return null
            val names = mutableListOf<String>()
            for (item in list) {
                if (item == null) continue
                for (getter in nameGetterNames) {
                    val value = clean(readStringGetter(item, getter))
                    if (value != null) names.add(value)
                }
            }
            bestOfNames(*names.toTypedArray())
        } catch (_: Throwable) {
            null
        }
    }

    private fun readStringGetter(target: Any, getterName: String): String? {
        return try { target.javaClass.getMethod(getterName).invoke(target) as? String } catch (_: Throwable) { null }
    }

    private fun chooseMeaningfulAddress(primary: String?, fallback: String?): String? {
        val candidates = listOfNotNull(clean(primary), clean(fallback))
            .filter { !looksLikeCoordinate(it) }
            .distinct()
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
        val v = value?.trim()?.lowercase()?.replace(" ", "")?.replace("：", ":") ?: return false
        if (v.isEmpty()) return false
        if (v.contains("经度") || v.contains("纬度") || v.contains("longitude") || v.contains("latitude")) return true
        return Regex(""".*[-+]?\d{2,3}\.\d{4,}.*[-+]?\d{1,2}\.\d{4,}.*""").matches(v)
    }

    private fun clean(value: String?): String? {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return null
        if (v == "[]" || v.equals("null", true)) return null
        return v
    }

    private fun joinNonBlank(vararg parts: String?): String? {
        val list = parts.mapNotNull { clean(it) }.distinct()
        if (list.isEmpty()) return null
        return list.joinToString(" ")
    }

    private fun omitProvincePrefix(value: String?): String? {
        var v = clean(value) ?: return null
        v = v.replace(Regex("^\\s*[\\u4e00-\\u9fa5]{2,7}省\\s*"), "")
        v = v.replace(Regex("^\\s*(内蒙古|广西壮族|西藏|宁夏回族|新疆维吾尔)自治区\\s*"), "")
        v = v.replace(Regex("^\\s*[\\u4e00-\\u9fa5]{2,7}自治区\\s*"), "")
        return v.trim()
    }

    private fun confirmSelection() {
        val latLng = selectedLatLng
        if (latLng == null) {
            Toast.makeText(this, "请先搜索或在地图上点选位置", Toast.LENGTH_SHORT).show()
            return
        }
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRA_RESULT_LAT, latLng.latitude)
            putExtra(EXTRA_RESULT_LNG, latLng.longitude)
            putExtra(EXTRA_RESULT_ADDRESS, selectedAddress ?: "手动选点，正在解析周边地点...")
        })
        finish()
    }

    private fun addMarker(latLng: LatLng, title: String) {
        aMap.clear()
        aMap.addMarker(MarkerOptions().position(latLng).title(title))
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
