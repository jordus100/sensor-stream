package com.example.test

import io.appium.java_client.Setting
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.connection.ConnectionState
import io.appium.java_client.remote.MobileCapabilityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
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


const val ADB_DEVICE_UID = "public.smartdust.me:13369"
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
        val remoteUrl = URL(APPIUM_SERVER_URL)
        driver = AndroidDriver(remoteUrl, desiredCapabilities)
        driver.setSetting(Setting.WAIT_FOR_IDLE_TIMEOUT, 100)
        driver.connection = ConnectionState(6)
    }

    @Test
    fun sensorsReadoutsTest(){
        val gyroX = driver.findElement(By.id("gyroX"))
        val accelX = driver.findElement(By.id("accelX"))
        val gyroTxt = gyroX.text
        val accelTxt = accelX.text
        var wait = WebDriverWait(driver, Duration.ofMillis(500))
        wait.pollingEvery(Duration.ofMillis(5))
        wait.until { (gyroX.text != gyroTxt) && (accelX.text != accelTxt) }
    }

    @Test
    fun connectionStatusDisplayTest() { // the device needs to have only wifi internet connection
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.getAppString("connection_good")
        ))

        driver.connection = ConnectionState(0)
        wait = WebDriverWait(driver, Duration.ofSeconds(2))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.getAppString("connection_bad")
        ))

        driver.connection = ConnectionState(6)
        wait = WebDriverWait(driver, Duration.ofSeconds(30))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.getAppString("connection_good")
        ))
    }

    val finger = PointerInput(PointerInput.Kind.TOUCH, "finger")

    @Test(groups = ["onTouch"])
    fun transmissionTouchControlTest(){
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.getAppString("connection_good")
        ))

        val element = driver.findElement(By.id("transmissionStatusText"))
        if(element.text != driver.getAppString("transmission_status_off")) throw AssertionError()

        driver.performTouch(element.rect.x, element.rect.y, TouchActionType.DOWN)
        wait = WebDriverWait(driver, Duration.ofSeconds(3))
        wait.until(ExpectedConditions.textToBe(By.id("transmissionStatusText"),
            driver.getAppString("transmission_status_on")
        ))
        driver.performTouch(element.rect.x, element.rect.y, TouchActionType.RELEASE)
        wait.until(ExpectedConditions.textToBe(By.id("transmissionStatusText"),
            driver.getAppString("transmission_status_off")
        ))
    }

    @Test(groups = ["onButton"], dependsOnGroups = ["onTouch"])
    fun streamModeChangeTest(){
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(ExpectedConditions.textToBe(By.id("statusText"),
            driver.getAppString("connection_good")
        ))
        val startBtn = driver.findElement(By.id("startButton"))
        val transmissionTxt = driver.findElement(By.id("transmissionStatusText"))
        startBtn.click()
        Thread.sleep(1000)
        if(transmissionTxt.text != driver.getAppString("transmission_status_off"))
            throw AssertionError()
        val streamCheckbox = driver.findElement(By.className("android.widget.CheckBox"))
        streamCheckbox.click()
        startBtn.click()
        wait = WebDriverWait(driver, Duration.ofMillis(400))
        wait.pollingEvery(Duration.ofMillis(25))
        wait.until { transmissionTxt.text == driver.getAppString("transmission_status_on") }
        startBtn.click()
        wait.until { transmissionTxt.text == driver.getAppString("transmission_status_off") }
        streamCheckbox.click()
        startBtn.click()
        Thread.sleep(1000)
        if(transmissionTxt.text != driver.getAppString("transmission_status_off"))
            throw AssertionError()
        transmissionTouchControlTest()
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

    fun AndroidDriver.getAppString(key : String) : String?{
        return if (appStringMap[key] == null) getAppStringMap("en")[key] else appStringMap[key]
    }


    @AfterTest
    fun tearDown() {
        driver.connection = ConnectionState(6)
        driver.quit()
    }

}