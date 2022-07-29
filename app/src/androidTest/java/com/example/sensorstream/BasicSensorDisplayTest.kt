package com.example.sensorstream


import androidx.test.espresso.DataInteraction
import androidx.test.espresso.ViewInteraction
import androidx.test.filters.LargeTest
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent

import androidx.test.InstrumentationRegistry.getInstrumentation
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*

import com.example.sensorstream.R

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.core.IsInstanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.`is`

@LargeTest
@RunWith(AndroidJUnit4::class)
class BasicSensorDisplayTest {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun basicSensorDisplayTest() {
        val appCompatButton = onView(
allOf(withId(R.id.startButton), withText("START"),
childAtPosition(
childAtPosition(
withId(android.R.id.content),
0),
0),
isDisplayed()))
        appCompatButton.perform(click())
        
        val frameLayout = onView(
allOf(withId(android.R.id.content),
withParent(allOf(withId(androidx.appcompat.R.id.action_bar_root),
withParent(IsInstanceOf.instanceOf(android.widget.FrameLayout::class.java)))),
isDisplayed()))
        frameLayout.check(matches(isDisplayed()))
        
        val textView = onView(
allOf(withId(R.id.accelZ), withText("z : 9.748414"),
withParent(withParent(withId(android.R.id.content))),
isDisplayed()))
        textView.check(matches(isDisplayed()))
        
        val textView2 = onView(
allOf(withId(R.id.gyroZ), withText("z : -0.003701"),
withParent(withParent(withId(android.R.id.content))),
isDisplayed()))
        textView2.check(matches(isDisplayed()))
        }
    
    private fun childAtPosition(
            parentMatcher: Matcher<View>, position: Int): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }
    }
