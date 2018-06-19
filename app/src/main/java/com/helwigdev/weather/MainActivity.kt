package com.helwigdev.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.vending.billing.IInAppBillingService
import com.crashlytics.android.Crashlytics
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.analytics.FirebaseAnalytics
import com.helwigdev.weather.R.id.async
import com.helwigdev.weather.R.id.av_main_bottom
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.design.snackbar
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {


    private lateinit var weatherViews: Array<View>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mFirebaseAnalytics: FirebaseAnalytics
    private lateinit var billingClient: BillingClient

    private lateinit var prefs: SharedPreferences
    private val HAS_AGREED_DISCLAIMER = "has_agreed_disclaimer"
    private val HAS_ASKED_ADS = "has_asked_ads"
    private val HAS_CONSENTED_PERSONALIZED_ADS = "has_consented_personalized_ads"
    private val MOST_RECENT_URL = "most_recent_url"
    private var API_KEY = "d5e3e279a6601eb65caa7464c1f5a356"
    private val PERM_REQUEST_COARSE_LOCATION = 5
    private val ADMOB_APP_ID = "ca-app-pub-5637328886369714~9365163449"
    private val PREF_OWM_API = "owm_api_key"

    /*
    todo
    highlight high values
    add rain
     */

    /*
    system override methods
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = this.defaultSharedPreferences

        //check if we should use the user's api key or ours
        if(prefs.contains(PREF_OWM_API) && prefs.getString(PREF_OWM_API, "") != ""){
            API_KEY = prefs.getString(PREF_OWM_API, API_KEY)
        }

        //init analytics and billing
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        var bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.CONTENT, "app_open")
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, bundle)

        billingClient = BillingClient.newBuilder(this).setListener(this).build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    // The billing client is ready. You can query purchases here.
                    //disable ad removal if it's already been purchased
                    initializeAdsIfNecessary()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                billingClient.startConnection(this)
            }
        })

        //show alerts if necessary
        //else if format ensures only one is shown at a time so as not to annoy the user
        val shouldShowDisclaimer = !prefs.getBoolean(HAS_AGREED_DISCLAIMER, false)
        val shouldShowAdOption = !prefs.getBoolean(HAS_ASKED_ADS, false)
        if(shouldShowDisclaimer) {
            //todo write a better disclaimer?
            //quit unless we get an agree
            alert("Safety is your responsibility. This app only provides a guideline. " +
                    "Please talk to your vet or trainer if you're unsure of safety.") {
                positiveButton("I agree") {
                    Log.d("disclaimer", "user agreed")
                    val editor = prefs.edit()
                    //might not actually need this, but best to have it anyway
                    editor.putBoolean(HAS_AGREED_DISCLAIMER, true)
                    editor.apply()
                    bundle = Bundle()
                    bundle.putString("disclaimer", "agreed")
                    mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
                }
                negativeButton("I do not agree") {
                    bundle = Bundle()
                    bundle.putString("disclaimer", "declined")
                    mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
                    finish()
                }
                onCancelled {
                    bundle = Bundle()
                    bundle.putString("disclaimer", "cancelled")
                    mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
                    finish()
                }
            }.show()
        } else if (shouldShowAdOption){
            alert("OK to personalize ads?"){
                val editor = prefs.edit()
                yesButton {

                    editor.putBoolean(HAS_CONSENTED_PERSONALIZED_ADS, true)
                    editor.putBoolean(HAS_ASKED_ADS, true)
                    editor.apply()
                }
                noButton {
                    editor.putBoolean(HAS_CONSENTED_PERSONALIZED_ADS, false)
                    editor.putBoolean(HAS_ASKED_ADS, true)
                    editor.apply()
                }
            }.show()
        }




        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        weatherViews = arrayOf(cv_main_weather, iv_icon, tv_weather_main,
                tv_weather_description, v_layout, tv_humidity, tv_humidity_val,
                tv_uv_index, tv_uv_index_val,
                tv_cloud_cover, tv_cloud_cover_val, tv_sunrise, tv_sunrise_val,
                tv_sunset, tv_sunset_val, tv_windspeed, tv_windspeed_val,
                tv_winddir, tv_winddir_val, tv_location, tv_updated_at, tv_safetoride)

        for (view in weatherViews){
            view.visibility = View.INVISIBLE
        }

        b_current_go.setOnClickListener {
            val loc = et_location.editableText.toString()
            hideSoftKeyBoard()

            updateWeather(getWeatherUrl(loc))

        }

        //attempt to load weather for current location
        if(isLocationApproved()){
            getCurrentLocationWeather()
        } else {
            pb_current.visibility = View.GONE
        }

    }

    private fun initializeAdsIfNecessary(){
        //i don't think the billing client likes async calls?
        val purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP)
        doAsync {
            //initialize ads

            //check if ad removal has been purchased
            var shouldShowAds = true
            if(purchasesResult.purchasesList != null) {
                for (purchase in purchasesResult.purchasesList) {
                    when (purchase.sku) {
                        skuRemoveAds -> {
                            shouldShowAds = false
                            Log.d("AdRemoval","Ad removal purchased: not showing ads: purchase token " + purchase.purchaseToken)
                        }
                        else -> {
                            Crashlytics.log(Log.ERROR, "PurchaseError", "Unknown purchase SKU: " + purchase.sku)
                        }
                    }

                }
            }

            if(shouldShowAds) {
                MobileAds.initialize(applicationContext, ADMOB_APP_ID)
                if (prefs.getBoolean(HAS_CONSENTED_PERSONALIZED_ADS, false)) {

                    runOnUiThread { av_main_bottom.loadAd(AdRequest.Builder().addTestDevice("F6C603C77A3FF9E5A5C1201A22C8CEC1").build()) }
                } else {
                    //consent not given, mark as non-tracked ad
                    val extras = Bundle()
                    extras.putString("npa", "1")
                    runOnUiThread {
                        av_main_bottom.loadAd(AdRequest.Builder()
                                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                                .addTestDevice("F6C603C77A3FF9E5A5C1201A22C8CEC1")
                                .build())
                    }
                }
            } else {
                runOnUiThread {
                    av_main_bottom.visibility = View.GONE
                    Log.d("AdRemoval","Disabling adview")
                }
            }
        }
    }


    override fun onResume() {
        //make sure we disable ads when returning from settings if need be
        val  purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP)
        if(purchasesResult.purchasesList != null) {
            updatePurchases(purchasesResult.purchasesList)
        }

        super.onResume()
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        //if, somehow, a purchase is made while this activity is active
        if (responseCode == BillingClient.BillingResponse.OK && purchases != null) {
            updatePurchases(purchases)
        }
    }

    private fun updatePurchases(purchases: MutableList<Purchase>) {
        for (purchase in purchases) {
            when(purchase.sku){
                skuRemoveAds ->{
                    prefs.edit()
                            .putString("purchase_token", purchase.purchaseToken)
                            .apply()
                    initializeAdsIfNecessary()
                }
                else -> {
                    Crashlytics.log(Log.ERROR, "PurchaseError","Unknown purchase SKU")
                }
            }

        }
    }


    //link menu to activity
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        //take action when menu items are clicked
        return when(item?.itemId){
            R.id.mi_refresh -> {
                refresh()
                true
            }
            R.id.mi_loadhere -> {
                if(isLocationApproved()){
                    getCurrentLocationWeather()
                } else {
                    //lol wtf user
                    snackbar(et_location, "Location permission not approved").show()
                    pb_current.visibility = View.GONE
                }
                true
            }
            R.id.mi_settings -> {
                val intent = Intent(applicationContext, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /*
    weather retrieval-related methods
     */

    //this is only called after permissions are verified, and is separated out to avoid copy/paste
    @SuppressLint("MissingPermission")
    private fun getCurrentLocationWeather(){
        //Toast.makeText(applicationContext, "Fetching current location...", Toast.LENGTH_LONG).show()

        fusedLocationClient.lastLocation
                .addOnSuccessListener { location : Location? ->
                    // Got last known location. In some rare situations this can be null.
                    if (location != null){

                        updateWeather(getWeatherUrl(location.latitude, location.longitude))

                    } else {
                        Toast.makeText(applicationContext, "Can't get location - services may be off",Toast.LENGTH_LONG).show()
                        pb_current.visibility = View.GONE
                    }
                }

    }

    //ask for permissions or return true
    private fun isLocationApproved(): Boolean{
        return if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERM_REQUEST_COARSE_LOCATION)
            false //we'll load the weather data later if the user approves the permission
        } else {
            //permission was granted
            true
        }
    }

    //load that shizznit
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERM_REQUEST_COARSE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    getCurrentLocationWeather()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    snackbar(et_location, "Location permission not approved").show()
                }
                return
            }

        // Add other 'when' lines to check for other
        // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun refresh(){
        val mostRecentUrl = prefs.getString(MOST_RECENT_URL, null)
        if(mostRecentUrl != null){
            blankView(Runnable {
                updateWeather(mostRecentUrl)
            })

        } else {
            toast("Nothing to refresh - select a location").show()
        }
    }

    //build weather url with a location string
    private fun getWeatherUrl(location: String): String{
        return "https://api.openweathermap.org/data/2.5/weather?q=" +
                location +
                "&APPID=" +
                API_KEY
    }

    //build weather url with lat/lon coordinates
    private fun getWeatherUrl(latitude: Double, longitude: Double): String{
        //docs: api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}
        return "https://api.openweathermap.org/data/2.5/weather?lat=" +
                latitude.toString() +
                "&lon=" +
                longitude.toString() +
                "&APPID=" +
                API_KEY

    }

    //meat of the app
    private fun updateWeather(url: String){
        //check for internet connection
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        val isConnected: Boolean = activeNetwork?.isConnected== true

        //log to analytics
        var bundle = Bundle()
        bundle.putBoolean("networkstatus",isConnected)
        mFirebaseAnalytics.logEvent("action_update_weather", bundle)

        //run inside an asynctask (god i love kotlin)
        if(isConnected) {
            doAsync {
                //because i'm still clumsy with kotlin
                var result = ""

                //update url in prefs
                val editor = prefs.edit()
                editor.putString(MOST_RECENT_URL, url)
                editor.apply()

                runOnUiThread {
                    changeViewToLoading()
                }

                //get data from api
                val obj = URL(url)
                //assume the worst
                var errored = true
                with(obj.openConnection() as HttpURLConnection) {
                    // optional default is GET
                    requestMethod = "GET"

                    //load regular data
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        errored = false
                        //standard reader
                        BufferedReader(InputStreamReader(inputStream)).use {
                            val response = StringBuffer()

                            var inputLine = it.readLine()
                            while (inputLine != null) {
                                response.append(inputLine)
                                inputLine = it.readLine()
                            }
                            result = response.toString()
                        }
                    } else {
                        //things went poorly
                        BufferedReader(InputStreamReader(errorStream)).use {
                            val response = StringBuffer()

                            var inputLine = it.readLine()
                            while (inputLine != null) {
                                response.append(inputLine)
                                inputLine = it.readLine()
                            }
                            result = response.toString()
                        }
                    }


                }

                if (!errored) {

                    //grab all the data we need to init the view
                    //standard json loads
                    val parent = JSONObject(result)
                    var uvIndex = 0.0
                    var uvIndexColor = tv_uv_index.currentTextColor

                    try{
                        //get UV index
                        val lat = parent.getJSONObject("coord").getString("lat")
                        val lon = parent.getJSONObject("coord").getString("lon")
                        val uvs = "https://api.openweathermap.org/data/2.5/uvi?lat=" +
                                lat +
                                "&lon=" +
                                lon +
                                "&APPID=" +
                                API_KEY
                        val uvUrl = URL(uvs)

                        //we don't need fancy error handling for this bit
                        val response = JSONObject(uvUrl.readText())
                        uvIndex = response.getDouble("value")

                    } catch (e: Exception){
                        Crashlytics.log(Log.ERROR, "uvindex",e.message)
                    }

                    //we now have the data from the api
                    //extract everything we need
                    val weather = parent.getJSONArray("weather").getJSONObject(0)
                    val iconId = weather.getString("icon")
                    val weatherString = weather.getString("main")
                    val weatherDesc = weather.getString("description")
                    val tempKelvin = parent.getJSONObject("main").getString("temp").toFloat()
                    val humidityPercent = parent.getJSONObject("main").getInt("humidity")
                    val town = parent.getString("name")
                    val country = parent.getJSONObject("sys").getString("country")
                    val timestamp = parent.getString("dt").toLong()
                    val cloudPercent = parent.getJSONObject("clouds").getInt("all")
                    val sunriseStamp = parent.getJSONObject("sys").getString("sunrise").toLong()
                    val sunsetStamp = parent.getJSONObject("sys").getString("sunset").toLong()

                    //need wind speed, direction
                    //m/s to mph: * 2.2369
                    val windSpeedMS = parent.getJSONObject("wind").getDouble("speed")

                    var windDir = 0
                    //this is not a guaranteed variable, if wind is still it'll just be gone
                    if (parent.getJSONObject("wind").has("deg")) {
                        windDir = parent.getJSONObject("wind").getInt("deg")
                    }

                    //everyone wants values like 3.234924352345 MPH, right?
                    val windSpeedMph = "%.1f".format((windSpeedMS * 2.2369)) + " MPH"

                    //convert kelvin to fahrenheit
                    val tempF = tempKelvin * 1.8 - 459.67

                    //stamp to date
                    val date = Date(timestamp * 1000)
                    //i haven't been able to find another way of getting the current locale compatible with api 19-25
                    @Suppress("DEPRECATION")
                    val sdf = SimpleDateFormat("EEE, h:mm a", resources.configuration.locale)
                    val updatedAt = "Data from " + sdf.format(date)

                    //sunrise, sunset to string
                    val riseDate = Date(sunriseStamp * 1000)
                    val setDate = Date(sunsetStamp * 1000)
                    @Suppress("DEPRECATION")
                    val risesetSdf = SimpleDateFormat("hh:mm a", resources.configuration.locale)
                    val sunriseAt = risesetSdf.format(riseDate)
                    val sunsetAt = risesetSdf.format(setDate)

                    //need to show relevant icon as well
                    val bmUrl = URL("https://openweathermap.org/img/w/$iconId.png")
                    val image = BitmapFactory.decodeStream(bmUrl.openConnection().getInputStream())

                    //calculate safety
                    //below 130 OK
                    //130-170 caution
                    //170+ danger do not ride
                    val factor = tempF + humidityPercent


                    //calculate color of UV index value
                    uvIndexColor = when(uvIndex) {
                        in 0..2 -> R.color.darkGreen
                        in 2..5 -> R.color.darkYellow
                        in 5..7 -> R.color.darkOrange
                        in 7..10 -> R.color.darkRed
                        else -> R.color.purple
                    }


                    runOnUiThread {
                        //set the texts
                        val formatTempF = "%.2f".format(tempF)
                        tv_weather_main.text = getString(R.string.weather_main_format, weatherString, formatTempF)
                        tv_weather_description.text = weatherDesc
                        tv_humidity_val.text = getString(R.string.perc_format, humidityPercent)
                        tv_uv_index_val.text = uvIndex.toString()
                        tv_uv_index_val.setTextColor(resources.getColor(uvIndexColor))
                        tv_location.text = getString(R.string.location_format, town, country)
                        tv_updated_at.text = updatedAt
                        iv_icon.setImageBitmap(image)
                        tv_cloud_cover_val.text = getString(R.string.perc_format, cloudPercent)
                        tv_sunrise_val.text = sunriseAt
                        tv_sunset_val.text = sunsetAt
                        tv_windspeed_val.text = windSpeedMph
                        tv_winddir_val.text = getString(R.string.winddir_format, windDir)

                        //set ride safety
                        when {
                            factor < 120 -> {
                                //good to go
                                tv_safetoride.text = getString(R.string.safe)
                                tv_safetoride.setTextColor(Color.GREEN)
                            }
                            factor < 160 -> {
                                //caution
                                tv_safetoride.text = getString(R.string.caution)
                                tv_safetoride.setTextColor(Color.rgb(255, 102, 0))
                            }
                            else -> {
                                //danger
                                tv_safetoride.text = getString(R.string.danger)
                                tv_safetoride.setTextColor(Color.RED)
                            }
                        }

                        bundle = Bundle()
                        bundle.putBoolean("updatesuccess",true)
                        mFirebaseAnalytics.logEvent("action_update_weather", bundle)

                        unblankView()
                    }
                } else {
                    //if there was an error, show it
                    runOnUiThread { pb_current.visibility = View.GONE }
                    try {
                        val parent = JSONObject(result)
                        val error = parent.getInt("cod")
                        var errString = "Server error: $error"
                        //add details if necessary
                        when (error) {
                            401 -> {
                                errString = "Error $error: is your API key correct?"
                            }
                            404 -> {
                                errString = "Location not recognized"
                            }
                        }
                        snackbar(et_location, errString).show()
                    } catch (e: JSONException) {
                        //couldn't get response
                        snackbar(et_location, "No response returned from server").show()
                    }
                }

            }
        } else {
            snackbar(et_location, "No network connection detected").show()
            pb_current.visibility = View.GONE
        }
    }

    /*
    view update-related methods
     */

    private fun blankView(after: Runnable){
        //allow running a runnable after the animations complete
        var didRunAfter = false
        for (view in weatherViews){

            if(!didRunAfter){
                view.animate()
                        .alpha(0.0f)
                        .setStartDelay(0)//because the start delay persists between .animate calls
                        .withEndAction(after)
                        .duration = 300
                didRunAfter = true
            } else {
                view.animate()
                        .alpha(0.0f)
                        .setStartDelay(0)
                        .duration = 300
            }

        }
    }

    private fun blankView(){
        for (view in weatherViews){
            view.animate()
                    .alpha(0.0f).duration = 30
        }
    }

    private fun unblankView(){
        //fancy af animations
        pb_current.visibility = View.GONE
        et_location.clearFocus()
        var startDelay: Long = 0
        for (view in weatherViews){
            view.visibility = View.VISIBLE
            view.animate()
                    .alpha(1.0f)
                    .setStartDelay(startDelay)
                    .duration = 300
            startDelay += 30
        }
        hideSoftKeyBoard()
    }

    private fun hideSoftKeyBoard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            if (imm.isAcceptingText) { // verify if the soft keyboard is open
                imm.hideSoftInputFromWindow(et_location.windowToken, 0)
            }
        } catch (e: Exception){
            Crashlytics.log(Log.ERROR, "hidesoftkeyboard", e.message)
        }
    }

    private fun changeViewToLoading(){
        pb_current.visibility = View.VISIBLE
        blankView()


    }

    companion object {

        //shared static values
        val skuRemoveAds = "htt_iap_remove_ads"
        val skuList = arrayListOf(skuRemoveAds)

    }

}
