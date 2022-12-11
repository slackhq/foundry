/*
 * Copyright (C) 2022 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.gradle.tasks

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import slack.gradle.util.newTemporaryFolder

class ManifestParsingTest {

  @JvmField @Rule val temporaryFolder = newTemporaryFolder()

  @Test
  fun basicTest() {
    val manifestFile = temporaryFolder.newFile("AndroidManifest.xml")
    manifestFile.writeText(MANIFEST_CONTENT)
    val permissions = CheckManifestPermissionsTask.parseXmlPermissions(manifestFile)
    assertThat(permissions)
      .containsExactly(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.GET_ACCOUNTS",
        "android.permission.USE_FINGERPRINT",
        "android.permission.USE_BIOMETRIC",
        "android.permission.WAKE_LOCK",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_CONTACTS",
        "android.permission.VIBRATE",
        "android.permission.RECORD_AUDIO",
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.BLUETOOTH",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.REQUEST_INSTALL_PACKAGES"
      )
  }
}

// language=xml
private val MANIFEST_CONTENT =
  """
  <?xml version="1.0" encoding="utf-8"?>
  <manifest
      package="com.Slack"
      xmlns:android="http://schemas.android.com/apk/res/android"
      xmlns:tools="http://schemas.android.com/tools">

      <uses-permission android:name="android.permission.INTERNET"/>
      <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
      <uses-permission android:name="android.permission.GET_ACCOUNTS"/>
      <uses-permission android:name="android.permission.USE_FINGERPRINT"/>
      <uses-permission android:name="android.permission.USE_BIOMETRIC"/>
      <uses-permission android:name="android.permission.WAKE_LOCK"/>

      <!-- Used for file upload picker -->
      <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>

      <!-- Used to download files and store pictures taken in the app -->
      <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>

      <!-- Used for inviting contacts to teams -->
      <uses-permission android:name="android.permission.READ_CONTACTS"/>

      <!-- Allows us to override default notification settings to disable vibration -->
      <uses-permission android:name="android.permission.VIBRATE"/>

      <!-- Used to get audio from the microphone during a call -->
      <uses-permission android:name="android.permission.RECORD_AUDIO"/>

      <!-- Used to switch from speaker to earpiece -->
      <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>

      <!-- Used to end a Slack call when picking a real phone call -->
      <uses-permission android:name="android.permission.READ_PHONE_STATE"/>

      <!-- Used to switch to bluetooth headsets -->
      <uses-permission android:name="android.permission.BLUETOOTH"/>

      <!-- Used so JobScheduler can be started by the system after device reboot -->
      <uses-permission-sdk-23 android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>

      <!-- Used to allow installation of apk files since our target sdk is over 26 -->
      <uses-permission-sdk-23 android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>

      <!-- Used to start foreground service for Slack call -->
      <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
  </manifest>
  """
    .trimIndent()
