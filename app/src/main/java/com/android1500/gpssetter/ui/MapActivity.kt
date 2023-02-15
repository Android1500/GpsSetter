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
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.properties.Delegates


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
            viewModel.update(true, lat, lon)
            mLatLng.let {
                mMarker?.position = it!!
            }
            mMarker?.isVisible = true
            binding.bottomSheetContainer.startSpoofing.visibility = View.GONE
            binding.bottomSheetContainer.stopButton.visibility = View.VISIBLE
            lifecycleScope.launch {
                mLatLng?.getAddress(this@MapActivity)?.let { address ->
                    address.collect{ value ->
                        showStartNotification(value)
                    }
                }
            }
            showToast(getString(R.string.location_set))
        }
        binding.bottomSheetContainer.stopButton.setOnClickListener {
            mLatLng.let {
                viewModel.update(false, it!!.latitude, it.longitude)
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
        with(binding.bottomSheetContainer){

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