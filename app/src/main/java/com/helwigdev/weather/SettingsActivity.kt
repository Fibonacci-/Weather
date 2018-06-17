package com.helwigdev.weather

import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.reward.RewardItem
import com.google.android.gms.ads.reward.RewardedVideoAd
import com.google.android.gms.ads.reward.RewardedVideoAdListener
import org.jetbrains.anko.design.snackbar


class SettingsActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction().replace(android.R.id.content, MyPreferenceFragment()).commit()


    }

    class MyPreferenceFragment : PreferenceFragment(), RewardedVideoAdListener {

        private lateinit var mRewardedVideoAd: RewardedVideoAd

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preferences)
            mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity)

            val prefVid = findPreference("pref_watch_ad")
            prefVid.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Toast.makeText(activity, "Initializing ad...", Toast.LENGTH_LONG).show()

                //start ad watch
                MobileAds.initialize(activity, "ca-app-pub-3940256099942544/5224354917")

                // Use an activity context to get the rewarded video instance.

                mRewardedVideoAd.rewardedVideoAdListener = this
                loadRewardedVideoAd()


                true
            }

            val prefAdRemove = findPreference("pref_remove_ads")
            prefAdRemove.isEnabled = false//todo
            prefAdRemove.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                //start iap dialog
                Toast.makeText(activity, "Not yet implemented", Toast.LENGTH_LONG).show()
                true
            }
        }

        private fun loadRewardedVideoAd() {
            //TODO switch to real ad id
            mRewardedVideoAd.loadAd("ca-app-pub-5637328886369714/8266993759",
                    AdRequest.Builder()
                            .addTestDevice("F6C603C77A3FF9E5A5C1201A22C8CEC1")
                            .build())
        }

        override fun onPause() {
            super.onPause()
            mRewardedVideoAd.pause(activity)
        }

        override fun onResume() {
            super.onResume()

            mRewardedVideoAd.resume(activity)

        }

        override fun onDestroy() {
            super.onDestroy()
            mRewardedVideoAd.destroy(activity)
        }

        override fun onRewardedVideoAdLeftApplication() {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onRewardedVideoAdLoaded() {
            //according to docs we should load the ad in oncreate
            //but i'm wagering not many people will click this
            //so let's try not to waste bandwidth
            mRewardedVideoAd.show()
        }

        override fun onRewardedVideoAdOpened() {
        }

        override fun onRewardedVideoCompleted() {
            Toast.makeText(activity, "Thank you!", Toast.LENGTH_LONG).show()
        }

        override fun onRewarded(p0: RewardItem?) {
        }

        override fun onRewardedVideoStarted() {
        }

        override fun onRewardedVideoAdFailedToLoad(p0: Int) {
            Toast.makeText(activity, "Video failed to load - sorry!", Toast.LENGTH_LONG).show()
            Crashlytics.log(Log.ERROR, "RewardedVideo", "Rewarded video failed to load with code $p0")
        }

        override fun onRewardedVideoAdClosed() {
        }
    }

}