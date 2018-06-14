package com.helwigdev.weather

import android.content.Context
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log

class MainActivity : AppCompatActivity() {

    lateinit var weatherViews: Array<View>
    val API_KEY = "d5e3e279a6601eb65caa7464c1f5a356"




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        weatherViews = arrayOf(iv_icon, tv_weather_main, tv_weather_description,
            v_layout, tv_humidity, tv_humidity_val, tv_location, tv_updated_at)

        blankView()

        b_current_go.setOnClickListener({
            val loc = et_location.editableText.toString()
            hideSoftKeyBoard()
            changeViewToLoading()
            doAsync {
                updateWeather(loc)
            }
        })

    }

    private fun updateWeather(location: String){
        //should be run inside an asynctask
        //build api url
        val url = "https://api.openweathermap.org/data/2.5/weather?q=" +
                location +
                "&APPID=" +
                API_KEY

        val result = URL(url).readText()
        val jsonObject = JSONObject(result)

        //we now have the data from the api
        //extract everything we need
        val weather = jsonObject.getJSONArray("weather").getJSONObject(0)
        val iconId = weather.getString("icon")
        val weatherString = weather.getString("main")
        val weatherDesc = weather.getString("description")
        val tempKelvin = jsonObject.getJSONObject("main").getString("temp").toFloat()
        val humidityPercent = jsonObject.getJSONObject("main").getString("humidity")
        val town = jsonObject.getString("name")
        val country = jsonObject.getJSONObject("sys").getString("country")
        val timestamp = jsonObject.getString("dt").toLong()
        Log.d("Timestamp",timestamp.toString())

        //convert kelvin to fahrenheit
        val tempF = tempKelvin * 1.8 - 459.67

        //stamp to date
        val date = Date(timestamp * 1000)
        val sdf = SimpleDateFormat("EEE MMM d, h:mm a", resources.configuration.locale)
        val updatedAt = "At " + sdf.format(date)

        //need to show relevant icon as well
        val bmUrl = URL("http://openweathermap.org/img/w/$iconId.png")
        val image = BitmapFactory.decodeStream(bmUrl.openConnection().getInputStream())


        runOnUiThread({
            val formatTempF = "%.2f".format(tempF)
            tv_weather_main.text = "$weatherString, $formatTempF°F"
            tv_weather_description.text = weatherDesc
            tv_humidity_val.text = "$humidityPercent%"
            tv_location.text = "$town, $country"
            tv_updated_at.text = updatedAt
            iv_icon.setImageBitmap(image)
            unblankView()
        })

    }

    private fun blankView(){
        for (view in weatherViews){
            view.animate()
                    .alpha(0.0f).duration = 300
        }
    }

    private fun unblankView(){
        pb_current.visibility = View.GONE
        for (view in weatherViews){
            view.animate()
                    .alpha(1.0f).duration = 300
        }
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
