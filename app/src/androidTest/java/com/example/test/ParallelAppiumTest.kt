package com.example.test

import io.appium.java_client.Setting
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.connection.ConnectionState
import io.appium.java_client.remote.MobileCapabilityType
import org.openqa.selenium.devtools.v85.input.model.MouseButton
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.remote.DesiredCapabilities
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Parameters
import java.net.URL
import java.time.Duration

open class ParallelAppiumTest {

    protected lateinit var driver: AndroidDriver

    @BeforeTest
    @Parameters("deviceUID", "systemPort")
    fun setUp(deviceUID : String, systemPort : String) {
        val desiredCapabilities = DesiredCapabilities()
        desiredCapabilities.setCapability("platformName", "Android")
        desiredCapabilities.setCapability("appium:automationName", "UiAutomator2")
        desiredCapabilities.setCapability("appium:deviceName", deviceUID)
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, deviceUID)
        desiredCapabilities.setCapability("appium:appPackage", APP_PACKAGE)
        desiredCapabilities.setCapability("appium:appActivity", ACTIVITY)
        desiredCapabilities.setCapability("appium:app", PATH_TO_APK)
        desiredCapabilities.setCapability("appium:newCommandTimeout", 60)
        desiredCapabilities.setCapability("uiautomator2ServerInstallTimeout", 60000)
        desiredCapabilities.setCapability("systemPort", systemPort)
        desiredCapabilities.setCapability("adbExecTimeout", 60000)
        val remoteUrl = URL(APPIUM_SERVER_URL)
        driver = AndroidDriver(remoteUrl, desiredCapabilities)
        driver.setSetting(Setting.WAIT_FOR_IDLE_TIMEOUT, 50)
        driver.connection = ConnectionState(6)
    }

    val finger = PointerInput(PointerInput.Kind.TOUCH, "finger")

    protected fun AndroidDriver.performTouch(x : Int, y : Int, touchActionType: TouchActionType) {
        val touchSequence = org.openqa.selenium.interactions.Sequence(finger, 0)
        touchSequence.addAction(
            finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y))
        when(touchActionType){
            TouchActionType.DOWN ->
                touchSequence.addAction(finger.createPointerDown(MouseButton.LEFT.ordinal))
            TouchActionType.RELEASE -> {
                touchSequence.addAction(finger.createPointerDown(MouseButton.LEFT.ordinal))
                touchSequence.addAction(finger.createPointerUp(MouseButton.LEFT.ordinal))
            }

        }
        perform(listOf(touchSequence))
    }

    protected fun AndroidDriver.getAppString(key : String) : String?{
        return if (appStringMap[key] == null)
            getAppStringMap("en")[key] else appStringMap[key]
    }


    @AfterTest
    fun tearDown() {
        driver.connection = ConnectionState(6)
        driver.quit()
    }
}