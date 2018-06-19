package com.helwigdev.weather

import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.android.billingclient.api.*
import com.crashlytics.android.Crashlytics
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.reward.RewardItem
import com.google.android.gms.ads.reward.RewardedVideoAd
import com.google.android.gms.ads.reward.RewardedVideoAdListener
import com.google.firebase.analytics.FirebaseAnalytics
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.design.snackbar


class SettingsActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction().replace(android.R.id.content, MyPreferenceFragment()).commit()


    }

    class MyPreferenceFragment : PreferenceFragment(), RewardedVideoAdListener, PurchasesUpdatedListener {


        private lateinit var mRewardedVideoAd: RewardedVideoAd
        private lateinit var billingClient: BillingClient
        private lateinit var mFirebaseAnalytics: FirebaseAnalytics

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preferences)
            mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity)
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(activity)

            val prefAdRemove = findPreference("pref_remove_ads")
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


            billingClient = BillingClient.newBuilder(activity).setListener(this).build()
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                    if (billingResponseCode == BillingClient.BillingResponse.OK) {
                        // The billing client is ready. You can query purchases here.
                        //disable ad removal if it's already been purchased
                        val purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP)
                        if(purchasesResult.purchasesList != null) {
                            for (purchase in purchasesResult.purchasesList) {
                                when (purchase.sku) {
                                    MainActivity.skuRemoveAds -> {
                                        prefAdRemove.isEnabled = false
                                        prefAdRemove.summary = "Thank you!"
                                        Log.d("AdRemoval","Ad removal purchased: disabling re-purchase: purchase token " + purchase.purchaseToken)
                                    }
                                    else -> {
                                        Crashlytics.log(Log.ERROR, "PurchaseError", "Unknown purchase SKU")
                                    }
                                }

                            }
                        }

                    }
                }
                override fun onBillingServiceDisconnected() {
                    // Try to restart the connection on the next request to
                    // Google Play by calling the startConnection() method.
                    billingClient.startConnection(this)
                }
            })



            prefAdRemove.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                //start iap dialog
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Event.BEGIN_CHECKOUT,"remove_ads_start")
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Param.CHECKOUT_STEP, bundle)


                val flowParams = BillingFlowParams.newBuilder()
                        .setSku(MainActivity.skuRemoveAds)
                        .setType(BillingClient.SkuType.INAPP) // SkuType.SUB for subscription
                        .build()
                val responseCode = billingClient.launchBillingFlow(activity, flowParams)


                true
            }
        }

        private fun loadRewardedVideoAd() {
            val bundle = Bundle()
            bundle.putString("rewarded_video","load event")
            mFirebaseAnalytics.logEvent("rewarded_video_watch", bundle)
            mRewardedVideoAd.loadAd("ca-app-pub-5637328886369714/8266993759",
                    AdRequest.Builder()
                            .addTestDevice("F6C603C77A3FF9E5A5C1201A22C8CEC1")
                            .build())
        }

        override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Event.CHECKOUT_PROGRESS,"complete")
            if (responseCode == BillingClient.BillingResponse.OK && purchases != null) {


                bundle.putString(FirebaseAnalytics.Event.CHECKOUT_PROGRESS,"success")


                updatePurchases(purchases)
            } else if (responseCode == BillingClient.BillingResponse.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.
                bundle.putString(FirebaseAnalytics.Event.CHECKOUT_PROGRESS,"cancelled")
            } else {
                // Handle any other error codes.
                bundle.putString(FirebaseAnalytics.Event.CHECKOUT_PROGRESS,"general_failure")
            }
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Param.CHECKOUT_STEP, bundle)
        }
        fun updatePurchases(purchases: MutableList<Purchase>) {
            for (purchase in purchases) {
                when(purchase.sku){
                    MainActivity.skuRemoveAds ->{
                        defaultSharedPreferences.edit()
                                .putString("purchase_token", purchase.purchaseToken)
                                .apply()

                    }
                    else -> {
                        Crashlytics.log(Log.ERROR, "PurchaseError","Unknown purchase SKU")
                    }
                }

            }
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