package com.example.test

import io.appium.java_client.android.connection.ConnectionState
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import java.time.Duration

class AppiumSensorsReadoutsActivityTests : ParallelAppiumTest() {

    private lateinit var startBtn : WebElement
    private lateinit var transmissionTxt : WebElement
    private lateinit var gyroX : WebElement
    private lateinit var accelX : WebElement

    @BeforeTest
    fun activitySetUp(){
        startBtn = driver.findElement(By.id("startButton"))
        transmissionTxt = driver.findElement(By.id("transmissionStatusText"))
        gyroX = driver.findElement(By.id("gyroX"))
        accelX = driver.findElement(By.id("accelX"))
    }

    @Test
    fun sensorsReadoutsTest(){
        val gyroTxt = gyroX.text
        val accelTxt = accelX.text
        var wait = WebDriverWait(driver, Duration.ofMillis(1000))
        wait.pollingEvery(Duration.ofMillis(5))
        wait.until { (gyroX.text != gyroTxt) && (accelX.text != accelTxt) }
    }

    @Test
    fun connectionStatusDisplayTest() { // the device needs to have only wifi internet connection
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(
            ExpectedConditions.textToBe(By.id("statusText"),
            driver.getAppString("connection_good")
        ))

        driver.connection = ConnectionState(0)
        wait = WebDriverWait(driver, Duration.ofSeconds(2))
        wait.until(
            ExpectedConditions.textToBe(By.id("statusText"),
                driver.getAppString("connection_bad")
        ))

        driver.connection = ConnectionState(6)
        wait = WebDriverWait(driver, Duration.ofSeconds(30))
        wait.until(
            ExpectedConditions.textToBe(By.id("statusText"),
            driver.getAppString("connection_good")
        ))
    }


    @Test(groups = ["onTouch"])
    fun transmissionTouchControlTest(){
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(
            ExpectedConditions.textToBe(
                By.id("statusText"),
            driver.getAppString("connection_good")
        ))

        if(transmissionTxt.text != driver.getAppString("transmission_status_off"))
            throw AssertionError()

        driver.performTouch(transmissionTxt.rect.x, transmissionTxt.rect.y, TouchActionType.DOWN)
        wait = WebDriverWait(driver, Duration.ofSeconds(3))
        wait.until(
            ExpectedConditions.textToBe(
                By.id("transmissionStatusText"),
            driver.getAppString("transmission_status_on")
        ))
        driver.performTouch(transmissionTxt.rect.x, transmissionTxt.rect.y, TouchActionType.RELEASE)
        wait.until(
            ExpectedConditions.textToBe(
                By.id("transmissionStatusText"),
            driver.getAppString("transmission_status_off")
        ))
    }

    @Test(dependsOnGroups = ["onTouch"])
    fun streamModeChangeTest(){
        var wait = WebDriverWait(driver, Duration.ofSeconds(8))
        wait.until(
            ExpectedConditions.textToBe(
                By.id("statusText"),
            driver.getAppString("connection_good")
        ))
        if(startBtn.text != driver.getAppString("start")) throw AssertionError()

        startBtn.click() //checking if on button toggle streaming mode is disabled
        Thread.sleep(1000)
        if(transmissionTxt.text != driver.getAppString("transmission_status_off"))
            throw AssertionError()
        val streamCheckbox = driver.findElement(By.className("android.widget.CheckBox"))

        streamCheckbox.click() //checking if after checking the checkbox,
        // on-touch streaming mode gets disabled
        driver.performTouch(transmissionTxt.rect.x, transmissionTxt.rect.y, TouchActionType.DOWN)
        Thread.sleep(2000)
        if(transmissionTxt.text == driver.getAppString("transmission_status_on"))
            throw AssertionError()
        driver.performTouch(transmissionTxt.rect.x, transmissionTxt.rect.y, TouchActionType.RELEASE)

        startBtn.click() //checking if toggling the streaming on works
        if(startBtn.text != driver.getAppString("stop")) throw AssertionError()
        wait = WebDriverWait(driver, Duration.ofMillis(400))
        wait.pollingEvery(Duration.ofMillis(25))
        wait.until { transmissionTxt.text == driver.getAppString("transmission_status_on") }

        startBtn.click() //checking if toggling the streaming off works
        if(startBtn.text != driver.getAppString("start")) throw AssertionError()
        wait.until { transmissionTxt.text == driver.getAppString("transmission_status_off") }

        streamCheckbox.click() //checking if after un-checking the checkbox,
        //on-toggle streaming mode gets disabled
        startBtn.click()
        Thread.sleep(1000)
        if(transmissionTxt.text != driver.getAppString("transmission_status_off"))
            throw AssertionError()
        transmissionTouchControlTest()
    }
}