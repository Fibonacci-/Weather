package com.helwigdev.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.design.snackbar
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var weatherViews: Array<View>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var prefs: SharedPreferences
    private val PREFS_FILENAME = "com.helwigdev.weather.prefs"
    private val HAS_AGREED_DISCLAIMER = "has_agreed_disclaimer"
    private val HAS_ASKED_ADS = "has_asked_ads"
    private val HAS_CONSENTED_PERSONALIZED_ADS = "has_consented_personalized_ads"
    private val MOST_RECENT_URL = "most_recent_url"

    private val API_KEY = "d5e3e279a6601eb65caa7464c1f5a356"
    private val PERM_REQUEST_COARSE_LOCATION = 5

    /*
    todo
    layout tweaks - bigger font?
    disclaimer with OK
    analytics
    ads
    ad removal purchase
    logo
    refresh button (mostrecenturl pref object)
    settings pane
    adjust thresholds?

     */

    /*
    system override methods
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = this.getSharedPreferences(PREFS_FILENAME, 0)


        val shouldShowDisclaimer = !prefs.getBoolean(HAS_AGREED_DISCLAIMER, false)
        val shouldShowAdOption = !prefs.getBoolean(HAS_ASKED_ADS, false)
        if(shouldShowDisclaimer) {
            alert("Some disclaimer") {
                positiveButton("I agree") {
                    Log.d("disclaimer", "user agreed")
                    val editor = prefs.edit()
                    editor.putBoolean(HAS_AGREED_DISCLAIMER, true)
                    editor.apply()
                }
                negativeButton("I do not agree") {
                    finish()
                }
                onCancelled {
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
        }

    }

    //link menu to activity
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when(item?.itemId){
            R.id.mi_refresh -> {
                refresh()
                true
            }
            R.id.mi_settings -> {
                toast("settings clicked").show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /*
    weather retrieval-related methods
     */

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
                    }
                }

    }

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
                    //todo disable asking again if they say no
                    Toast.makeText(applicationContext, "Location permission denied",Toast.LENGTH_LONG).show()
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

    private fun getWeatherUrl(location: String): String{
        return "https://api.openweathermap.org/data/2.5/weather?q=" +
                location +
                "&APPID=" +
                API_KEY
    }

    private fun getWeatherUrl(latitude: Double, longitude: Double): String{
        //docs: api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}
        return "https://api.openweathermap.org/data/2.5/weather?lat=" +
                latitude.toString() +
                "&lon=" +
                longitude.toString() +
                "&APPID=" +
                API_KEY

    }

    private fun updateWeather(url: String){
        //check for internet connection
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        val isConnected: Boolean = activeNetwork?.isConnected== true
        //should be run inside an asynctask
        if(isConnected) {
            doAsync {
                var result = ""
                val editor = prefs.edit()
                editor.putString(MOST_RECENT_URL, url)
                editor.apply()

                runOnUiThread {
                    changeViewToLoading()
                }

                val obj = URL(url)

                var errored = true

                with(obj.openConnection() as HttpURLConnection) {
                    // optional default is GET
                    requestMethod = "GET"


                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        errored = false
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

                    val parent = JSONObject(result)

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

                    //need wind speed, gusts, direction
                    //m/s to mph: * 2.2369
                    val windSpeedMS = parent.getJSONObject("wind").getDouble("speed")

                    var windDir = 0
                    if (parent.getJSONObject("wind").has("deg")) {
                        windDir = parent.getJSONObject("wind").getInt("deg")
                    }

                    val windSpeedMph = "%.1f".format((windSpeedMS * 2.2369)) + " MPH"

                    //convert kelvin to fahrenheit
                    val tempF = tempKelvin * 1.8 - 459.67

                    //stamp to date
                    val date = Date(timestamp * 1000)
                    val sdf = SimpleDateFormat("EEE, h:mm a", resources.configuration.locale)
                    val updatedAt = "Data from " + sdf.format(date)

                    //sunrise, sunset to string
                    val riseDate = Date(sunriseStamp * 1000)
                    val setDate = Date(sunsetStamp * 1000)
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


                    runOnUiThread {
                        val formatTempF = "%.2f".format(tempF)
                        tv_weather_main.text = getString(R.string.weather_main_format, weatherString, formatTempF)
                        tv_weather_description.text = weatherDesc
                        tv_humidity_val.text = "$humidityPercent%"
                        tv_location.text = "$town, $country"
                        tv_updated_at.text = updatedAt
                        iv_icon.setImageBitmap(image)
                        tv_cloud_cover_val.text = "$cloudPercent%"
                        tv_sunrise_val.text = sunriseAt
                        tv_sunset_val.text = sunsetAt
                        tv_windspeed_val.text = windSpeedMph
                        tv_winddir_val.text = "$windDir°"

                        when {
                            factor < 130 -> {
                                //good to go
                                tv_safetoride.text = "Safe to ride"
                                tv_safetoride.setTextColor(Color.GREEN)
                            }
                            factor < 170 -> {
                                //caution
                                tv_safetoride.text = "Caution! Cooling compromised"
                                tv_safetoride.setTextColor(Color.rgb(255, 102, 0))
                            }
                            else -> {
                                //danger
                                tv_safetoride.text = "DANGER! DO NOT RIDE"
                                tv_safetoride.setTextColor(Color.RED)
                            }
                        }

                        unblankView()
                    }
                } else {
                    //if there was an error
                    runOnUiThread { pb_current.visibility = View.GONE }
                    try {
                        val parent = JSONObject(result)
                        val error = parent.getInt("cod")
                        snackbar(et_location, "Server error: $error").show()
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
        var didRunAfter = false
        for (view in weatherViews){

            if(!didRunAfter){
                view.animate()
                        .alpha(0.0f)
                        .setStartDelay(0)
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
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        if (imm.isAcceptingText) { // verify if the soft keyboard is open
            imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        }
    }

    private fun changeViewToLoading(){
        pb_current.visibility = View.VISIBLE
        blankView()


    }

}
