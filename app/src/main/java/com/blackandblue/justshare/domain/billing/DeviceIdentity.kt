package com.blackandblue.justshare.domain.billing

/**
 * Value object wrapping the persistent anonymous device UUID used as the
 * billing and relay-quota identity for AlterSend Remote.
 *
 * The ID is generated once on first app launch and never changes.
 * No personal information is ever stored or transmitted alongside it.
 */
data class DeviceIdentity(val id: String) {
    init {
        require(id.isNotBlank()) { "DeviceIdentity id must not be blank" }
    }
}
