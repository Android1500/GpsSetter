package com.android1500.gpssetter.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.R
import com.android1500.gpssetter.adapter.FavListAdapter
import com.android1500.gpssetter.databinding.ActivityMapBinding
import com.android1500.gpssetter.ui.viewmodel.MainViewModel
import com.android1500.gpssetter.utils.JoystickService
import com.android1500.gpssetter.utils.NotificationsChannel
import com.android1500.gpssetter.utils.PrefManager
import com.android1500.gpssetter.utils.ext.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.properties.Delegates
import kotlin.random.Random
import com.android1500.gpssetter.room.Favourite // Assuming FavData is similar to Favourite for structure

@AndroidEntryPoint
class MapActivity :  MonetCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    private lateinit var mMap: GoogleMap
    private val viewModel by viewModels<MainViewModel>()
    private val update by lazy { viewModel.getAvailableUpdate() }
    private val notificationsChannel by lazy { NotificationsChannel() }
    private var favListAdapter: FavListAdapter = FavListAdapter()
    private var mMarker: Marker? = null
    private var mLatLng: LatLng? = null
    private var lat by Delegates.notNull<Double>()
    private var lon by Delegates.notNull<Double>()
    private var xposedDialog: AlertDialog? = null
    private lateinit var alertDialog: MaterialAlertDialogBuilder
    private lateinit var dialog: AlertDialog
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val PERMISSION_ID = 42

    private lateinit var exportFavoritesLauncher: ActivityResultLauncher<Intent>
    private lateinit var importFavoritesLauncher: ActivityResultLauncher<Intent>

    private var randomPositioningJob: Job? = null


    private val elevationOverlayProvider by lazy {
        ElevationOverlayProvider(this)
    }

    private val headerBackground by lazy {
        elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(
            resources.getDimension(R.dimen.bottom_sheet_elevation)
        )
    }
    override val applyBackgroundColorToWindow = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launchWhenCreated {
            monet.awaitMonetReady()
            setContentView(binding.root)
        }
        setSupportActionBar(binding.toolbar)
        initializeMap()
        isModuleEnable()
        updateChecker()
        setBottomSheet()
        setUpNavigationView()
        setupMonet()
        setupButton()
        setDrawer()

        exportFavoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val favList = viewModel.allFavList.value
                        try {
                            val jsonString = favoritesToJson(favList)
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(jsonString.toByteArray())
                                showToast(getString(R.string.export_successful))
                            } ?: throw IOException("Failed to open output stream for URI: $uri")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            showToast(getString(R.string.export_failed) + ": ${e.message}")
                        }
                    } ?: showToast(getString(R.string.export_failed_no_uri))
            }
        }

        importFavoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            val jsonString = inputStream.bufferedReader().use { it.readText() }
                            val importedFavorites = jsonToImportableFavorites(jsonString)
                            var importedCount = 0
                            if (importedFavorites.isNotEmpty()) {
                                importedFavorites.forEach { fav ->
                                    viewModel.storeFavorite(fav.name, fav.latitude, fav.longitude)
                                    importedCount++
                                }
                                showToast(getString(R.string.import_successful_count, importedCount))
                                if (dialog.isShowing && favListAdapter != null) {
                                    viewModel.doGetUserDetails() // Refresh list
                                }
                            } else {
                                showToast(getString(R.string.no_valid_favorites_in_file))
                            }
                        } ?: throw IOException("Failed to open input stream for URI: $uri")
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        showToast(getString(R.string.invalid_file_format) + ": ${e.message}")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast(getString(R.string.import_failed_generic, e.message ?: "Unknown error"))
                    }
                } ?: showToast(getString(R.string.import_failed_no_uri))
            }
        }

        if (PrefManager.isJoyStickEnable){
            startService(Intent(this, JoystickService::class.java))
        }

    }


    @SuppressLint("MissingPermission")
    private fun setupButton(){
        binding.favourite.setOnClickListener {
            addFavouriteDialog()
        }
        binding.getlocationContainer.setOnClickListener {
            getLastLocation()
        }

        if (viewModel.isStarted) {
            binding.bottomSheetContainer.startSpoofing.visibility = View.GONE
            binding.bottomSheetContainer.stopButton.visibility = View.VISIBLE
        }

        binding.bottomSheetContainer.startSpoofing.setOnClickListener {
            if (PrefManager.isRandomPositioningEnabled) {
                startRandomPositioningLogic()
            } else {
                viewModel.update(true, lat, lon)
                mLatLng.let {
                    mMarker?.position = it!!
                }
                mMarker?.isVisible = true
                binding.bottomSheetContainer.startSpoofing.visibility = View.GONE
                binding.bottomSheetContainer.stopButton.visibility = View.VISIBLE
                lifecycleScope.launch {
                    mLatLng?.getAddress(this@MapActivity)?.let { address ->
                        address.collect { value ->
                            showStartNotification(value)
                        }
                    }
                }
                showToast(getString(R.string.location_set))
            }
        }
        binding.bottomSheetContainer.stopButton.setOnClickListener {
            // Stop random positioning if it's active
            if (PrefManager.isRandomPositioningEnabled) {
                PrefManager.isRandomPositioningEnabled = false
                binding.bottomSheetContainer.randomPositionSwitch.isChecked = false
                stopRandomPositioningLogic() //This will also show a toast "Random positioning stopped."
            }

            // Proceed with normal stop spoofing logic
            mLatLng?.let {
                viewModel.update(false, it.latitude, it.longitude)
            }
            mMarker?.isVisible = false
            binding.bottomSheetContainer.stopButton.visibility = View.GONE
            binding.bottomSheetContainer.startSpoofing.visibility = View.VISIBLE
            cancelNotification()
            showToast(getString(R.string.location_unset))
        }

    }

    private fun setDrawer() {
        supportActionBar?.setDisplayShowTitleEnabled(false)
       val mDrawerToggle = object : ActionBarDrawerToggle(
            this,
            binding.container,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        ) {
            override fun onDrawerClosed(view: View) {
                super.onDrawerClosed(view)
                invalidateOptionsMenu()
            }

            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                invalidateOptionsMenu()
            }
        }
        binding.container.setDrawerListener(mDrawerToggle)

    }

    private fun setBottomSheet(){
        //val progress = binding.bottomSheetContainer.search.searchProgress

        val bottom = BottomSheetBehavior.from(binding.bottomSheetContainer.bottomSheet)
        with(binding.bottomSheetContainer){ // Accessing views from BottomSheetBinding

            // Load initial values for Random Positioning
            randomPositionSwitch.isChecked = PrefManager.isRandomPositioningEnabled
            randomTimeIntervalEdittext.setText(PrefManager.randomTimeIntervalSeconds.toString())
            randomDistanceRadiusEdittext.setText(PrefManager.randomDistanceRadiusMeters.toString())

            // Set listeners for Random Positioning
            randomPositionSwitch.setOnCheckedChangeListener { _, isChecked ->
                PrefManager.isRandomPositioningEnabled = isChecked
                if (isChecked) {
                    startRandomPositioningLogic()
                } else {
                    stopRandomPositioningLogic()
                }
            }

            randomTimeIntervalEdittext.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = randomTimeIntervalEdittext.text.toString()
                    try {
                        val interval = text.toInt()
                        if (interval > 0) {
                            PrefManager.randomTimeIntervalSeconds = interval
                        } else {
                            randomTimeIntervalEdittext.error = getString(R.string.invalid_input_positive_number)
                        }
                    } catch (e: NumberFormatException) {
                        randomTimeIntervalEdittext.error = getString(R.string.invalid_input_positive_number)
                    }
                } else {
                    randomTimeIntervalEdittext.error = null // Clear error when editing starts
                }
            }

            randomDistanceRadiusEdittext.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = randomDistanceRadiusEdittext.text.toString()
                    try {
                        val radius = text.toFloat()
                        if (radius > 0) {
                            PrefManager.randomDistanceRadiusMeters = radius
                        } else {
                            randomDistanceRadiusEdittext.error = getString(R.string.invalid_input_positive_number)
                        }
                    } catch (e: NumberFormatException) {
                        randomDistanceRadiusEdittext.error = getString(R.string.invalid_input_positive_number)
                    }
                } else {
                    randomDistanceRadiusEdittext.error = null // Clear error when editing starts
                }
            }

            search.searchBox.setOnEditorActionListener { v, actionId, _ ->

                if (actionId == EditorInfo.IME_ACTION_SEARCH) {

                    if (isNetworkConnected()) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            val  getInput = v.text.toString()
                            if (getInput.isNotEmpty()){
                                getSearchAddress(getInput).let {
                                    it.collect { result ->
                                        when(result) {
                                            is SearchProgress.Progress -> {
                                               // progress.visibility = View.VISIBLE
                                            }
                                            is SearchProgress.Complete -> {
                                                lat = result.lat
                                                lon = result.lon
                                                moveMapToNewLocation(true)
                                            }

                                            is SearchProgress.Fail -> {
                                                showToast(result.error!!)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        showToast(getString(R.string.no_internet))
                    }
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }

        }





        binding.mapContainer.map.setOnApplyWindowInsetsListener { _, insets ->

            val topInset: Int = insets.systemWindowInsetTop
            val bottomInset: Int = insets.systemWindowInsetBottom
            bottom.peekHeight = binding.bottomSheetContainer.searchLayout.measuredHeight + bottomInset

            val searchParams = binding.bottomSheetContainer.searchLayout.layoutParams as MarginLayoutParams
            searchParams.bottomMargin  = bottomInset + searchParams.bottomMargin
            binding.navView.setPadding(0,topInset,0,0)

            insets.consumeSystemWindowInsets()
        }

        bottom.state = BottomSheetBehavior.STATE_COLLAPSED

    }

    private fun setupMonet() {
        val secondaryBackground = monet.getBackgroundColorSecondary(this)
        val background = monet.getBackgroundColor(this)
        binding.bottomSheetContainer.search.searchBox.backgroundTintList = ColorStateList.valueOf(secondaryBackground!!)
        val root =  binding.bottomSheetContainer.root.background as GradientDrawable
        root.setColor(ColorUtils.setAlphaComponent(headerBackground,235))
        binding.getlocationContainer.backgroundTintList = ColorStateList.valueOf(background)
        binding.favourite.backgroundTintList = ColorStateList.valueOf(background)

    }



    private fun setUpNavigationView() {
        binding.navView.setNavigationItemSelectedListener {
            when(it.itemId){

                R.id.get_favourite -> {
                    openFavouriteListDialog()
                }
                R.id.settings -> {
                    startActivity(Intent(this,SettingsActivity::class.java))
                }
                R.id.about -> {
                    aboutDialog()
                }
                R.id.action_export_favorites -> {
                    exportFavorites()
                }
                R.id.action_import_favorites -> {
                    importFavorites()
                }
            }
            binding.container.closeDrawer(GravityCompat.START)
            true
        }

    }


    private fun initializeMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    private fun isModuleEnable(){
        viewModel.isXposed.observe(this) { isXposed ->
            xposedDialog?.dismiss()
            xposedDialog = null
            if (!isXposed) {
                xposedDialog = MaterialAlertDialogBuilder(this).run {
                    setTitle(R.string.error_xposed_module_missing)
                    setMessage(R.string.error_xposed_module_missing_desc)
                    setCancelable(BuildConfig.DEBUG)
                    show()
                }
            }

        }

    }



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        with(mMap){
            mapType = viewModel.mapType
            val zoom = 12.0f
            lat = viewModel.getLat
            lon  = viewModel.getLng
            mLatLng = LatLng(lat, lon)
            mLatLng.let {
                mMarker = addMarker(
                    MarkerOptions().position(it!!).draggable(false)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
                )
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
            }
            setPadding(0,80,0,170)
            setOnMapClickListener(this@MapActivity)
            setMaxZoomPreference(22.0f) // Increased max zoom level
            if (viewModel.isStarted){
                mMarker?.let {
                    it.isVisible = true
                    it.showInfoWindow()
                }
            }
        }
    }

    override fun onMapClick(latLng: LatLng) {
        mLatLng = latLng
        mMarker?.let { marker ->
            mLatLng.let {
                marker.position = it!!
                marker.isVisible = true
                mMap.animateCamera(CameraUpdateFactory.newLatLng(it))
                lat = it.latitude
                lon = it.longitude
            }
        }
        // If random positioning is active, update its center point
        if (PrefManager.isRandomPositioningEnabled && randomPositioningJob?.isActive == true) {
            startRandomPositioningLogic() // Re-start with new mLatLng as center
        }
    }


    private fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            mLatLng = LatLng(lat, lon)
            mLatLng.let { latLng ->
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng!!, 12.0f))
                mMarker?.apply {
                    position = latLng
                    isVisible = true
                    showInfoWindow()
                }
            }
        }

    }


    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState()
    }



    private fun aboutDialog(){
        alertDialog = MaterialAlertDialogBuilder(this)
        layoutInflater.inflate(R.layout.about,null).apply {
            val  tittle = findViewById<TextView>(R.id.design_about_title)
            val  version = findViewById<TextView>(R.id.design_about_version)
            val  info = findViewById<TextView>(R.id.design_about_info)
            tittle.text = getString(R.string.app_name)
            version.text = BuildConfig.VERSION_NAME
            info.text = getString(R.string.about_info)
        }.run {
            alertDialog.setView(this)
            alertDialog.show()
        }
    }



    private fun addFavouriteDialog(){
        alertDialog =  MaterialAlertDialogBuilder(this).apply {
            val view = layoutInflater.inflate(R.layout.dialog_layout,null)
            val editText = view.findViewById<EditText>(R.id.search_edittxt)
            setTitle(getString(R.string.add_fav_dialog_title))
            setPositiveButton(getString(R.string.dialog_button_add)) { _, _ ->
                val s = editText.text.toString()
                if (!mMarker?.isVisible!!){
                  showToast(getString(R.string.location_not_select))
                }else{
                    viewModel.storeFavorite(s, lat, lon)
                    viewModel.response.observe(this@MapActivity){
                        if (it == (-1).toLong()) showToast(getString(R.string.cant_save)) else showToast(getString(R.string.save))
                    }
                }
            }
            setView(view)
            show()
        }

    }


    private fun openFavouriteListDialog() {
        getAllUpdatedFavList()
        alertDialog = MaterialAlertDialogBuilder(this)
        alertDialog.setTitle(getString(R.string.favourites))
        val view = layoutInflater.inflate(R.layout.fav,null)
        val  rcv = view.findViewById<RecyclerView>(R.id.favorites_list)
        rcv.layoutManager = LinearLayoutManager(this)
        rcv.adapter = favListAdapter
        favListAdapter.onItemClick = {
            it.let {
                lat = it.lat!!
                lon = it.lng!!
            }
            moveMapToNewLocation(true)
            if (dialog.isShowing) dialog.dismiss()

        }
        favListAdapter.onItemDelete = {
            viewModel.deleteFavourite(it)
        }
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()

    }


    private fun getAllUpdatedFavList(){
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.doGetUserDetails()
                viewModel.allFavList.collect {
                    favListAdapter.submitList(it)
                }
            }
        }

    private fun exportFavorites() {
        // Check if there are any favorites to export
        if (viewModel.allFavList.value.isEmpty()) {
            showToast(getString(R.string.no_favorites_to_export))
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "gps_setter_favorites.json")
        }
        exportFavoritesLauncher.launch(intent)
    }

    private fun importFavorites() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importFavoritesLauncher.launch(intent)
    }

    // Data class for temporarily holding imported favorite data
    internal data class ImportableFavData(val name: String, val latitude: Double, val longitude: Double)

    companion object {
        // Make internal for testing, accessible via MapActivity.generateRandomLocation
        internal fun generateRandomLocation(center: LatLng, radiusMeters: Float): LatLng {
            val random = Random.Default
            val radiusInDegrees = radiusMeters / 111111.0 // Approximate conversion
            // Random distance and angle
            val u = random.nextDouble()
            val v = random.nextDouble()
            val w = radiusInDegrees * StrictMath.sqrt(u)
            val t = 2 * Math.PI * v
            // Compute offsets
            val x = w * StrictMath.cos(t)
            val y = w * StrictMath.sin(t) / StrictMath.cos(Math.toRadians(center.latitude)) // Adjust for longitude scaling

            val newLat = center.latitude + x
            val newLng = center.longitude + y
            return LatLng(newLat, newLng)
        }

        @Throws(JSONException::class)
        internal fun favoritesToJson(favorites: List<Favourite>): String {
            val jsonArray = JSONArray()
            favorites.forEach { favData ->
                val jsonObject = JSONObject()
                jsonObject.put("name", favData.name)
                // Ensure lat and lng are not null; use a sensible default or skip if necessary
                jsonObject.put("latitude", favData.lat ?: 0.0)
                jsonObject.put("longitude", favData.lng ?: 0.0)
                jsonArray.put(jsonObject)
            }
            return jsonArray.toString(2) // Indent for readability
        }

        @Throws(JSONException::class)
        internal fun jsonToImportableFavorites(jsonString: String): List<ImportableFavData> {
            val favoritesList = mutableListOf<ImportableFavData>()
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.optString("name")
                // Use optDouble with fallback to NaN to check if value exists and is valid
                val latitude = jsonObject.optDouble("latitude", Double.NaN)
                val longitude = jsonObject.optDouble("longitude", Double.NaN)

                if (name.isNotEmpty() && !latitude.isNaN() && !longitude.isNaN()) {
                    favoritesList.add(ImportableFavData(name, latitude, longitude))
                }
            }
            return favoritesList
        }
    }

    private fun startRandomPositioningLogic() {
            val u = random.nextDouble()
            val v = random.nextDouble()
            val w = radiusInDegrees * StrictMath.sqrt(u)
            val t = 2 * Math.PI * v
            // Compute offsets
            val x = w * StrictMath.cos(t)
            val y = w * StrictMath.sin(t) / StrictMath.cos(Math.toRadians(center.latitude)) // Adjust for longitude scaling

            val newLat = center.latitude + x
            val newLng = center.longitude + y
            return LatLng(newLat, newLng)
        }
    }

    private fun startRandomPositioningLogic() {
        val u = random.nextDouble()
        val v = random.nextDouble()
        val w = radiusInDegrees * StrictMath.sqrt(u)
        val t = 2 * Math.PI * v
        // Compute offsets
        val x = w * StrictMath.cos(t)
        val y = w * StrictMath.sin(t) / StrictMath.cos(Math.toRadians(center.latitude)) // Adjust for longitude scaling

        val newLat = center.latitude + x
        val newLng = center.longitude + y
        return LatLng(newLat, newLng)
    }

    private fun startRandomPositioningLogic() {
        randomPositioningJob?.cancel() // Cancel any existing job

        if (!PrefManager.isRandomPositioningEnabled) {
            stopRandomPositioningLogic() // Ensure it's fully stopped if pref is false
            return
        }

        val centerLatLng = mLatLng
        if (centerLatLng == null || mMarker?.isVisible == false) {
            showToast(getString(R.string.random_positioning_select_center_prompt))
            binding.bottomSheetContainer.randomPositionSwitch.isChecked = false // Turn off switch
            PrefManager.isRandomPositioningEnabled = false
            return
        }

        val intervalSeconds = PrefManager.randomTimeIntervalSeconds
        val radiusMeters = PrefManager.randomDistanceRadiusMeters

        if (intervalSeconds <= 0 || radiusMeters <= 0) {
            showToast("Please set a valid interval and radius for random positioning.")
            binding.bottomSheetContainer.randomPositionSwitch.isChecked = false // Turn off switch
            PrefManager.isRandomPositioningEnabled = false
            return
        }

        randomPositioningJob = lifecycleScope.launch {
            showToast(getString(R.string.random_positioning_started))
            // Ensure main spoofing is active
            if (!viewModel.isStarted) {
                val firstRandomLatLng = Companion.generateRandomLocation(centerLatLng, radiusMeters)
                viewModel.update(true, firstRandomLatLng.latitude, firstRandomLatLng.longitude)
                mMarker?.position = firstRandomLatLng
                lat = firstRandomLatLng.latitude // Update current lat/lon
                lon = firstRandomLatLng.longitude
                binding.bottomSheetContainer.startSpoofing.visibility = View.GONE
                binding.bottomSheetContainer.stopButton.visibility = View.VISIBLE
                mMarker?.isVisible = true
                lifecycleScope.launch {
                    firstRandomLatLng.getAddress(this@MapActivity)?.collect{ addressValue ->
                        showStartNotification(addressValue)
                    }
                }
            }

            while (isActive && PrefManager.isRandomPositioningEnabled) {
                delay(intervalSeconds * 1000L)
                if (!PrefManager.isRandomPositioningEnabled) break // Double check before proceeding

                val newRandomLatLng = Companion.generateRandomLocation(centerLatLng, radiusMeters)
                viewModel.update(true, newRandomLatLng.latitude, newRandomLatLng.longitude)
                mMarker?.position = newRandomLatLng
                lat = newRandomLatLng.latitude // Update current lat/lon for next potential center
                lon = newRandomLatLng.longitude
                // Optionally, show a notification or a subtle map update indication
                 mMap.animateCamera(CameraUpdateFactory.newLatLng(newRandomLatLng), 300, null)
            }
        }
        // Ensure UI reflects that spoofing is active
        binding.bottomSheetContainer.startSpoofing.visibility = View.GONE
        binding.bottomSheetContainer.stopButton.visibility = View.VISIBLE
        mMarker?.isVisible = true
    }

    private fun stopRandomPositioningLogic() {
        randomPositioningJob?.cancel()
        randomPositioningJob = null
        // Only show stopped toast if it was actually running or meant to run
        if (binding.bottomSheetContainer.randomPositionSwitch.isChecked || PrefManager.isRandomPositioningEnabled) {
             // showToast(getString(R.string.random_positioning_stopped)) // Can be noisy, consider removing or making it subtle
        }
        // If the main spoofing should also stop when random positioning is stopped, add here:
        // viewModel.update(false, lat, lon)
        // binding.bottomSheetContainer.stopButton.visibility = View.GONE
        // binding.bottomSheetContainer.startSpoofing.visibility = View.VISIBLE
        // cancelNotification()
        // showToast(getString(R.string.location_unset))
    }

    }


    private fun updateDialog(){
        alertDialog = MaterialAlertDialogBuilder(this)
        alertDialog.setTitle(R.string.update_available)
        alertDialog.setMessage(update?.changelog)
        alertDialog.setPositiveButton(getString(R.string.update_button)) { _, _ ->
            MaterialAlertDialogBuilder(this).apply {
                val view = layoutInflater.inflate(R.layout.update_dialog, null)
                val progress = view.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                val cancel = view.findViewById<AppCompatButton>(R.id.update_download_cancel)
                setView(view)
                cancel.setOnClickListener {
                    viewModel.cancelDownload(this@MapActivity)
                    dialog.dismiss()
                }
                lifecycleScope.launch {
                    viewModel.downloadState.collect {
                        when (it) {
                            is MainViewModel.State.Downloading -> {
                                if (it.progress > 0) {
                                    progress.isIndeterminate = false
                                    progress.progress = it.progress
                                }
                            }
                            is MainViewModel.State.Done -> {
                                viewModel.openPackageInstaller(this@MapActivity, it.fileUri)
                                viewModel.clearUpdate()
                                dialog.dismiss()
                            }

                            is MainViewModel.State.Failed -> {
                                Toast.makeText(
                                    this@MapActivity,
                                    R.string.bs_update_download_failed,
                                    Toast.LENGTH_LONG
                                ).show()
                                dialog.dismiss()

                            }
                            else -> {}
                        }
                    }
                }
                update?.let { it ->
                    viewModel.startDownload(this@MapActivity, it)
                } ?: run {
                    dialog.dismiss()
                }
            }.run {
                dialog = create()
                dialog.show()
            }
        }
        dialog = alertDialog.create()
        dialog.show()

    }

    private fun updateChecker(){
        lifecycleScope.launchWhenResumed {
            viewModel.update.collect{
                if (it!= null){
                    updateDialog()
                }
            }
        }
    }


    private suspend fun getSearchAddress(address: String) = callbackFlow {
        withContext(Dispatchers.IO){
            trySend(SearchProgress.Progress)
            val matcher: Matcher =
                Pattern.compile("[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?").matcher(address)

            if (matcher.matches()){
                delay(3000)
                trySend(SearchProgress.Complete(matcher.group().split(",")[0].toDouble(),matcher.group().split(",")[1].toDouble()))
            }else {
                val geocoder = Geocoder(this@MapActivity)
                val addressList: List<Address>? = geocoder.getFromLocationName(address,3)

                try {
                    addressList?.let {
                        if (it.size == 1){
                           trySend(SearchProgress.Complete(addressList[0].latitude, addressList[0].longitude))
                        }else {
                            trySend(SearchProgress.Fail(getString(R.string.address_not_found)))
                        }
                    }
                } catch (io : IOException){
                    trySend(SearchProgress.Fail(getString(R.string.no_internet)))
                }
            }
        }

        awaitClose { this.cancel() }
    }




    private fun showStartNotification(address: String){
        notificationsChannel.showNotification(this){
            it.setSmallIcon(R.drawable.ic_stop)
            it.setContentTitle(getString(R.string.location_set))
            it.setContentText(address)
            it.setAutoCancel(true)
            it.setCategory(Notification.CATEGORY_EVENT)
            it.priority = NotificationCompat.PRIORITY_HIGH
        }

    }


    private fun cancelNotification(){
        notificationsChannel.cancelAllNotifications(this)
    }


    // Get current location
    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                fusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location: Location? = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        lat = location.latitude
                        lon = location.longitude
                        moveMapToNewLocation(true)
                    }
                }
            } else {
                showToast("Turn on location")
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }
    }


    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation!!
            lat = mLastLocation.latitude
            lon = mLastLocation.longitude
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_ID
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            getLastLocation()
        }
    }






}


sealed class SearchProgress {
    object Progress : SearchProgress()
    data class Complete(val lat: Double , val lon : Double) : SearchProgress()
    data class Fail(val error: String?) : SearchProgress()
}