package com.agentos.capability.api

import android.os.Parcel
import android.os.Parcelable

data class CommunicationRequest(val operation: Int, val recipient: String, val body: String, val subscriptionId: Int = -1) : Parcelable {
    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeInt(operation); out.writeString(recipient); out.writeString(body); out.writeInt(subscriptionId)
    }
    override fun describeContents() = 0
    override fun toString() = "CommunicationRequest(operation=$operation, recipient=<redacted>, body=<redacted>)"
    companion object CREATOR : Parcelable.Creator<CommunicationRequest> {
        override fun createFromParcel(p: Parcel) = CommunicationRequest(p.readInt(), p.readString().orEmpty(), p.readString().orEmpty(), p.readInt())
        override fun newArray(size: Int): Array<CommunicationRequest?> = arrayOfNulls(size)
    }
}

data class CommunicationReply(val status: Int, val message: String) : Parcelable {
    override fun writeToParcel(out: Parcel, flags: Int) { out.writeInt(status); out.writeString(message) }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<CommunicationReply> {
        override fun createFromParcel(p: Parcel) = CommunicationReply(p.readInt(), p.readString().orEmpty())
        override fun newArray(size: Int): Array<CommunicationReply?> = arrayOfNulls(size)
    }
}
