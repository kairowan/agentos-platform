package com.agentos.capability.service

internal class CallerIdentityPolicy(private val allowedPackage: String) {
    fun isAuthorized(packagesForUid: List<String>, signatureMatches: Boolean): Boolean =
        signatureMatches && packagesForUid == listOf(allowedPackage)
}
