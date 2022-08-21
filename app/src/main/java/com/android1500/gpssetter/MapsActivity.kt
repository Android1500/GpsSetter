package com.android1500.gpssetter

import android.Manifest
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android1500.gpssetter.adapter.FavListAdapter
import com.android1500.gpssetter.databinding.ActivityMapsBinding
import com.android1500.gpssetter.room.User
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
import com.gun0912.tedpermission.coroutine.TedPermission
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*


@Suppress("NAME_SHADOWING")
@AndroidEntryPoint
class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener, FavListAdapter.ClickListener{

    private val viewModel by viewModels<MainViewModel>()
    private val binding by lazy {ActivityMapsBinding.inflate(layoutInflater)}
    private lateinit var mMap: GoogleMap
    private lateinit var favListAdapter: FavListAdapter
    private var mMarker: Marker? = null
    private var mLatLng: LatLng? = null
    private var lat: Double? = null
    private var lon: Double? = null
    private var xposedDialog: AlertDialog? = null
    private lateinit var alertDialog: AlertDialog.Builder
    private lateinit var dialog: AlertDialog
    private var user: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        checkLocationPermission()
        setFloatActionButton()
        isModuleEnable()
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }



    private fun checkLocationPermission(){
        lifecycleScope.launch {
            val permissionResult = TedPermission.create()
                .setDeniedTitle("Permission denied")
                .setDeniedMessage(
                    "If you reject permission,you can not use this real location\n\nPlease turn on permissions at [Setting] > [Permission]"
                )
                .setGotoSettingButtonText("Back")
                .setPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .check()
        }
    }
    private fun isModuleEnable(){
        viewModel.isXposed.observe(this){ isXposed ->
            xposedDialog?.dismiss()
            xposedDialog = null
            if (!isXposed){
                xposedDialog = MaterialAlertDialogBuilder(this).run {
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
            viewModel.update(true,lat!!, lon!!)
            mMarker?.position = mLatLng!!
            mMarker?.isVisible = true
            binding.start.visibility = View.GONE
            binding.stop.visibility =View.VISIBLE
            Toast.makeText(this,"Location spoofing start",Toast.LENGTH_LONG).show()
        }
        binding.stop.setOnClickListener {
                viewModel.update(false, mLatLng!!.latitude, mLatLng!!.longitude)
                mMarker?.isVisible = false
                binding.stop.visibility = View.GONE
                binding.start.visibility = View.VISIBLE
                Toast.makeText(this,"Location spoofing stop",Toast.LENGTH_LONG).show()

        }
    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap
        val zoom = 12.0f
        lat = viewModel.getLat
        lon  = viewModel.getLng
        mLatLng = LatLng(lat!!, lon!!)
        mMarker = mMap.addMarker(
            MarkerOptions().position(mLatLng!!).draggable(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
        )

        if (viewModel.isStarted){
            mMarker?.isVisible = true
            mMarker?.showInfoWindow()
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLatLng!!, zoom))
        mMap.setOnMapClickListener(this)
        if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION") == 0) {
            mMap.isMyLocationEnabled = true;
        }
        mMap.uiSettings.isCompassEnabled = true


    }

    override fun onMapClick(p0: LatLng) {
            mLatLng = p0
            mMarker?.position = mLatLng!!
            mMarker?.isVisible = true
            mMap.animateCamera(CameraUpdateFactory.newLatLng(mLatLng!!))
            lat = mLatLng!!.latitude
            lon = mLatLng!!.longitude


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when(item.itemId){
            R.id.about -> aboutDialog()
            R.id.add_fav -> addFavouriteDialog()
            R.id.get_favourite -> openFavouriteListDialog()
            R.id.search -> searchDialog()

       }
        return super.onOptionsItemSelected(item)
    }

    private fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            mLatLng = LatLng(lat!!, lon!!)
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLatLng!!, 12.0f))
        mMarker?.position = mLatLng as LatLng
        mMarker?.isVisible = true
        mMarker?.showInfoWindow()

    }

    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState()
    }

    private fun aboutDialog(){

        val dialog = MaterialAlertDialogBuilder(this)
        val view = layoutInflater.inflate(R.layout.about,null)
        val  tittle = view.findViewById<TextView>(R.id.design_about_title)
        val  version = view.findViewById<TextView>(R.id.design_about_version)
        val  info = view.findViewById<TextView>(R.id.design_about_info)
        tittle.text = getString(R.string.app_name)
        version.text = BuildConfig.VERSION_NAME
        info.text = getString(R.string.about_info)
        dialog.setView(view)
        dialog.show()

    }

    private fun searchDialog(){
        val view = layoutInflater.inflate(R.layout.search_layout,null)
        val editText = view.findViewById<EditText>(R.id.search_edittxt)
        MaterialAlertDialogBuilder(this).run {
            setTitle("Search")
            setView(view)
            setPositiveButton("Search") { _, _ ->
                val string = editText.text.toString()
                if (string != "") {
                    var addresses: List<Address>? = null
                    try {
                        addresses = Geocoder(applicationContext).getFromLocationName(string, 3)
                    } catch (ignored: Exception) {
                    }
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address: Address = addresses[0]
                        mLatLng = LatLng(address.latitude, address.longitude)
                        lat = mLatLng!!.latitude
                        lon = mLatLng!!.longitude
                        moveMapToNewLocation(false)
                    }
                }

            }
            show()
        }


    }

   private fun addFavouriteDialog(){
       val view = layoutInflater.inflate(R.layout.search_layout,null)
       val editText = view.findViewById<EditText>(R.id.search_edittxt)
       MaterialAlertDialogBuilder(this).run {
           setTitle("Add favourite")
           setView(view)
           setPositiveButton("Add") { _, _ ->
               val s = editText.text.toString()
               if (!mMarker?.isVisible!!){
                   Toast.makeText(this@MapsActivity,"Not location select",Toast.LENGTH_SHORT).show()
               }else{
                   storeFavorite(-1,s, lat!!, lon!!)
               }
           }
           show()
       }

   }

    private fun openFavouriteListDialog() {
        favListAdapter = FavListAdapter(this)
        getAllUpdatedFavList()
        alertDialog = MaterialAlertDialogBuilder(this@MapsActivity)
        alertDialog.setTitle("Favourites")
        val view = layoutInflater.inflate(R.layout.fav,null)
        val  rcv = view.findViewById<RecyclerView>(R.id.favorites_list)
        rcv.layoutManager = LinearLayoutManager(this)
        rcv.adapter = favListAdapter
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()

        }


 private fun storeFavorite(
        slot: Int,
        address: String,
        lat: Double,
        lon: Double
    ): Boolean {
        var slot = slot
        var address: String? = address
      if (slot == -1) {
            var i = 0
            while (true) {
              if(getFavorite(i) == null) {
                    slot = i
                    break
                } else {
                    i++
                }
            }
        }
        if (address == null || address.isEmpty()) {
            address = getString(R.string.empty_add)
        }
        user = User(id = slot.toLong(), address = address, lat = lat, lng = lon)
        addFavourite(user!!)
        return true
    }



    private fun getFavorite(id: Int): User {
        return viewModel.getFavouriteSingle(id)
    }

    override fun onItemClick(item: User?) {
      lat = item!!.lat
      lon = item.lng
        if (dialog.isShowing) dialog.dismiss()
       moveMapToNewLocation(true)
        Toast.makeText(applicationContext,item.address,Toast.LENGTH_SHORT).show()
    }

    override fun onItemDelete(item: User?) {
        viewModel.deleteFavourite(item!!)
        Toast.makeText(applicationContext,"Delete",Toast.LENGTH_SHORT).show()


    }

    private fun getAllUpdatedFavList(){
        this.lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.doGetUserDetails()
                viewModel.allFavList.collect { users->
                    favListAdapter.addAllFav(ArrayList(users))
                    favListAdapter.notifyDataSetChanged()
                }
            }
        }

    }
    private fun addFavourite(user: User){
        viewModel.insertUserDetails(user)
        viewModel.response.observe(this){
            if (it == (-1).toLong()) Toast.makeText(this, "Can't save", Toast.LENGTH_SHORT).show()
            Toast.makeText(this, applicationContext.getString(R.string.record_saved), Toast.LENGTH_SHORT).show()
        }
    }

}










