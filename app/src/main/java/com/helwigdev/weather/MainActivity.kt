package com.helwigdev.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var weatherViews: Array<View>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    val API_KEY = "d5e3e279a6601eb65caa7464c1f5a356"
    val PERM_REQUEST_COARSE_LOCATION = 5

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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        weatherViews = arrayOf(iv_icon, tv_weather_main, tv_weather_description,
            v_layout, tv_humidity, tv_humidity_val, tv_location, tv_updated_at,
                tv_cloud_cover, tv_cloud_cover_val, tv_sunrise, tv_sunrise_val,
                tv_sunset, tv_sunset_val, tv_windspeed, tv_windspeed_val,
                tv_winddir, tv_winddir_val, tv_safetoride)

        blankView()

        b_current_go.setOnClickListener({
            val loc = et_location.editableText.toString()
            hideSoftKeyBoard()
            changeViewToLoading()
            doAsync {
                updateWeather(getWeatherUrl(loc))
            }
        })

        //attempt to load weather for current location
        if(isLocationApproved()){
            getCurrentLocationWeather()
        }

    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationWeather(){
        Toast.makeText(applicationContext, "Fetching current location...", Toast.LENGTH_LONG).show()
        changeViewToLoading()

        fusedLocationClient.lastLocation
                .addOnSuccessListener { location : Location? ->
                    // Got last known location. In some rare situations this can be null.
                    if (location != null){
                        doAsync {
                            updateWeather(getWeatherUrl(location.latitude, location.longitude))
                        }
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
        //should be run inside an asynctask
        var result = ""

        try {
            result = URL(url).readText()
        }catch(e: FileNotFoundException){
            runOnUiThread({
                val errString = "The system did not understand that location. Try again later"
                Toast.makeText(applicationContext, errString, Toast.LENGTH_LONG).show()
                blankView()
            })
        }

        if(result == ""){
            return
        }

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
        val windDir = parent.getJSONObject("wind").getInt("deg")

        val windSpeedMph = "%.1f".format((windSpeedMS * 2.2369)) + " MPH"

        //convert kelvin to fahrenheit
        val tempF = tempKelvin * 1.8 - 459.67

        //stamp to date
        val date = Date(timestamp * 1000)
        val sdf = SimpleDateFormat("EEE MMM d, h:mm a", resources.configuration.locale)
        val updatedAt = "Data from " + sdf.format(date)

        //sunrise, sunset to string
        val riseDate = Date(sunriseStamp * 1000)
        val setDate = Date(sunsetStamp * 1000)
        val risesetSdf = SimpleDateFormat("hh:mm a", resources.configuration.locale)
        val sunriseAt = risesetSdf.format(riseDate)
        val sunsetAt = risesetSdf.format(setDate)

        //need to show relevant icon as well
        val bmUrl = URL("http://openweathermap.org/img/w/$iconId.png")
        val image = BitmapFactory.decodeStream(bmUrl.openConnection().getInputStream())

        //calculate safety
        //below 130 OK
        //130-170 caution
        //170+ danger do not ride
        val factor = tempF + humidityPercent


        runOnUiThread({
            val formatTempF = "%.2f".format(tempF)
            tv_weather_main.text = "$weatherString, $formatTempF°F"
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
        })

    }

    private fun blankView(){
        pb_current.visibility = View.GONE
        for (view in weatherViews){
            view.animate()
                    .alpha(0.0f).duration = 300
        }
    }

    private fun unblankView(){
        pb_current.visibility = View.GONE
        et_location.clearFocus()
        for (view in weatherViews){
            view.animate()
                    .alpha(1.0f).duration = 300
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

        blankView()

        pb_current.visibility = View.VISIBLE
    }

}
