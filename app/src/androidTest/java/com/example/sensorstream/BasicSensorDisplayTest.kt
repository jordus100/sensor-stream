package com.example.sensorstream


import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sensorstream.view.SensorsReadoutsActivity
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.core.IsInstanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BasicSensorDisplayTest {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(SensorsReadoutsActivity::class.java)

    @Test
    fun basicSensorDisplayTest() {
        val appCompatButton = onView(
            allOf(
                withId(R.id.startButton), withText("START"),
                childAtPosition(
                    childAtPosition(
                        withId(android.R.id.content),
                        0
                    ),
                    0
                ),
                isDisplayed()
            )
        )
        appCompatButton.perform(click())

        val frameLayout = onView(
            allOf(
                withId(android.R.id.content),
                withParent(
                    allOf(
                        withId(androidx.appcompat.R.id.action_bar_root),
                        withParent(IsInstanceOf.instanceOf(android.widget.FrameLayout::class.java))
                    )
                ),
                isDisplayed()
            )
        )
        frameLayout.check(matches(isDisplayed()))

        val textView = onView(
            allOf(
                withId(R.id.accelZ),
                withParent(withParent(withId(android.R.id.content))),
                isDisplayed()
            )
        )
        textView.check(matches(isDisplayed()))

        val textView2 = onView(
            allOf(
                withId(R.id.gyroZ),
                withParent(withParent(withId(android.R.id.content))),
                isDisplayed()
            )
        )
        textView2.check(matches(isDisplayed()))
    }

    private fun childAtPosition(
        parentMatcher: Matcher<View>, position: Int
    ): Matcher<View> {

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
