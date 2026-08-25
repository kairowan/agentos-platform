package com.agentos.capability.api

import android.os.Parcel
import android.os.Parcelable

class CapabilityReply(
    val status: Int,
    val capabilityId: String,
    val title: String,
    val message: String,
    val token: String,
    val factKeys: List<String>,
    val factValues: List<String>,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        status = parcel.readInt(),
        capabilityId = parcel.readString().orEmpty(),
        title = parcel.readString().orEmpty(),
        message = parcel.readString().orEmpty(),
        token = parcel.readString().orEmpty(),
        factKeys = parcel.createStringArrayList().orEmpty(),
        factValues = parcel.createStringArrayList().orEmpty(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(status)
        parcel.writeString(capabilityId)
        parcel.writeString(title)
        parcel.writeString(message)
        parcel.writeString(token)
        parcel.writeStringList(factKeys)
        parcel.writeStringList(factValues)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<CapabilityReply> {
        override fun createFromParcel(parcel: Parcel) = CapabilityReply(parcel)
        override fun newArray(size: Int): Array<CapabilityReply?> = arrayOfNulls(size)
    }
}
