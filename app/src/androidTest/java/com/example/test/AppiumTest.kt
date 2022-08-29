package com.example.test

import io.appium.java_client.Setting
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.connection.ConnectionState
import io.appium.java_client.remote.MobileCapabilityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.devtools.v85.input.model.MouseButton
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import java.net.URL
import java.time.Duration
import org.openqa.selenium.interactions.Sequence
import java.util.*


const val ADB_DEVICE_UID = "ZY227MRS44"
const val APP_PACKAGE = "com.example.sensorstream"
const val ACTIVITY = "com.example.sensorstream.view.SensorsReadoutsActivity"
const val PATH_TO_APK =
    "C:\\Users\\admin\\AndroidStudioProjects\\sensor-stream\\app\\build\\outputs\\apk\\debug\\app-debug.apk"
const val APPIUM_SERVER_URL = "http://127.0.0.1:4723/wd/hub"

enum class TouchActionType{
    DOWN, RELEASE
}

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

        driver.setConnection(ConnectionState(6))
        wait = WebDriverWait(driver, Duration.ofSeconds(30))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.appStringMap.get("connection_good")))
    }

    val finger = PointerInput(PointerInput.Kind.TOUCH, "finger")

    @Test
    fun transmissionTouchControl(){
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.appStringMap.get("connection_good")))

        var element = driver.findElement(By.id("transmissionStatusText"))
        if(element.text != driver.appStringMap.get("transmission_status_off")) throw AssertionError()

        driver.performTouch(element.rect.x, element.rect.y, TouchActionType.DOWN)
        wait = WebDriverWait(driver, Duration.ofSeconds(3))
        wait.until(ExpectedConditions.textToBe(By.id("transmissionStatusText"),
            driver.appStringMap.get("transmission_status_on")))
        driver.performTouch(element.rect.x, element.rect.y, TouchActionType.RELEASE)
        wait.until(ExpectedConditions.textToBe(By.id("transmissionStatusText"),
            driver.appStringMap.get("transmission_status_off")))
    }

    private fun AndroidDriver.performTouch(x : Int, y : Int, touchActionType: TouchActionType) {
        val touchSequence = Sequence(finger, 0)
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

    @AfterTest
    fun tearDown() {
        driver.setConnection(ConnectionState(6))
        driver.quit()
    }

}