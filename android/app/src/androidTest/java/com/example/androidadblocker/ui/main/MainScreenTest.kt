package com.example.androidadblocker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.androidadblocker.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainScreen(onToggleVpn = { _, _ -> })
    }
  }

  @Test
  fun titleAndButton_exist() {
    composeTestRule.onNodeWithText("Android Ad Blocker").assertExists()
    composeTestRule.onNodeWithText("Start Protection").assertExists()
  }
}
