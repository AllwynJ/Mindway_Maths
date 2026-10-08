package com.elsco.mindwaymaths.data.remote

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.appcheck.FirebaseAppCheck
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class FirebaseProvider @Inject constructor() {
    val configured get() = FirebaseApp.getApps(com.elsco.mindwaymaths.MindwayApplication.instance).isNotEmpty()
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val store: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val appCheck: FirebaseAppCheck get() = FirebaseAppCheck.getInstance()
}
