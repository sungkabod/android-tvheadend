/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package ie.macinnes.tvheadend;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import org.acra.ACRA;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.HttpSender;

import ie.macinnes.tvheadend.migrate.MigrateUtils;

public class Application extends android.app.Application {
    private static final String TAG = Application.class.getName();

    private RefWatcher mRefWatcher;

    public static RefWatcher getRefWatcher(Context context) {
        Application application = (Application) context.getApplicationContext();
        return application.mRefWatcher;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!LeakCanary.isInAnalyzerProcess(this)) {
            mRefWatcher = LeakCanary.install(this);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // Initialize ACRA crash reporting
        if (BuildConfig.ACRA_ENABLED) {
            Log.i(TAG, "Initializing ACRA");
            try {
                final ACRAConfiguration config = new ConfigurationBuilder(this)
                        .setHttpMethod(HttpSender.Method.PUT)
                        .setReportType(HttpSender.Type.JSON)
                        .setFormUri(BuildConfig.ACRA_REPORT_URI + "/" + BuildConfig.VERSION_CODE)
                        .setLogcatArguments("-t", "1000", "-v", "time", "*:D")
                        .setAdditionalSharedPreferences(Constants.PREFERENCE_TVHEADEND)
                        .setSharedPreferenceName(Constants.PREFERENCE_TVHEADEND)
                        .setSharedPreferenceMode(Context.MODE_PRIVATE)
                        .setBuildConfigClass(BuildConfig.class)
                        .build();
                ACRA.init(this, config);
            } catch (ACRAConfigurationException e) {
                Log.e(TAG, "Failed to init ACRA", e);
            }
        }

        // TODO: Find a better (+ out of UI thread) way to do this.
        MigrateUtils.doMigrate(getBaseContext());

        if (BuildConfig.DEBUG) {
            // StrictMode Setup
            Log.i(TAG, "Initializing Android StrictMode");
            StrictMode.ThreadPolicy.Builder threadPolicy = new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath();

            StrictMode.setThreadPolicy(threadPolicy.build());

            StrictMode.VmPolicy.Builder vmPolicy = new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectLeakedSqlLiteObjects()
                    .detectFileUriExposure()
//                    .detectUntaggedSockets()  // Skipped as our HTSP lib does not tag its sockets yet
//                    .detectCleartextNetwork() // Skipped as HTSP is not encrypted....
                    .penaltyLog()
                    .penaltyDeath();


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vmPolicy.detectContentUriWithoutPermission();
            }

            StrictMode.setVmPolicy(vmPolicy.build());
        }
    }
}
