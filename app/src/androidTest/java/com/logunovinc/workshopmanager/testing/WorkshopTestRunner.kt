package com.logunovinc.workshopmanager.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class WorkshopTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: android.os.Bundle) {
        // A named real test is explicit opt-in; missing configuration fails its attestation.
        if (arguments.getString("class").orEmpty().split(",").none { it in setOf(RealBackendConnectedTest::class.java.name, RealCloseIncompleteConnectedTest::class.java.name) }) {
            val existing=arguments.getString("notAnnotation").orEmpty()
            arguments.putString("notAnnotation", listOf(existing,RealBackendOnly::class.java.name).filter(String::isNotEmpty).joinToString(","))
        }
        super.onCreate(arguments)
    }
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
