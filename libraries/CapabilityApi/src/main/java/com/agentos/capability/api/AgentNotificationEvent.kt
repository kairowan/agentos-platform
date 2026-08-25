package com.agentos.capability.api

import android.os.Parcel
import android.os.Parcelable

class AgentNotificationEvent(
    val packageName: String,
    val sender: String,
    val text: String,
    val postedAtMillis: Long,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        packageName = parcel.readString().orEmpty(),
        sender = parcel.readString().orEmpty(),
        text = parcel.readString().orEmpty(),
        postedAtMillis = parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeString(sender)
        parcel.writeString(text)
        parcel.writeLong(postedAtMillis)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AgentNotificationEvent> {
        override fun createFromParcel(parcel: Parcel) = AgentNotificationEvent(parcel)
        override fun newArray(size: Int): Array<AgentNotificationEvent?> = arrayOfNulls(size)
    }
}
