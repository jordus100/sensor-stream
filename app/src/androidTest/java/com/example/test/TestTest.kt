package com.example.test

import io.appium.java_client.android.AndroidDriver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.testng.annotations.BeforeTest
import kotlin.Throws
import org.openqa.selenium.remote.DesiredCapabilities
import org.testng.annotations.AfterTest
import org.testng.annotations.Test
import java.net.MalformedURLException
import java.net.URL

class TestTest {
    private var driver: AndroidDriver? = null

    @BeforeTest
    fun setUp() {
        val desiredCapabilities = DesiredCapabilities()
        desiredCapabilities.setCapability("platformName", "Android")
        desiredCapabilities.setCapability("appium:automationName", "UiAutomator2")
        desiredCapabilities.setCapability("appium:deviceName", "ZY227MRS44")
        desiredCapabilities.setCapability("appium:appPackage", "com.example.sensorstream")
        desiredCapabilities.setCapability(
            "appium:appActivity",
            "com.example.sensorstream.view.SensorsReadoutsActivity"
        )
        desiredCapabilities.setCapability(
            "appium:app",
            "C:\\Users\\admin\\AndroidStudioProjects\\sensor-stream\\app\\build\\outputs\\apk\\debug\\app-debug.apk"
        )
        desiredCapabilities.setCapability("appium:newCommandTimeout", 60)
        desiredCapabilities.setCapability("appium:ensureWebviewsHavePages", true)
        desiredCapabilities.setCapability("appium:nativeWebScreenshot", true)
        desiredCapabilities.setCapability("appium:connectHardwareKeyboard", true)
        val remoteUrl = URL("http://127.0.0.1:4723/wd/hub")
        driver = AndroidDriver(remoteUrl, desiredCapabilities)
    }

    @Test
    fun sampleTest() {
        runBlocking { delay(5000) }
    }

    @AfterTest
    fun tearDown() {
        driver!!.quit()
    }
}