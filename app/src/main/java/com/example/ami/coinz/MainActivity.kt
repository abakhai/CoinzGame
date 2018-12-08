package com.example.ami.coinz


//TODO clean up all the code, make dfferent function for each step
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.PersistableBundle
import android.support.design.widget.BottomNavigationView
import android.support.design.widget.Snackbar
import android.support.v4.app.NavUtils
import android.support.v4.app.TaskStackBuilder
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Icon
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener, LocationEngineListener {
    private lateinit var mapView : MapView

    private var mAuth: FirebaseAuth? = null
    lateinit var toolba: ActionBar
    private val context : Context = this
    private var map : MapboxMap? = null

    private var tag = "MainActivity"

    private var firestore: FirebaseFirestore? = null
    private var firestoreUser: DocumentReference? = null

    private var coinsList = ArrayList<Coin>()
    private val BASE_URL = "http://homepages.inf.ed.ac.uk/stg/coinz/"
    lateinit var updatedURL : String
    private val endOfUrl: String = "/coinzmap.geojson"

    private var downloadDate = ""// Format: YYYY/MM/DD
    private val preferencesFile = "MyPrefsFile" // for storing preferences

    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation : Location
    private var locationEngine : LocationEngine? = null
    private var retro : RetrofitClient? = null

    private var locationLayerPlugin : LocationLayerPlugin? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        toolba = supportActionBar!!
        val bottomNavigation : BottomNavigationView = findViewById(R.id.navBar)

        mAuth = FirebaseAuth.getInstance()
        Mapbox.getInstance(applicationContext, getString(R.string.access_key))
        //changed it from applicationcontext to this and now back TODO remove comment
        mapView = findViewById(R.id.mapboxMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        bottomNavigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
        var menu = bottomNavigation.getMenu()
        var menuItem = menu.getItem(1)
        menuItem.setChecked(true)
        /*fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }*/
    }


    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.homenav -> {
                var intent1 = Intent(this, HomeActivity::class.java)
                startActivity(intent1)
                return@OnNavigationItemSelectedListener true
            }
            R.id.mapnav -> {
                var intent2 = Intent(this, MainActivity::class.java)
                intent2.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent2)
                return@OnNavigationItemSelectedListener true
            }
            R.id.walletnav -> {
                var intent3 = Intent(this, WalletActivity::class.java)
                startActivity(intent3)
                return@OnNavigationItemSelectedListener true
            }
            R.id.banknav -> {
                var intent4 = Intent(this, BankActivity::class.java)
                startActivity(intent4)
                return@OnNavigationItemSelectedListener true
            }
            R.id.transfernav -> {
                var intent5 = Intent(this, TransferActivity::class.java)
                startActivity(intent5)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }



//TODO clean up the app

    override fun onMapReady(mapboxMap: MapboxMap?) {
        if (mapboxMap == null) {
            Log.d(tag, "[onMapReady mapboxMap is null")
        } else {
            map = mapboxMap
            //Set user interface options
            map?.uiSettings?.isCompassEnabled = true
            //map?.uiSettings?.isZoomControlsEnabled = true
            //TODO see if its ok without the ? like in the lecture

            //Make location information available
            enableLocation()
            //AddMarkers().addMarkers(map, title, snippet, iconB, iconG, iconR, iconY)
            getJson()
            //Todo make this better
        }
    }

    fun updatedURL(baseURL: String) : String {


        var current = LocalDateTime.now()
        var formattedDate = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        var now = current.format(formattedDate)


        var dateString = now
        updatedURL = baseURL + dateString + endOfUrl


        return updatedURL
    }


    fun getJson() {
        var json = DownloadFileTask(DownloadCompleteRunner).execute(updatedURL(BASE_URL)).get()

        var FeatureCollection = FeatureCollection.fromJson(json)
        var FeatureList = FeatureCollection.features()
        var featureSize = FeatureList?.size as Int
        var jsonObject = JSONObject(json)
        var ratesObject = jsonObject.getJSONObject("rates")
        var DOLR = ratesObject.getString("DOLR")
        var SHIL = ratesObject.getString("SHIL")
        var PENY = ratesObject.getString("PENY")
        var QUID = ratesObject.getString("QUID")

        for (index in 0..featureSize - 1) {
            var feature = FeatureList[index]
            var geo = feature.geometry()
            var point = geo as Point
            var id = feature.properties()?.get("id").toString().replace("\"", "")
            var coords = point.coordinates()
            var currency = feature.properties()?.get("currency").toString().replace("\"", "")
            var value = feature.properties()?.get("marker-symbol").toString().replace("\"", "")
            coinsList?.add(Coin(id,coords,currency,value))

        }

        addMarkers(map, coinsList)


    }

    fun addMarkers(map : MapboxMap?, coins : ArrayList<Coin>) {
        map?.removeAnnotations()
        val IF = IconFactory.getInstance(applicationContext)

        var iconB = IF.fromResource(R.drawable.marker_blue)
        var iconG = IF.fromResource(R.drawable.marker_green)
        var iconR = IF.fromResource(R.drawable.marker_red)
        var iconY = IF.fromResource(R.drawable.marker_yellow)
        var icon = iconY

        for (index in 0..((coins.size)-1)) {

            var coin = coins[index]
            var currency = coin.curr
            var coords = coin.coord
            var value = coin.value

            //TOdo make this better

            //Default yellow for quid
            if (currency == "DOLR") {
                icon = iconG
                //snip = DOLR * value


            } else if (currency == "SHIL") {

                icon = iconB
                //  snip = SHIL * value

            } else if (currency == "PENY") {

                icon = iconR
                // snip = PENY * value
            } else {
                icon = iconY
                //  snip = QUID * value
            }


            map?.addMarker(
                    MarkerOptions()
                            .title(getString(R.string.maker_title, currency))
                            .snippet(getString(R.string.marker_snippet, value))
                            .icon(icon)
                            .position(LatLng(coords[1], coords[0]))

            )

        }
        }









    private fun enableLocation() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            Log.d(tag, "Permissions are granted")
            initLocationEngine()
            initLocationLayer()
        } else {
            Log.d(tag, "Permissions are not granted")
            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(this)
        }
    }

    @SuppressWarnings("MissingPermission")
    override fun onConnected() {
        Log.d(tag, "[onConnected] requesting location update")
        locationEngine?.requestLocationUpdates()
    }

    override fun onLocationChanged(location: Location?) {
        if (location == null) {
            Log.d(tag, "[onLocationChanged location is null")

        } else {
            originLocation = location
             var loc = Location("map")
            var coinlist = ArrayList<Coin>()
            for (c in coinsList) {
                loc.latitude = c.coord[1]
                loc.longitude = c.coord[0]

            if (originLocation.distanceTo(loc) <= 25) {
                coinlist.add(c)
            }

        }
            for (coin in coinlist) {
                if (coin in coinsList) {
                    coinsList.remove(coin)
                }
            }
            addMarkers(map, coinsList)
            setCameraPostition(originLocation)
        }
    }

    override fun onPermissionResult(granted: Boolean) {
        Log.d(tag, "[onPermissionResult] granted == $granted")
       if (granted) {
           enableLocation()
       } else {
           //TODO open a dialogue with the user
       }
    }

    @SuppressWarnings("MissingPermission")
    private fun initLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine?.interval = 5000 // preferably every 5 seconds
        locationEngine?.fastestInterval = 1000 // at most every second
        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine?.activate()

        val lastLocation = locationEngine?.lastLocation
        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPostition(lastLocation)
        } else {
            locationEngine?.addLocationEngineListener(this)
        }
    }

    private fun setCameraPostition(location : Location) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom
        (LatLng(location.latitude, location.longitude),13.0 ))

    }

    @SuppressWarnings("MissingPermission")
    private fun initLocationLayer() {
        if (mapView == null) {
            Log.d(tag, "mapView is null")
        } else {
            if (map == null) {
                Log.d(tag, "map is null")
            } else {
                locationLayerPlugin = LocationLayerPlugin(mapView, map!!, locationEngine)
                locationLayerPlugin?.setLocationLayerEnabled(true)
                locationLayerPlugin?.cameraMode = CameraMode.TRACKING
                locationLayerPlugin?.renderMode = RenderMode.NORMAL
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Log.d(tag, "Permission: $permissionsToExplain")
        // TODO present popup message or dialog
    }


    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()

        if (mAuth?.currentUser == null) {
            var intentlog = Intent(this, FirebaseUIActivity::class.java)
            startActivity(intentlog)
        } else {

            Log.d(tag, "Someone is logged in" )
        }
        //Restore preferences
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)

        //use "" as the default value (this might be the first time the app is run)
        downloadDate = settings.getString("lastDownloadDate", "")

        //Write a message to "logcat" (for debugging purposes)
        Log.d(tag, "[onStart] Recalled lastDownloadDate is $downloadDate" )

        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationEngine?.requestLocationUpdates()
            locationLayerPlugin?.onStart()
        }
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()

        Log.d(tag, "[onStop] Storing lastDownloadDate of $downloadDate")

        //All objects are from android.context.Context

        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)

        //We need an Editor object to make preferences changes
        val editor = settings.edit()
        editor.putString("lastDownloadDate", downloadDate)
        //Apply the edits!
        editor.apply()

        locationEngine?.removeLocationUpdates()
        locationLayerPlugin?.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationEngine?.deactivate()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if (outState != null) {
            mapView.onSaveInstanceState(outState)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }


}
