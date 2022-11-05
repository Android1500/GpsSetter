package com.android1500.gpssetter

import android.app.Notification
import android.app.ProgressDialog
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.android1500.gpssetter.adapter.FavListAdapter
import com.android1500.gpssetter.databinding.FragmentMapsBinding
import com.android1500.gpssetter.utils.NotificationsChannel
import com.android1500.gpssetter.utils.PrefManager
import com.android1500.gpssetter.utils.ext.*
import com.android1500.gpssetter.viewmodel.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.util.regex.Pattern
import kotlin.properties.Delegates


@AndroidEntryPoint
class MapsFragment : Fragment(R.layout.fragment_maps), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private lateinit var mMap: GoogleMap
    private val viewModel by viewModels<MainViewModel>()
    private val binding : FragmentMapsBinding by viewBinding()
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
    private var addressList: List<Address>? = null

    private fun onMenuOptionSelected(item: MenuItem) {
        when(item.itemId){
            R.id.about -> aboutDialog()
            R.id.add_fav -> addFavouriteDialog()
            R.id.get_favourite -> openFavouriteListDialog()
            R.id.search -> searchDialog()
            R.id.settings -> navController.navigate(R.id.action_mapsFragment_to_settingsFragment)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar (
            toolbar = binding.toolbar,
            title = getString(R.string.app_name),
            menuRes = R.menu.main_menu,
            onMenuOptionSelected = this::onMenuOptionSelected
        )

        initializeMap()
        setFloatActionButton()
        isModuleEnable()
        updateChecker()

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

    }

    private fun initializeMap() {
        lifecycleScope.launchWhenResumed {
            val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment?.getMapAsync(this@MapsFragment)
        }

    }

    private fun isModuleEnable(){
        viewModel.isXposed.observe(requireActivity()){ isXposed ->
            xposedDialog?.dismiss()
            xposedDialog = null
            if (!isXposed){
                xposedDialog = MaterialAlertDialogBuilder(requireContext()).run {
                    setTitle(R.string.error_xposed_module_missing)
                    setMessage(R.string.error_xposed_module_missing_desc)
                    setCancelable(BuildConfig.DEBUG)
                    show()
                }
            }
        }

    }

    private fun setFloatActionButton() {
        if (viewModel.isStarted) {
            binding.start.visibility = View.GONE
            binding.stop.visibility = View.VISIBLE
        }

        binding.start.setOnClickListener {
            viewModel.update(true, lat, lon)
            mLatLng.let {
                mMarker?.position = it!!
            }
            mMarker?.isVisible = true
            binding.start.visibility = View.GONE
            binding.stop.visibility =View.VISIBLE
            lifecycleScope.launch {
                mLatLng?.getAddress(requireContext())?.let { address ->
                    address.collect{ value ->
                        showStartNotification(value)
                    }
                }
            }
            requireContext().showToast(getString(R.string.location_set))
        }
        binding.stop.setOnClickListener {
            mLatLng.let {
                viewModel.update(false, it!!.latitude, it.longitude)
            }
            mMarker?.isVisible = false
            binding.stop.visibility = View.GONE
            binding.start.visibility = View.VISIBLE
            cancelNotification()
            requireContext().showToast(getString(R.string.location_unset))

        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        with(mMap){
            mapType = PrefManager.mapType
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
            setOnMapClickListener(this@MapsFragment)

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
        alertDialog = MaterialAlertDialogBuilder(requireContext())
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



    private fun searchDialog() {
        alertDialog = MaterialAlertDialogBuilder(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_layout,null)
        val editText = view.findViewById<EditText>(R.id.search_edittxt)
        editText.hint = getString(R.string.search_hint)
        val progressBar = ProgressDialog(requireContext())
        progressBar.setMessage("Searching...")
        alertDialog.setTitle("Search")
        alertDialog.setView(view)
        alertDialog.setPositiveButton("Search") { _, _ ->
            if (requireContext().isNetworkConnected()){
                lifecycleScope.launch(Dispatchers.Main) {
                    val  getInput = editText.text.toString()
                    if (getInput.isNotEmpty()){
                        getSearchAddress(getInput).let {
                            it.collect { result ->
                                when(result) {
                                    is SearchProgress.Progress -> {
                                        progressBar.show()
                                    }
                                    is SearchProgress.Complete -> {
                                        val address = result.address
                                        val split = address.split(",")
                                        lat = split[0].toDouble()
                                        lon = split[1].toDouble()
                                        progressBar.dismiss()
                                        moveMapToNewLocation(true)
                                    }
                                    is SearchProgress.Fail -> {
                                        progressBar.dismiss()
                                        requireContext().showToast(result.error!!)
                                    }
                                    else -> {  }
                                }
                            }
                        }
                    }
                }
            } else {
                requireContext().showToast(getString(R.string.no_internet))
            }
        }
        alertDialog.show()

    }

    private fun addFavouriteDialog(){

        alertDialog =  MaterialAlertDialogBuilder(requireContext()).apply {
            val view = layoutInflater.inflate(R.layout.dialog_layout,null)
            val editText = view.findViewById<EditText>(R.id.search_edittxt)
            setTitle(getString(R.string.add_fav_dialog_title))
            setPositiveButton(getString(R.string.dialog_button_add)) { _, _ ->
                val s = editText.text.toString()
                if (!mMarker?.isVisible!!){
                    requireContext().showToast("Not location select")
                }else{
                    viewModel.storeFavorite(s, lat, lon)
                    viewModel.response.observe(requireActivity()){
                        if (it == (-1).toLong()) requireContext().showToast("Can't save") else requireContext().showToast("Save")
                    }
                }
            }
            setView(view)
            show()
        }

    }


    private fun openFavouriteListDialog() {
        getAllUpdatedFavList()
        alertDialog = MaterialAlertDialogBuilder(requireContext())
        alertDialog.setTitle(getString(R.string.favourites))
        val view = layoutInflater.inflate(R.layout.fav,null)
        val  rcv = view.findViewById<RecyclerView>(R.id.favorites_list)
        rcv.layoutManager = LinearLayoutManager(requireContext())
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
        alertDialog = MaterialAlertDialogBuilder(requireContext())
        alertDialog.setTitle(R.string.update_available)
        alertDialog.setMessage(update?.changelog)
        alertDialog.setPositiveButton(getString(R.string.update_button)) { _, _ ->
            MaterialAlertDialogBuilder(requireContext()).apply {
                val view = layoutInflater.inflate(R.layout.update_dialog, null)
                val progress = view.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                val cancel = view.findViewById<AppCompatButton>(R.id.update_download_cancel)
                setView(view)
                cancel.setOnClickListener {
                    viewModel.cancelDownload(requireContext())
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
                                viewModel.openPackageInstaller(requireContext(), it.fileUri)
                                viewModel.clearUpdate()
                                dialog.dismiss()
                            }

                            is MainViewModel.State.Failed -> {
                                Toast.makeText(
                                    requireContext(),
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
                    viewModel.startDownload(requireContext(), it)
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
            if (isRegexMatch(address)){
                delay(3000)
                trySend(SearchProgress.Complete(address))
            }else {
                try {
                    val geocoder = Geocoder(requireContext())
                    addressList = geocoder.getFromLocationName(address,5)
                    val list: List<Address>? = addressList
                    if (list == null) {
                        trySend(SearchProgress.Fail(null))
                    }
                    if (list?.size == 1){
                        val sb = StringBuilder()
                        sb.append(list[0].latitude)
                        sb.append(",")
                        sb.append(list[0].longitude)
                        trySend(SearchProgress.Complete(sb.toString()))
                    } else {
                        trySend(SearchProgress.Fail(getString(R.string.address_not_found)))
                    }
                } catch (io : IOException){
                    io.printStackTrace()
                    trySend(SearchProgress.Fail(getString(R.string.no_internet)))
                }

            }

        }

        awaitClose { this.cancel() }

    }


    private fun isRegexMatch(input: String?): Boolean {
        return Pattern.matches(
            "[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?",
            input!!
        )
    }


    private fun showStartNotification(address: String){
        notificationsChannel.showNotification(requireContext()){
            it.setSmallIcon(R.drawable.ic_stop)
            it.setContentTitle(getString(R.string.location_set))
            it.setContentText(address)
            it.setAutoCancel(true)
            it.setCategory(Notification.CATEGORY_EVENT)
            it.priority = NotificationCompat.PRIORITY_HIGH
        }

    }


    private fun cancelNotification(){
        notificationsChannel.cancelAllNotifications(requireContext())
    }




}

sealed class SearchProgress {
    object Progress : SearchProgress()
    data class Complete(val address: String) : SearchProgress()
    data class Fail(val error: String?) : SearchProgress()

}
