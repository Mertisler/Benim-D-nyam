package com.loc.benimdunyam.data.remote.firebase

import com.google.firebase.auth.FirebaseUser
import com.loc.benimdunyam.domain.model.CloudAccount

internal fun FirebaseUser.toCloudAccount(): CloudAccount = CloudAccount(
    uid = uid,
    email = email,
    isEmailVerified = isEmailVerified
)
