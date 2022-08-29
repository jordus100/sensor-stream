package com.example.test

import android.content.Context
import android.text.method.Touch
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.remote.MobileCapabilityType
import org.openqa.selenium.By
import org.testng.annotations.BeforeTest
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testng.annotations.AfterTest
import org.testng.annotations.Test
import java.net.URL
import java.time.Duration
import io.appium.java_client.Setting
import io.appium.java_client.android.connection.ConnectionState

const val ADB_DEVICE_UID = "ZY227MRS44"
const val APP_PACKAGE = "com.example.sensorstream"
const val ACTIVITY = "com.example.sensorstream.view.SensorsReadoutsActivity"
const val PATH_TO_APK =
    "C:\\Users\\admin\\AndroidStudioProjects\\sensor-stream\\app\\build\\outputs\\apk\\debug\\app-debug.apk"
const val APPIUM_SERVER_URL = "http://127.0.0.1:4723/wd/hub"

class AppiumTest {
    private lateinit var driver: AndroidDriver

    @BeforeTest
    fun setUp() {
        val desiredCapabilities = DesiredCapabilities()
        desiredCapabilities.setCapability("platformName", "Android")
        desiredCapabilities.setCapability("appium:automationName", "UiAutomator2")
        desiredCapabilities.setCapability("appium:deviceName", ADB_DEVICE_UID)
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, ADB_DEVICE_UID)
        desiredCapabilities.setCapability("appium:appPackage", APP_PACKAGE)
        desiredCapabilities.setCapability("appium:appActivity", ACTIVITY)
        desiredCapabilities.setCapability("appium:app", PATH_TO_APK)
        desiredCapabilities.setCapability("appium:newCommandTimeout", 60)
        desiredCapabilities.setCapability("uiautomator2ServerInstallTimeout", 60000)
        val remoteUrl = URL(APPIUM_SERVER_URL) //this is standard Appium http server config
        driver = AndroidDriver(remoteUrl, desiredCapabilities)
        driver.setSetting(Setting.WAIT_FOR_IDLE_TIMEOUT, 100)
        driver.setConnection(ConnectionState(6))
    }

    @Test
    fun connectionStatusDisplay() { // the device needs to have only wifi internet connection
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.appStringMap.get("connection_good")))
        driver.setConnection(ConnectionState(0))
        wait = WebDriverWait(driver, Duration.ofSeconds(2))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.appStringMap.get("connection_bad")))
    }

    @AfterTest
    fun tearDown() {
        driver.setConnection(ConnectionState(6))
        println("stop the count")
        driver.quit()
    }

}